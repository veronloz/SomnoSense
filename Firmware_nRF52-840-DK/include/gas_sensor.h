/* gas_sensor.h - API for reading the Seeed multichannel gas sensor */
#ifndef GAS_SENSOR_H
#define GAS_SENSOR_H

#include <zephyr/types.h>
#include <device.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Number of bytes the API uses for the 5 gas floats (5 * 4) */
#define GAS_SENSOR_BYTES (5 * 4)

/* Fill buf (must be at least GAS_SENSOR_BYTES). Returns 0 on success or a
 * negative errno on error.
 */
int gas_read_bytes(const struct device *i2c_dev, uint8_t *buf, size_t len);

/* Convenience: read float values into the provided array (count must be >=5).
 * Returns 0 on success.
 */
int gas_read_floats(const struct device *i2c_dev, float *vals, size_t count);

#ifdef __cplusplus
}
#endif

#endif /* GAS_SENSOR_H */