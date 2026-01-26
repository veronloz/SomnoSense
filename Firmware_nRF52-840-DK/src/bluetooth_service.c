/* bluetooth_service.c - encapsulate GATT service + advertising + notification */

#include <zephyr.h>
#include <device.h>
#include <sys/printk.h>
#include <logging/log.h>

#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/conn.h>
#include <bluetooth/gatt.h>
#include <bluetooth/uuid.h>
#include <string.h>

#include "bluetooth_service.h"

LOG_MODULE_REGISTER(bluetooth_service, LOG_LEVEL_INF);

static struct bt_conn *current_conn;

#define DEVICE_NAME CONFIG_BT_DEVICE_NAME
#define DEVICE_NAME_LEN (sizeof(DEVICE_NAME) - 1)

static uint8_t service_buffer[GAS_SENSOR_DATA_LEN];

/* UUIDs */
#define BT_UUID_GAS_SENSOR_SERVICE_VAL \
    BT_UUID_128_ENCODE(0x47617353, 0x656e, 0x736f, 0x7253, 0x766300000000)
#define BT_UUID_GAS_SENSOR_SERVICE BT_UUID_DECLARE_128(BT_UUID_GAS_SENSOR_SERVICE_VAL)

#define BT_UUID_GAS_READINGS_CHAR_VAL \
    BT_UUID_128_ENCODE(0x47617352, 0x6561, 0x6469, 0x6e67, 0x73000000000)
#define BT_UUID_GAS_READINGS_CHAR BT_UUID_DECLARE_128(BT_UUID_GAS_READINGS_CHAR_VAL)

static ssize_t read_gas_char_cb(struct bt_conn *conn,
                              const struct bt_gatt_attr *attr,
                              void *buf, uint16_t len,
                              uint16_t offset)
{
    return bt_gatt_attr_read(conn, attr, buf, len, offset, service_buffer, sizeof(service_buffer));
}

static void gas_char_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
    bool notifications_enabled = (value == BT_GATT_CCC_NOTIFY);
    printk("Notifications %s\n", notifications_enabled ? "enabled" : "disabled");
}

/* Define service */
BT_GATT_SERVICE_DEFINE(gas_sensor_service,
    BT_GATT_PRIMARY_SERVICE(BT_UUID_GAS_SENSOR_SERVICE),
    BT_GATT_CHARACTERISTIC(BT_UUID_GAS_READINGS_CHAR,
                           BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
                           BT_GATT_PERM_READ,
                           read_gas_char_cb,
                           NULL,
                           NULL),
    BT_GATT_CCC(gas_char_ccc_cfg_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE)
);

static const struct bt_data ad[] = {
    BT_DATA_BYTES(BT_DATA_FLAGS, (BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR)),
    BT_DATA_BYTES(BT_DATA_UUID128_ALL, BT_UUID_GAS_SENSOR_SERVICE_VAL),
};

static const struct bt_data sd[] = {
    BT_DATA(BT_DATA_NAME_COMPLETE, DEVICE_NAME, DEVICE_NAME_LEN),
};

static void connected(struct bt_conn *conn, uint8_t err)
{
    if (err) {
        printk("Connection failed (err 0x%02x)\n", err);
    } else {
        printk("Connected\n");
        current_conn = bt_conn_ref(conn);
    }
}

static void disconnected(struct bt_conn *conn, uint8_t reason)
{
    printk("Disconnected (reason 0x%02x)\n", reason);
    if (current_conn) {
        bt_conn_unref(current_conn);
        current_conn = NULL;
    }
}

BT_CONN_CB_DEFINE(conn_callbacks) = {
    .connected = connected,
    .disconnected = disconnected,
};

static void bt_ready(int err)
{
    char addr_s[BT_ADDR_LE_STR_LEN];
    bt_addr_le_t addr = {0};
    size_t count = 1;

    if (err) {
        printk("Bluetooth init failed (err %d)\n", err);
        return;
    }

    printk("Bluetooth initialized\n");

    err = bt_le_adv_start(BT_LE_ADV_CONN, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
    if (err) {
        printk("Advertising failed to start (err %d)\n", err);
        return;
    }

    bt_id_get(&addr, &count);
    bt_addr_le_to_str(&addr, addr_s, sizeof(addr_s));
    printk("Advertising started, connectable as %s\n", addr_s);
}

int bluetooth_service_init(void)
{
    return bt_enable(bt_ready);
}

int bluetooth_gas_update_and_notify(const uint8_t *data, size_t len)
{
    if (!data || len == 0) return -EINVAL;
    size_t copy_len = (len > GAS_SENSOR_DATA_LEN) ? GAS_SENSOR_DATA_LEN : len;
    memcpy(service_buffer, data, copy_len);
    if (!current_conn) return -ENOTCONN;
    bt_gatt_notify(current_conn, &gas_sensor_service.attrs[1], service_buffer, copy_len);
    return 0;
}
