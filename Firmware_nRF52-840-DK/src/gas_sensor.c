#include <drivers/i2c.h>
#include <sys/printk.h>
#include "gas_sensor.h"

#define GAS_SENSOR_ADDR 0x04
#define GAS_CO_REG      0x02
#define GAS_NO2_REG     0x04
#define GAS_NH3_REG     0x06
#define GAS_CH4_REG     0x08
#define GAS_C2H5OH_REG  0x0A
#define GAS_SCALE       100.0f

int gas_sensor_init(const struct device **i2c_dev) {
    *i2c_dev = DEVICE_DT_GET(DT_NODELABEL(i2c0));
    if (!device_is_ready(*i2c_dev)) {
        return -1;
    }
    return 0;
}

static float read_reg(const struct device *i2c_dev, uint8_t reg) {
    uint8_t buf[2];
    if (i2c_write_read(i2c_dev, GAS_SENSOR_ADDR, &reg, 1, buf, sizeof(buf))) {
        return -1.0f;
    }
    uint16_t raw = ((uint16_t)buf[0] << 8) | buf[1];
    return (float)raw / GAS_SCALE;
}

int gas_sensor_read_all(const struct device *i2c_dev, struct gas_data *data) {
    data->co   = read_reg(i2c_dev, GAS_CO_REG);
    data->no2  = read_reg(i2c_dev, GAS_NO2_REG);
    data->nh3  = read_reg(i2c_dev, GAS_NH3_REG);
    data->ch4  = read_reg(i2c_dev, GAS_CH4_REG);
    data->etoh = read_reg(i2c_dev, GAS_C2H5OH_REG);
    return 0;
}