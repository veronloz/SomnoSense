#ifndef DHT_SENSOR_H
#define DHT_SENSOR_H

#include <zephyr/types.h>
#include <drivers/sensor.h>


/**
 * @brief Initializes the DHT11 sensor.
 * @return 0 on success, negative error code otherwise.
 */
int dht_init(void);

/**
 * @brief Reads data from the sensor.
 * @param temp Pointer to store temperature (Celsius)
 * @param hum Pointer to store humidity (%)
 * @return 0 on success, negative error code otherwise.
 */
int dht_read_data(struct sensor_value *temp, struct sensor_value *hum);

#endif