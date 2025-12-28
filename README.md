# SomnoSense: a room monitoring app 📱🌡️

A comprehensive Android application for monitoring environmental conditions using Bluetooth Low Energy (BLE) sensors. The app connects to nRF52480-based devices to read and display real-time temperature, humidity, and gas level data with an intuitive interface. 

## Features ✨

- **BLE Device Scanning** - Discover nearby Bluetooth devices
- **Environmental Monitoring** - Real-time temperature, humidity, and gas level readings
- **Air Quality Assessment** - Automatic air quality classification based on sensor data
- **Auto-Refresh** - Continuous sensor data updates every 2 seconds
- **Modern UI** - Clean, user-friendly interface with visual indicators

## Prerequisites 📋

### Hardware Requirements
- Android smartphone with Bluetooth 4.0+ (BLE support)
- nRF52480 development board with environmental sensors
- USB cable for device connection

### Software Requirements
- Android Studio Arctic Fox (2020.3.1) or later
- Android SDK API 21 (Android 5.0) or higher
- Java 8 or Kotlin support

## Installation Guide 🛠️

### 1. Android Studio Setup

1. **Download Android Studio**
   - Visit [developer.android.com/studio](https://developer.android.com/studio)
   - Download the latest version for your operating system
   - Follow the installation wizard

2. **Install Required SDKs**
   - Open Android Studio
   - Go to **Tools → SDK Manager**
   - Install the following:
     - Android SDK API 21+ (Android 5.0+)
     - Android SDK Build-Tools
     - Android Emulator (optional)
     - Google Play services

3. **Configure Kotlin Support** (if not already installed)
   - Go to **File → Settings → Plugins**
   - Install "Kotlin" plugin
   - Restart Android Studio

### 2. Project Setup

1. **Clone the Repository**
   ```bash
   git clone https://github.com/your-username/RoomMonitorApp.git
   cd RoomMonitorApp
   ```

2. **Open Project in Android Studio**
   - Launch Android Studio
   - Select **Open an Existing Project**
   - Navigate to the cloned repository folder
   - Click **OK**

3. **Sync Project with Gradle**
   - Android Studio will automatically sync Gradle files
   - If prompted, accept any license agreements
   - Wait for the "Project Sync Completed" message

### 3. Build Configuration

1. **Verify Build Settings**
   - Open `build.gradle` (Module: app)
   - Ensure minimum SDK is 21 or higher
   - Confirm Kotlin plugin is applied

2. **Install Dependencies**
   - The project will automatically download required dependencies
   - Monitor the build progress in the bottom status bar

## Running the App 🚀

### Option 1: Physical Android Device

1. **Enable Developer Options**
   - Go to **Settings → About Phone**
   - Tap "Build Number" 7 times
   - Return to Settings and enter **Developer Options**

2. **Enable USB Debugging**
   - In Developer Options, enable **USB Debugging**
   - Connect your phone via USB cable

3. **Run the App**
   - In Android Studio, click the **Run** button (▶️)
   - Select your connected device
   - Click **OK**

### Option 2: Android Emulator

1. **Create Virtual Device**
   - Go to **Tools → AVD Manager**
   - Click **Create Virtual Device**
   - Select a phone model (e.g., Pixel 4)
   - Choose API level 21 or higher
   - Complete the setup

2. **Launch Emulator**
   - Select your virtual device
   - Click the **Play** button
   - Wait for the emulator to start

3. **Run the App**
   - Click **Run** in Android Studio
   - Select the running emulator
   - The app will install and launch automatically

## Configuration ⚙️

### BLE Device Setup

1. **Find Your Device UUIDs**
   - Install **nRF Connect** from Google Play Store
   - Connect to your nRF52480 device
   - Note down the Service and Characteristic UUIDs

2. **Update UUIDs in Code**
   - Open `MainActivity.kt`
   - Locate the UUID constants at the top of the file:
   ```kotlin
   private val ENVIRONMENTAL_SENSING_SERVICE = UUID.fromString("YOUR_SERVICE_UUID")
   private val TEMPERATURE_CHARACTERISTIC = UUID.fromString("YOUR_TEMP_UUID")
   private val HUMIDITY_CHARACTERISTIC = UUID.fromString("YOUR_HUMIDITY_UUID")
   private val GAS_CHARACTERISTIC = UUID.fromString("YOUR_GAS_UUID")
   ```
   - Replace with your actual UUIDs

### Permissions

The app automatically requests these required permissions:
- `BLUETOOTH_SCAN` - For discovering BLE devices
- `BLUETOOTH_CONNECT` - For connecting to devices
- `ACCESS_FINE_LOCATION` - Required for BLE scanning on Android 6.0+

## How It Works 🔄

### 1. Initial Setup
- Launch the app
- Grant required permissions when prompted
- Ensure Bluetooth is enabled on your device

### 2. Device Discovery
- Tap **"Buscar Dispositivos BLE"** (Search BLE Devices)
- App scans for 10 seconds automatically
- Found devices appear in the list

### 3. Connection
- Tap **"Conectar al Sensor"** (Connect to Sensor)
- App connects to the first found device
- Status changes to "Connected"

### 4. Data Monitoring
- App automatically discovers sensor services
- Reads temperature, humidity, and gas levels
- Updates display every 2 seconds
- Shows air quality assessment

### 5. Expected Output
```
🌡️ MONITOR AMBIENTAL 🌡️

Temperatura: 23.5 °C
Humedad: 45.2 %
Nivel de Gas: 150

✅ Calidad del aire: Excelente

Estado: ✅ Conectado
```

## Troubleshooting 🔧

### Common Issues

1. **"Bluetooth not available"**
   - Ensure Bluetooth is enabled on your phone
   - Restart the app

2. **"Permissions denied"**
   - Go to App Settings → Permissions
   - Grant Location and Bluetooth permissions
   - Or reinstall the app

3. **No devices found**
   - Ensure your nRF52480 is powered on
   - Check if device is in advertising mode
   - Move devices closer together

4. **Connection fails**
   - Verify UUIDs match your device
   - Check nRF52480 firmware is running
   - Restart both devices

### Debug Mode

Enable logging by checking Logcat in Android Studio with filter tag: `"RoomMonitor"`

## Project Structure 📁

```
app/
├── src/main/
│   ├── java/com/example/roommonitorapp/
│   │   └── MainActivity.kt
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── values/strings.xml
│   │   └── manifest/AndroidManifest.xml
│   └── assets/
└── build.gradle
```

## Contributing 🤝

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License 📄

This project is licensed under the MIT License - see the LICENSE file for details.

## Support 💬

For support and questions:
- Create an issue in this repository
- Check the troubleshooting section
- Review Android BLE documentation

---

**Note**: This app is specifically designed for nRF52480 devices with environmental sensors. Make sure your hardware is properly configured before use.
