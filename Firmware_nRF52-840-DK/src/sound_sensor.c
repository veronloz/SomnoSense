#include <kernel.h>
#include <drivers/gpio.h>
#include "sound_sensor.h"

// Define the GPIO spec from the devicetree
/* Use 'sound_node' as defined in the overlay above */
static const struct gpio_dt_spec sound_gpio = GPIO_DT_SPEC_GET(DT_NODELABEL(sound_node), gpios);
static struct gpio_callback sound_cb_data;
static sound_callback_t user_cb = NULL;

// This function runs whenever the pin changes state
void sound_gpio_isr(const struct device *dev, struct gpio_callback *cb, uint32_t pins) {
    if (user_cb) {
        user_cb();
    }
}

int sound_sensor_init(sound_callback_t cb) {
    int ret;

    if (!device_is_ready(sound_gpio.port)) {
        return -ENODEV;
    }

    user_cb = cb;

    // Configure pin as input
    ret = gpio_pin_configure_dt(&sound_gpio, GPIO_INPUT);
    if (ret < 0) return ret;

    // Setup interrupt on the "Active" edge (usually falling for these modules)
    gpio_init_callback(&sound_cb_data, sound_gpio_isr, BIT(sound_gpio.pin));
    gpio_add_callback(sound_gpio.port, &sound_cb_data);
    
    ret = gpio_pin_interrupt_configure_dt(&sound_gpio, GPIO_INT_EDGE_TO_ACTIVE);
    return ret;
}