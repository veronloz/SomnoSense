/* main.c - Application main entry point */

/*
 * Copyright (c) 2015-2016 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 */


#include <kernel.h>
#include <device.h>
#include <zephyr/types.h>
#include <stddef.h>
#include <sys/printk.h>
#include <sys/util.h>

#include <string.h>
#include <stdio.h>

#include <drivers/sensor.h>

//Include sensor gas
#include <drivers/i2c.h>
#include <logging/log.h>
#include <zephyr.h>

// Includes bluetooth and sensor files
#include "bluetooth_service.h"
#include "temp_humi.h"
#include "gas_sensor.h"


#define DEVICE_NAME CONFIG_BT_DEVICE_NAME
#define DEVICE_NAME_LEN (sizeof(DEVICE_NAME) - 1)

// --- Keep track of the current connection ---
// Bluetooth connection and GATT handled in bluetooth_service.c 

// Thread stack and thread object for DHT notify thread (file scope)
K_THREAD_STACK_DEFINE(temp_humi_stack, 1024);
static struct k_thread temp_humi_tid;

// --- Define Custom UUIDs for our Gas Sensor Service ---
// Gas Sensor Service UUID: 47617353-656e-736f-7253-766300000000 (ASCII: "GasSensorSvc")
#define BT_UUID_GAS_SENSOR_SERVICE_VAL \
	BT_UUID_128_ENCODE(0x47617353, 0x656e, 0x736f, 0x7253, 0x766300000000)
#define BT_UUID_GAS_SENSOR_SERVICE BT_UUID_DECLARE_128(BT_UUID_GAS_SENSOR_SERVICE_VAL)

// Gas Readings Characteristic UUID: 47617352-6561-6469-6e67-73000000000 (ASCII: "GasReadings")
#define BT_UUID_GAS_READINGS_CHAR_VAL \
	BT_UUID_128_ENCODE(0x47617352, 0x6561, 0x6469, 0x6e67, 0x73000000000)
#define BT_UUID_GAS_READINGS_CHAR BT_UUID_DECLARE_128(BT_UUID_GAS_READINGS_CHAR_VAL)

/*
// Simple helper used by temp_humi module to forward notifications.
int my_sensor_notify_string(const char *s)
{
	if (!s) return -EINVAL;
	printk("temp_humi: %s\n", s);
	// For now, we simply log the string. If you want this over BLE, * implement a separate characteristic and use bt_gatt_notify here.
	return 0;
}
*/


void main(void)
{
	int err;
	printk("Starting Multichannel Gas Sensor (GATT Server mode)\n");

	// --- NEW: Initialize Bluetooth (through bluetooth_service) ---
	err = bluetooth_service_init();
	if (err) {
		printk("Bluetooth init failed (err %d)\n", err);
		return;
	}
	// bt_ready() will be called when BLE stack is up


	// --- Your existing I2C init code ---
	const struct device *i2c_dev = DEVICE_DT_GET(I2C_NODE);
	if (!device_is_ready(i2c_dev)) {
		printk("I2C device not ready\n");
		return;
	}

	k_sleep(K_SECONDS(1));   // allow sensor MCU to boot

	// Start DHT11 notify thread (spawns the temp_humi task)
	(void)k_thread_create(&temp_humi_tid, temp_humi_stack,
						  K_THREAD_STACK_SIZEOF(temp_humi_stack),
						  (k_thread_entry_t)dht11_notify_thread, NULL, NULL, NULL,
						  K_PRIO_COOP(8), 0, K_NO_WAIT);
	

	while (1) {
		read_all_gases(i2c_dev);
		k_sleep(K_SECONDS(2));
	}
}


