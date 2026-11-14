# ❄️ FrostTime

> **Bare-Metal, Zero-Drain Screen Time Monitor for Android**  
> Powered by Root & Jetpack Compose (Material You & MIUIX Aesthetics)

![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen.svg)
![Root](https://img.shields.io/badge/Root-Magisk%20%7C%20KernelSU%20%7C%20APatch-blue.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-cyan.svg)
![Idle Battery](https://img.shields.io/badge/Idle%20Battery%20Drain-0.0%25-success.svg)

---

## ⚡ Why FrostTime?

Standard Digital Wellbeing and 3rd-party screen time monitors are notorious resource hogs:
- They run heavy background services and accessibility listeners 24/7.
- They consume 3–8% of battery daily and keep wakefulness locks alive.
- They sync constantly with cloud services.

**FrostTime is engineered differently:**
1. **0.0% Idle Battery & CPU Drain**: No persistent background services, no daemons, and no accessibility services. Android's OS already logs foreground transitions internally; FrostTime queries this data strictly **on-demand in <30ms** when opened.
2. **Root Superpowers (`libsu`)**:
   - **Silent 1-Click Permissions**: Automatically grants `PACKAGE_USAGE_STATS` without forcing you to dig into settings.
   - **Hardware Wakeup & Unlock Tracking**: Accurate screen wake statistics directly from low-level system dumps.
   - **Zero-Overhead Digital Detox (App Freezing)**: Freeze/suspend distracting apps with `pm suspend` directly from the UI.
3. **Ultra-Modern Visuals**:
   - Pure OLED Black (`#000000`) canvas.
   - Squircle frosted cards inspired by **HyperOS / MIUIX** and **Material You**.
   - 24-hour hourly distribution bar chart with peak activity detection.
   - Fluid animated progress bars with high-res app icons.

---

## 📱 Screenshots & UI Highlights

- **Hero Screen Time Display**: Prominent bold time metrics (`4h 12m`) with zero-drain engine status indicator.
- **Hourly Activity Breakdown**: 24-column bar chart illustrating usage patterns across morning, afternoon, evening, and night.
- **App Breakdown**: Real-time app usage with percentages and duration.
- **Quick Action Menu**: Launch apps or freeze them using root (`pm suspend`) to stop distractions instantly.

---

## 🛠️ Tech Stack

- **Framework**: Jetpack Compose & Material 3 (Compose BOM 2024.05.00)
- **Root Architecture**: [libsu](https://github.com/topjohnwu/libsu) by TopJohnWu (Magisk / KernelSU / APatch compliant)
- **Language**: Kotlin 1.9.24 / Coroutines
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)

---

## 🚀 Building & Releases

### Automated Builds via GitHub Actions
Every commit pushed to `main` triggers a GitHub Actions workflow that compiles both **Debug** and **Release** APKs automatically. You can download the latest build from the **Actions** tab.

### Local Build (Android Studio or CLI)
```bash
# Clone the repository
git clone https://github.com/KaKE-567/FrostTime.git
cd FrostTime

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 📜 License
Licensed under the Apache 2.0 License.
