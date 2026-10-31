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

## Features

- Support for restoring previously purchased items.
- Custom Frida scripts, compiled into the APK per app.
- Manager app with Home, Scripts and Logs pages.
- Per-app script switches, a script editor and a revert to the bundled version.
- Live module log, including the output of the running scripts.

## Custom Frida Scripts

Some apps aren't supported by the module because they either verify purchase signatures locally or validate them through their own servers. These apps require custom scripts to bypass their specific verification methods.

```
scripts/src/
├── global.js
├── com.kiloo.subwaysurf.js
└── com.PoxelStudios.DudeTheftAuto.js
```

`global.js` is loaded for every app, before anything else. If a matching `<package_name>.js` file also exists, it runs right after the global script.

The scripts in `scripts/src` are compiled into `assets/scripts` during the build, so the module carries them itself. Only `global.js` has imports to bundle (the il2cpp bridge and the Java bridge); the per-app scripts are plain scripts that use the `Java` and `Il2Cpp` globals that `global.js` provides.

A script can be edited in the manager app. The saved copy lives in the module's shared directory and is used instead of the bundled script until you pick **Restore bundled version**, and the switch on the Scripts page stops a script from running for that app without touching the APK.

### Custom Script Examples

- Subway Surfers `com.kiloo.subwaysurf`

<img width="270" height="362" alt="ss0" src="https://github.com/user-attachments/assets/8b527efd-b4d9-47e8-a213-e09ef9f937c1" />

- Ultimate USB `com.mixapplications.ultimateusb`

- Bike Race `com.topfreegames.bikeracefreeworld`

### Remote script store

Per-app scripts can be published on their own, so a new script reaches the manager without a new APK:

- `npm run build:dist` in `scripts/` compiles every per-app script into `scripts/dist/` and writes `dist/manifest.json` with the size, author and SHA-256 of each file. The global script is left out: it carries the bridge and is compiled into the APK.
- `scripts/dist/` is committed, so the store lives in the same branch as the code and the manager reads it straight from the repository. The Update script store workflow rebuilds it when a file in `scripts/src/` changes on `dev` or `main`.
- The store address is a build setting rather than part of the repository: pass `-PscriptsBaseUrl=...` or put `scriptsBaseUrl` in `local.properties`, and CI derives it from the repository that is building. The Scripts screen updates from it with the **Update scripts** action.

A fresh install asks for the store once by itself when the Scripts screen finds nothing, so the list is not blank after installing; after that, **Update scripts** is the only thing that fetches.

Downloaded copies are stored next to the user copies. The module runs a user copy if there is one, else the synced copy. The only script packaged into the APK is the global one, so a per-app script has a single source.

### Contributing Custom Scripts

Want to add support for another game or app? Feel free to open a pull request with a custom script.

Scripts should only focus on bypassing purchase checks.

Please include:
- The package name of the app/game
- The custom script
- A brief description of what it does

## Building

Requirements: JDK 17, the Android SDK with NDK `30.0.16248370` and CMake `3.22.1`, and Node 20. One NDK serves both the devkit and the app; the script moves releng's revision pin to the one it is given.

The native wrapper links a QuickJS-only gumjs devkit built from source, so it has to exist before Gradle runs:

```
git clone --depth 1 --branch 17.24.3 --recurse-submodules https://github.com/frida/frida-gum.git frida-gum
ANDROID_NDK_ROOT=~/Android/Sdk/ndk/30.0.16248370 GUM_NDK_REQUIRED=30 bash ./build-frida-gum.sh frida-gum .
```

`global.js` imports `scripts/libs/frida-java-bridge.js`, which git does not track. Create it once with the package it re-exports:

```
cd scripts
npm ci
npm install --no-save https://github.com/staleroot/frida-java-bridge/archive/55e0dc8a26dd7b858fbcae55a93d16826e576c64.tar.gz
mkdir -p libs && printf 'export { default } from "frida-java-bridge";\n' > libs/frida-java-bridge.js
```

Then build. `compileFridaScripts` runs before `preBuild`, so the scripts are compiled automatically:

```
./gradlew assembleDebug
```

The APKs are produced for `arm64-v8a` and `armeabi-v7a`, plus a universal one. A release build is only signed when the keystore is available:

```
KEYSTORE_FILE=/path/to/keystore.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
  ./gradlew assembleRelease
```

GitHub Actions runs the same steps on pushes and pull requests, and publishes the debug and signed release APKs as artifacts.

## Telegram Channel

Join the FKIAB Telegram channel to get notified as soon as a new release is available: [t.me/fkiab](https://t.me/+j3i-S8nMy_o0MThk)

## Disclaimer

This project is provided for educational and research purposes only. Use it responsibly and only on applications and environments you own or have permission to test. The authors and contributors are not responsible for any misuse, damage, or violations caused by using this project. Please respect developers, software licenses, and platform rules.

---

**Feel free to open an issue if you have any problems or suggestions.**
