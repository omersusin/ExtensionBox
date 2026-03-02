# Extension Box

[![License](https://img.shields.io/github/license/omersusin/ExtensionBox?style=flat-square&color=blue)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-orange?style=flat-square&logo=jetpackcompose)](https://developer.android.com/compose)
[![Build Status](https://img.shields.io/github/actions/workflow/status/omersusin/ExtensionBox/build.yml?branch=development&style=flat-square&logo=github-actions)](https://github.com/omersusin/ExtensionBox/actions)

Extension Box is a modular system monitoring framework for Android, designed to provide high-fidelity hardware and software telemetry through an extensible architecture. The application leverages a foreground service model to ensure consistent data collection while maintaining a minimal resource footprint.

---

## Project Overview

The primary objective of Extension Box is to offer a structured environment where system monitoring capabilities are encapsulated into independent modules. This approach allows users to granularly control the lifecycle of each telemetry source, ensuring that background logic is only executed for active components.

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

### 📲​ Debloater
- Uninstall whatever app you want!
- Every app is cataloged by colors and name if they can harm the system or no!

### 🔋 Battery
- Current (mA)
- Power (W)
- Temperature
- Health
- Voltage (when available)

### Power and Energy Management
- Real-time current flow (mA) and power consumption (W)
- Battery thermal monitoring and health diagnostic reporting
- Detailed voltage telemetry and charging status analysis

### Computing Resources
- Processor utilization metrics and load distribution
- System memory allocation and availability tracking
- Kernel uptime and boot duration analysis

### Network and Connectivity
- Real-time throughput monitoring for uplink and downlink
- Traffic accounting with WiFi and cellular breakdown
- Network interface identification and VPN state detection
- Integrated periodic network performance testing

### Human-Machine Interaction
- Screen state accumulation (On/Off duration)
- Device unlock frequency and usage pattern analysis
- Physical activity tracking through hardware sensor integration

---

## Technical Architecture

The application is built on a modern Android stack with a focus on modularity and reactive data flow.

- **Centralized Service:** A unified `MonitorService` manages the lifecycle of all active extensions.
- **Module Registry:** A strictly typed registry system defines module capabilities, update frequencies, and data structures.
- **Persistent Storage:** Local data persistence is handled via Room and DataStore for telemetry history and configuration.
- **Reactive UI:** The dashboard is constructed using Jetpack Compose, featuring dynamic card rendering and real-time state synchronization.
- **Enhanced Access:** Optional integration with Shizuku for privileged system file access and advanced diagnostics.

---

## Technology Stack

- **UI Framework:** Jetpack Compose with Material Design 3
- **Language:** Kotlin Coroutines and Flow
- **Dependency Injection:** Manual injection optimized for modularity
- **Database:** Room Persistence Library
- **Networking:** Retrofit 3.0
- **Build System:** Gradle Kotlin DSL

---

## CI / CD Integration

The repository is integrated with GitHub Actions to automate the quality assurance and deployment pipeline:
- **Continuous Integration:** Automatic debug builds are triggered on every push to verify code integrity.
- **Automated Releases:** Tagged commits generate release-ready binaries automatically.
- **Artifact Management:** Support for signed APK generation through repository secrets.

---

## System Requirements

- Android 8.0+
- Required permissions vary depending on enabled modules (Root/Shizuku)

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
