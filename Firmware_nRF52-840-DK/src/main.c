#include <zephyr.h>
#include <sys/printk.h>
#include "dht_sensor.h"
#include "sound_sensor.h"
#include "gas_sensor.h"
#include "ble_manager.h"

void on_sound_detected(void) {
    printk(">>> [%lld ms] Sound Jump Detected! <<<\n", k_uptime_get());
    ble_update_sound();
}

void main(void) {
    const struct device *i2c_dev;
    struct gas_data g_data;
    struct sensor_value temp, hum;

    printk("System Starting...\n");

    if (ble_manager_init()) printk("BLE Init Failed\n");
    if (gas_sensor_init(&i2c_dev)) printk("Gas Sensor Init Failed\n");
    dht_init();
    sound_sensor_init(on_sound_detected);

    while (1) {
        // 1. Read DHT
        if (dht_read_data(&temp, &hum) == 0) {
            printk("T: %d.%d C | H: %d.%d %%\n", temp.val1, temp.val2/100000, hum.val1, hum.val2/100000);
            ble_update_temp_hum(&temp, &hum);
        }

        // 2. Read Gas & Update BLE
        gas_sensor_read_all(i2c_dev, &g_data);
        ble_update_sensor_data(&g_data);

        printk("Gas - CO:%d.%02d NO2:%d.%02d NH3:%d.%02d CH4:%d.%02d EtOH:%d.%02d ppm\n",
       (int)g_data.co,   (int)(g_data.co * 100) % 100,
       (int)g_data.no2,  (int)(g_data.no2 * 100) % 100,
       (int)g_data.nh3,  (int)(g_data.nh3 * 100) % 100,
       (int)g_data.ch4,  (int)(g_data.ch4 * 100) % 100,
       (int)g_data.etoh, (int)(g_data.etoh * 100) % 100);

        k_msleep(2000);
    }
}