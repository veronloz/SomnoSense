/* gas_sensor.c - Read multichannel gas sensor over I2C and provide functions
 * to return the results as floats or bytes.
 */

#include <zephyr.h>
#include <device.h>
#include <drivers/i2c.h>
#include <logging/log.h>
#include <sys/printk.h>
#include <string.h>

#include "gas_sensor.h"

LOG_MODULE_REGISTER(gas_sensor, LOG_LEVEL_INF);

#define GAS_SENSOR_ADDR 0x04
/* Sensor gas code (from your original file) */
LOG_MODULE_REGISTER(gas_sensor, LOG_LEVEL_INF);

#define I2C_NODE DT_NODELABEL(i2c0)
#define GAS_SENSOR_ADDR 0x04

/* Register map from Seeed/Arduino source */
#define GAS_CO_REG 	0x02
#define GAS_NO2_REG 	0x04
#define GAS_NH3_REG 	0x06
#define GAS_CH4_REG 	0x08
#define GAS_C2H5OH_REG 	0x0A

#define GAS_SCALE 100.0f  /* raw / scale => ppm */

static float read_gas(const struct device *i2c_dev, uint8_t reg)
{
	uint8_t buf[2];
	int ret = i2c_write_read(i2c_dev, GAS_SENSOR_ADDR, &reg, 1, buf, sizeof(buf));
	if (ret) {
		printk("I2C read failed reg 0x%02x (err %d)\n", reg, ret);
		return -1.0f;
	}
	uint16_t raw = ((uint16_t)buf[0] << 8) | buf[1];
	return (float)raw / GAS_SCALE;
}

/* Helper function to copy a float into our byte buffer */
static void float_to_bytes(float f, uint8_t *buf)
{
	/* Assumes little-endian, which is correct for nRF52 */
	memcpy(buf, &f, sizeof(f));
}

static void read_all_gases(const struct device *i2c_dev)
{
	float co   = read_gas(i2c_dev, GAS_CO_REG);
	float no2  = read_gas(i2c_dev, GAS_NO2_REG);
	float nh3  = read_gas(i2c_dev, GAS_NH3_REG);
	float ch4  = read_gas(i2c_dev, GAS_CH4_REG);
	float etoh = read_gas(i2c_dev, GAS_C2H5OH_REG);

	/* Use integer print to avoid float formatting (same as your code) */
	uint16_t co_i   = (uint16_t)(co   * 100);
	uint16_t no2_i  = (uint16_t)(no2  * 100);
	uint16_t nh3_i  = (uint16_t)(nh3  * 100);
	uint16_t ch4_i  = (uint16_t)(ch4  * 100);
	uint16_t etoh_i = (uint16_t)(etoh * 100);

	printk("CO:%u.%02u NO2:%u.%02u NH3:%u.%02u CH4:%u.%02u C2H5OH:%u.%02u ppm\n",
		   co_i/100,   co_i%100,
		   no2_i/100,  no2_i%100,
		   nh3_i/100,  nh3_i%100,
		   ch4_i/100,  ch4_i%100,
		   etoh_i/100, etoh_i%100);

	/* --- NEW: Prepare a local buffer and notify via bluetooth_service --- */
	uint8_t buf[20];
	float_to_bytes(co,   &buf[0]);
	float_to_bytes(no2,  &buf[4]);
	float_to_bytes(nh3,  &buf[8]);
	float_to_bytes(ch4,  &buf[12]);
	float_to_bytes(etoh, &buf[16]);

	/* Notify via new bluetooth module */
	bluetooth_gas_update_and_notify(buf, sizeof(buf));
}
