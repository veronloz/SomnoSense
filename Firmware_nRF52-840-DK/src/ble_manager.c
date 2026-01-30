#include <zephyr.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/conn.h>
#include <bluetooth/gatt.h>
#include <bluetooth/uuid.h>
#include <sys/printk.h>
#include <string.h>
#include <drivers/sensor.h>
#include "ble_manager.h"

static struct bt_conn *current_conn;

/* Data Buffers */
static uint8_t sensor_data_buffer[20];
static uint8_t temp_hum_buffer[8];
static uint8_t sound_buffer[4];
static uint32_t sound_counter = 0;

#define BT_UUID_GAS_SERVICE_VAL BT_UUID_128_ENCODE(0x47617353, 0x656e, 0x736f, 0x7253, 0x766300000000)
#define BT_UUID_GAS_CHAR_VAL    BT_UUID_128_ENCODE(0x47617352, 0x6561, 0x6469, 0x6e67, 0x730000000000)
#define BT_UUID_ENV_CHAR_VAL    BT_UUID_128_ENCODE(0x456e7669, 0x726f, 0x6e6d, 0x656e, 0x740000000000)
#define BT_UUID_SND_CHAR_VAL    BT_UUID_128_ENCODE(0x536f756e, 0x6444, 0x6574, 0x6563, 0x740000000000)

static struct bt_uuid_128 gas_service_uuid = BT_UUID_INIT_128(BT_UUID_GAS_SERVICE_VAL);
static struct bt_uuid_128 gas_char_uuid = BT_UUID_INIT_128(BT_UUID_GAS_CHAR_VAL);
static struct bt_uuid_128 env_char_uuid = BT_UUID_INIT_128(BT_UUID_ENV_CHAR_VAL);
static struct bt_uuid_128 snd_char_uuid = BT_UUID_INIT_128(BT_UUID_SND_CHAR_VAL);

/* Advertising data must be static/global to be constant */
static const struct bt_data ad[] = {
    BT_DATA_BYTES(BT_DATA_FLAGS, (BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR)),
    BT_DATA_BYTES(BT_DATA_UUID128_ALL, BT_UUID_GAS_SERVICE_VAL)
};

static const struct bt_data sd[] = {
    BT_DATA(BT_DATA_NAME_COMPLETE, CONFIG_BT_DEVICE_NAME, (sizeof(CONFIG_BT_DEVICE_NAME) - 1)),
};

static ssize_t read_gas_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr, void *buf, uint16_t len, uint16_t offset) {
    return bt_gatt_attr_read(conn, attr, buf, len, offset, sensor_data_buffer, sizeof(sensor_data_buffer));
}

static ssize_t read_env_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr, void *buf, uint16_t len, uint16_t offset) {
    return bt_gatt_attr_read(conn, attr, buf, len, offset, temp_hum_buffer, sizeof(temp_hum_buffer));
}

static ssize_t read_snd_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr, void *buf, uint16_t len, uint16_t offset) {
    return bt_gatt_attr_read(conn, attr, buf, len, offset, sound_buffer, sizeof(sound_buffer));
}

BT_GATT_SERVICE_DEFINE(gas_svc,
    BT_GATT_PRIMARY_SERVICE(&gas_service_uuid),
    /* Gas Characteristic */
    BT_GATT_CHARACTERISTIC(&gas_char_uuid.uuid, BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY, BT_GATT_PERM_READ, read_gas_cb, NULL, NULL),
    BT_GATT_CCC(NULL, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
    /* Temperature & Humidity Characteristic */
    BT_GATT_CHARACTERISTIC(&env_char_uuid.uuid, BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY, BT_GATT_PERM_READ, read_env_cb, NULL, NULL),
    BT_GATT_CCC(NULL, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
    /* Sound Detected Characteristic */
    BT_GATT_CHARACTERISTIC(&snd_char_uuid.uuid, BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY, BT_GATT_PERM_READ, read_snd_cb, NULL, NULL),
    BT_GATT_CCC(NULL, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
);

static void connected(struct bt_conn *conn, uint8_t err) {
    if (err) {
        printk("Connection failed (err %u)\n", err);
    } else {
        printk("Connected\n");
        current_conn = bt_conn_ref(conn);
    }
}

static void disconnected(struct bt_conn *conn, uint8_t reason) {
    printk("Disconnected (reason %u)\n", reason);
    if (current_conn) {
        bt_conn_unref(current_conn);
        current_conn = NULL;
    }
}

BT_CONN_CB_DEFINE(conn_callbacks) = { .connected = connected, .disconnected = disconnected };

int ble_manager_init(void) {
    int err = bt_enable(NULL);
    if (err) return err;

    return bt_le_adv_start(BT_LE_ADV_CONN, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
}

void ble_update_sensor_data(struct gas_data *data) {
    memcpy(&sensor_data_buffer[0], &data->co, 4);
    memcpy(&sensor_data_buffer[4], &data->no2, 4);
    memcpy(&sensor_data_buffer[8], &data->nh3, 4);
    memcpy(&sensor_data_buffer[12], &data->ch4, 4);
    memcpy(&sensor_data_buffer[16], &data->etoh, 4);

    if (current_conn) {
        bt_gatt_notify(current_conn, &gas_svc.attrs[1], sensor_data_buffer, sizeof(sensor_data_buffer));
    }
}

void ble_update_temp_hum(struct sensor_value *temp, struct sensor_value *hum) {
    float t_val = sensor_value_to_double(temp);
    float h_val = sensor_value_to_double(hum);

    memcpy(&temp_hum_buffer[0], &t_val, 4);
    memcpy(&temp_hum_buffer[4], &h_val, 4);

    if (current_conn) {
        /* Index 1 is Gas, 2 is Gas CCC, 3 is Env Char, 4 is Env CCC... wait.
           Service=0, GasCharDef=1, GasVal=2, GasCCC=3. EnvCharDef=4, EnvVal=5, EnvCCC=6. SndCharDef=7, SndVal=8, SndCCC=9. */
        bt_gatt_notify(current_conn, &gas_svc.attrs[5], temp_hum_buffer, sizeof(temp_hum_buffer));
    }
}

void ble_update_sound(void) {
    sound_counter++;
    memcpy(sound_buffer, &sound_counter, 4);

    if (current_conn) {
        bt_gatt_notify(current_conn, &gas_svc.attrs[8], sound_buffer, sizeof(sound_buffer));
    }
}