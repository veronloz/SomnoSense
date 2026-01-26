/* bluetooth_service.h - Public API for Bluetooth module */
#ifndef BLUETOOTH_SERVICE_H
#define BLUETOOTH_SERVICE_H

#include <zephyr/types.h>

#ifdef __cplusplus
extern "C" {
#endif

#define GAS_SENSOR_DATA_LEN 20

/* Initialize Bluetooth and start advertising
 * Returns 0 on success or negative error code
 */
int bluetooth_service_init(void);

/* Copy the provided gas sensor buffer (len bytes) into service internal
 * buffer and send a notification to connected central. Max len is
 * GAS_SENSOR_DATA_LEN.
 * Returns 0 on success or negative error code.
 */
int bluetooth_gas_update_and_notify(const uint8_t *data, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* BLUETOOTH_SERVICE_H */
