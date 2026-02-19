# Extension Box

Extension Box is a modular Android system monitoring application built around independently controlled extensions.

The app runs a foreground monitoring service and displays live system data through a persistent notification and in-app dashboard cards.  
Each module can be enabled or disabled individually.

Disabled modules do not execute background logic.

---

## Description

Extension Box provides structured, modular system monitoring without unnecessary overhead.

- Foreground service-based monitoring
- Persistent notification (compact + expanded)
- Modular architecture
- Independent module lifecycle
- No analytics or tracking
- Open-source

---

## Core Architecture

- Central `MonitorService` (Foreground Service)
- Module registry system
- Each module:
  - Has its own key
  - Has its own update interval
  - Produces its own data block
- Modules can be toggled on/off at runtime
- Dashboard renders live module cards

---

## Current Features

### 🔋 Battery
- Current (mA)
- Power (W)
- Temperature
- Health
- Voltage (when available)

### 🧠 CPU & RAM
- CPU usage
- Memory usage

### 📱 Screen Time
- Screen on time
- Screen off time
- Drain rates

### 😴 Deep Sleep
- Awake ratio
- Deep sleep ratio

### 📶 Network Speed
- Real-time download speed
- Real-time upload speed

### 📊 Data Usage
- Daily usage
- Monthly usage
- WiFi & mobile breakdown

### 🔓 Unlock Counter
- Daily unlock count
- Usage awareness tracking

### 💾 Storage
- Internal storage usage

### 📡 Connection Info
- WiFi state
- Cellular state
- VPN detection

### 🕒 Uptime
- Device uptime since boot

### 👣 Step Counter
- Step count
- Distance (sensor-based)

### 🏎 Speed Test
- Manual or periodic network speed testing

### 🍆 Fap Counter
- Self-monitoring counter
- Streak tracking

---

## App Screens

- Dashboard (live module cards)
- Extensions (enable/disable modules)
- Settings (monitor control & data reset)
- About screen

---

## CI / Release

- GitHub Actions builds debug APK on push
- Version tags (`v*`) generate release APK automatically
- Signed release is optional (via repository secrets)

---

## Requirements

- Android 8.0+
- Required permissions vary depending on enabled modules

---

## License

MIT License

