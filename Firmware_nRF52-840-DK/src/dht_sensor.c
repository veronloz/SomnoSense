#include <kernel.h>
#include <device.h>
#include <drivers/sensor.h>
#include "dht_sensor.h"

// Get the device binding from the Device Tree
static const struct device *dht_dev;

int dht_init(void) {
    dht_dev = DEVICE_DT_GET(DT_NODELABEL(dht11));

    if (!device_is_ready(dht_dev)) {
        return -1;
    }
    return 0;
}

int dht_read_data(struct sensor_value *temp, struct sensor_value *hum) {
    int rc = sensor_sample_fetch(dht_dev);
    if (rc != 0) return rc;

    sensor_channel_get(dht_dev, SENSOR_CHAN_AMBIENT_TEMP, temp);
    sensor_channel_get(dht_dev, SENSOR_CHAN_HUMIDITY, hum);

    return 0;
}