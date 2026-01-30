# SPDX-License-Identifier: Apache-2.0

cmake_minimum_required(VERSION 3.13.1)
include($ENV{ZEPHYR_BASE}/cmake/app/boilerplate.cmake NO_POLICY_SCOPE)
project(beacon)

target_sources(app PRIVATE ../src/main.c 
../src/dht_sensor.c 
../src/sound_sensor.c
../src/gas_sensor.c
../src/ble_manager.c
)