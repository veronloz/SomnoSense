
#include <kernel.h>
#include <logging/log.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/gap.h>
#include <bluetooth/uuid.h>
#include <bluetooth/conn.h>
/* No board buttons/leds needed here; remove dk_buttons_and_leds.h (not present) */
#include <device.h>
#include <drivers/sensor.h>
#include <sys/printk.h>
#include <devicetree.h>
#include <stdio.h>
#include "temp_humi.h"



/* Alias we defined in the overlay */
#define DHT11_NODE DT_ALIAS(dht11)

#if !DT_NODE_HAS_STATUS(DHT11_NODE, okay)
#error "DHT11 devicetree alias is not defined or status is not 'okay'"
#endif

/* Thread function to send sensor data via notification periodically */
void dht11_notify_thread(void *p1, void *p2, void *p3)
{
    const struct device *dht = DEVICE_DT_GET(DHT11_NODE);

    if (!device_is_ready(dht)) {
        printk("DHT11 device not ready\n");
        return;
    }

    struct sensor_value temp, hum;
    char data_str[20];  // Enough for "100C | 100%\0"

    while (1) {
        if (sensor_sample_fetch(dht) == 0) 
        {
            sensor_channel_get(dht, SENSOR_CHAN_AMBIENT_TEMP, &temp);
            sensor_channel_get(dht, SENSOR_CHAN_HUMIDITY, &hum);

            snprintf(data_str, sizeof(data_str), "%dC | %d%%", temp.val1, hum.val1);
            printk("Sending: %s\n", data_str);

            int err = my_sensor_notify_string(data_str);
            if (err == -EACCES) {
                printk("Notification not enabled by central.\n");
            } else if (err) {
                printk("Notification sent!\n");
            }
        } 
        else 
        {
            printk("Sample fetch failed\n");
        }

        k_sleep(K_MSEC(2000));
    }
}



/* --- DHT11 Sensor read function --- */
/*
static int read_dht11(void)
{
	const struct device *dht = device_get_binding("DHT11");
	if (!dht) {
		printk("DHT11 device not found\n");
		return -1;
	}

	struct sensor_value temp_val;
	struct sensor_value hum_val;

	if (sensor_sample_fetch(dht) < 0) {
		printk("DHT11: sensor_sample_fetch failed\n");
		return -1;
	}

	if (sensor_channel_get(dht, SENSOR_CHAN_AMBIENT_TEMP, &temp_val) < 0) {
		printk("DHT11: failed to get temperature channel\n");
		return -1;
	}

	if (sensor_channel_get(dht, SENSOR_CHAN_HUMIDITY, &hum_val) < 0) {
		printk("DHT11: failed to get humidity channel\n");
		return -1;
	}

	// Convert to float: val1 + val2/1e6 (val2 is micro units)
	float temp = (float)temp_val.val1 + (float)temp_val.val2 / 1000000.0f;
	float hum  = (float)hum_val.val1  + (float)hum_val.val2  / 1000000.0f;

	// Print as fixed-point (two decimals) similar to other prints
	int temp_i = (int)(temp * 100.0f);
	int hum_i  = (int)(hum  * 100.0f);

	printk("DHT11 -> Temp: %d.%02d C  Hum: %d.%02d %%\n",
		   temp_i/100, temp_i%100,
		   hum_i/100,  hum_i%100);

	return 0;
}
*/