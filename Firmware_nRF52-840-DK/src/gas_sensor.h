#ifndef GAS_SENSOR_H
#define GAS_SENSOR_H

#include <device.h>

/* Struct to hold all gas readings in one place */
struct gas_data {
    float co;
    float no2;
    float nh3;
    float ch4;
    float etoh;
};

int gas_sensor_init(const struct device **i2c_dev);
int gas_sensor_read_all(const struct device *i2c_dev, struct gas_data *data);

#endif