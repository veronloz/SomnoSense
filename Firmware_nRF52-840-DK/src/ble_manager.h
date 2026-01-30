#ifndef BLE_MANAGER_H
#define BLE_MANAGER_H

#include <zephyr/types.h>
#include "gas_sensor.h"
#include <drivers/sensor.h>

int ble_manager_init(void);
void ble_update_sensor_data(struct gas_data *data);
void ble_update_temp_hum(struct sensor_value *temp, struct sensor_value *hum);
void ble_update_sound(void);

#endif