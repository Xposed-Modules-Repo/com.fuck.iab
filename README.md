[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-blue.svg?logo=telegram)](https://t.me/+j3i-S8nMy_o0MThk)
[![Latest Release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.fuck.iab?display_name=release&label=Latest%20Release&color=%23ff9000
)](https://github.com/Xposed-Modules-Repo/com.fuck.iab/releases/latest)

# FKIAB

An Xposed module for hooking and customizing in-app billing functionality.

## Showcase

https://github.com/user-attachments/assets/baa25c2c-baaf-4e84-86e8-51e9f4545a4a

## Compatibility
- [x] Google Play Store
- [x] Bazaar (Iranian App Market)
- [x] Myket (Iranian App Market)
- [ ] Samsung
- [ ] Amazon
- [ ] Huawei

## The App & Modern UI

FKIAB features a modern Material 3 interface with dynamic theming and edge-to-edge support:

- **Home**:
  - **Framework Status**: Real-time status banner indicating Xposed service connection.
  - **Dashboard Cards**: Instant metrics for Hooked Apps, Installed Apps, Enabled Scripts, and Running Processes.
  - **Hooked Applications**: Displays hooked apps with live running badges (process counts and PIDs).
  - **Per-App Menu**: Quick access to App Info, Force Stop (via root), and Google Play Store.

- **Scripts**:
  - **Search & Filter**: Real-time search by app or package name, with filter chips (All, Store, Custom).
  - **Per-App Toggles**: Enable or disable any script on-the-fly with immediate effect.
  - **Script Details Bottom Sheet**: Inspect metadata, SHA-256 checksum (with one-tap copy), origin, and covered apps.
  - **Script Creator (+ FAB)**: Quickly create custom scripts by picking from installed apps or entering a package name.
  - **Store Sync**: Tap **Update scripts** to fetch the latest community scripts directly from the repository.

- **Script Editor**:
  - Dedicated code editor with monospace typography, word wrap toggle, and unsaved changes protection.
  - **Quick Snippets Bar**: One-tap injection of common Frida expressions (Java.use, Il2Cpp.perform, console.log, () => {}, eturn true;, ;).
  - Custom user scripts override remote scripts, with a one-tap option to revert to default.

- **Logs**:
  - Live logcat monitor with search/filtering by tag or keyword.
  - Auto-scroll toggle and Pause / Resume streaming controls.
  - Copy all logs or clear log buffer with one click.
  - Clear diagnosis if root access is missing or denied.

- **Theming**:
  - Material 3 Day / Night mode toggle in the toolbar.
  - Dynamic Color (Monet) on Android 12+ with signature warm amber fallback.

## Custom Frida Scripts

Some apps aren't supported by default because they verify purchase signatures locally or validate them through their own servers. These apps require custom scripts to bypass their specific verification methods.

`
scripts/src/
├── global.js
├── com.adguard.android.js
├── com.kiloo.subwaysurf.js
└── com.PoxelStudios.DudeTheftAuto.js
`

global.js is loaded for every app, before anything else. If a matching <package_name>.js file exists, it runs right after the global script.

### Custom Script Examples

- AdGuard com.adguard.android
- Subway Surfers com.kiloo.subwaysurf
- Ultimate USB com.mixapplications.ultimateusb
- Bike Race com.topfreegames.bikeracefreeworld
- Key Mapper io.github.sds100.keymapper
- 1.1.1.1 com.cloudflare.onedotonedotonedotone
- Metric Monitor com.itos.metric.monitor

### Contributing Custom Scripts

Want to add support for another game or app? Feel free to open a pull request with a custom script.

Scripts should only focus on bypassing purchase checks.

Please include:
- The package name of the app/game
- The custom script
- A brief description of what it does

## Building

Prerequisites:
- Android Studio / Android SDK (API 37, Min SDK 26)
- JDK 17
- Node.js 20+

`ash
# Optional: compile scripts bundle
cd scripts
npm ci
node build.js --all

# Build Debug APK
./gradlew assembleDebug
`

## Telegram Channel

Join the FKIAB Telegram channel to get notified as soon as a new release is available: [t.me/fkiab](https://t.me/+j3i-S8nMy_o0MThk)

## Disclaimer

This project is provided for educational and research purposes only. Use it responsibly and only on applications and environments you own or have permission to test. The authors and contributors are not responsible for any misuse, damage, or violations caused by using this project. Please respect developers, software licenses, and platform rules.

---

**Feel free to open an issue if you have any problems or suggestions.**