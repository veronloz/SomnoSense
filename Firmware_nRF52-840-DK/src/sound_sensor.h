#ifndef SOUND_SENSOR_H
#define SOUND_SENSOR_H

#include <drivers/gpio.h>

/**
 * @brief Type definition for the sound detection callback function
 */
typedef void (*sound_callback_t)(void);

/**
 * @brief Initializes the sound sensor GPIO and interrupts
 * @param cb The function to call when a sound jump is detected
 * @return 0 on success, negative error code otherwise
 */
int sound_sensor_init(sound_callback_t cb);

#endif