[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-blue.svg?logo=telegram)](https://t.me/+j3i-S8nMy_o0MThk)
[![Latest Release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.fuck.iab?display_name=release&label=Latest%20Release&color=%23ff9000
)](https://github.com/Xposed-Modules-Repo/com.fuck.iab/releases/latest)

# FKIAB

An Xposed module for hooking and customizing in-app billing functionality.

## Showcase

https://github.com/user-attachments/assets/baa25c2c-baaf-4e84-86e8-51e9f4545a4a

## Compatibility
- [x] Google Play Store
- [x] Bazaar
- [x] Myket
- [ ] Samsung
- [ ] Amazon
- [ ] Huawei

## The app

Home lists the apps the module is loaded in, with the processes it is running in. Each row has a three-dot menu with app info, force stop and Play Store.

Scripts lists every script with an on/off switch. Tapping a row opens the editor, where your own copy can be written; the same menu appears on each row. **Update scripts** downloads the newer scripts from the repository.

Logs shows what the module and the scripts print. It reads logcat, so it needs root access.

## Scripts

`scripts/src/global.js` runs in every hooked app before anything else. It sets up the Frida Java and il2cpp bridges, so a per-app script can just use `Java` and `Il2Cpp`. A file named after the app, such as `scripts/src/com.kiloo.subwaysurf.js`, runs right after the global one.

`global.js` is compiled into the APK during the build. Per-app scripts are not: they live in `scripts/dist` and the app downloads them, so a new or fixed script does not need a new APK.

To add one:

1. Add `scripts/src/<package name>.js` with a `// author: your name` first line.
2. Run `npm run build:dist` in `scripts/`. On `ui`, `dev` and `main` CI does this and commits the result itself.
3. In the app, tap **Update scripts**.

A script runs when its app starts, so restart the app after an update. Force stop in the row menu does that for you. If you edit a script in the app, your copy is used until you remove it, and **Update scripts** leaves it alone.

## Building

You need JDK 17, Node 20, and the Android SDK with NDK `30.0.16248370` and CMake `3.22.1`.

The native wrapper links a QuickJS-only gumjs devkit that has to be built first:

```
git clone --depth 1 --branch 17.24.3 --recurse-submodules https://github.com/frida/frida-gum.git frida-gum
ANDROID_NDK_ROOT=~/Android/Sdk/ndk/30.0.16248370 GUM_NDK_REQUIRED=30 bash ./build-frida-gum.sh frida-gum .
```

`global.js` imports `scripts/libs/frida-java-bridge.js`, which git does not track. Create it once:

```
cd scripts
npm ci
npm install --no-save https://github.com/staleroot/frida-java-bridge/archive/55e0dc8a26dd7b858fbcae55a93d16826e576c64.tar.gz
mkdir -p libs && printf 'export { default } from "frida-java-bridge";\n' > libs/frida-java-bridge.js
```

Then build. `compileFridaScripts` runs before `preBuild`, so `global.js` is compiled for you:

```
./gradlew assembleDebug
```

APKs are built for `arm64-v8a` and `armeabi-v7a`, plus a universal one. A release is only signed when a keystore is available:

```
KEYSTORE_FILE=/path/to/keystore.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
  ./gradlew assembleRelease
```

The app needs to know where to download scripts from. Pass `-PscriptsBaseUrl=https://.../scripts/dist/` or put `scriptsBaseUrl` in `local.properties`; CI works it out from the repository it builds. A debug build without either has no store and says so.

## Contributing

Open **Add a script** in the issue tracker and fill in the form: package name, app name, who wrote it, the script and one line about what it does. A bot turns it into a pull request with the script in `scripts/src`, its name in `scripts/names.json` and the store rebuilt. You do not need to know git.

A script should only bypass the purchase checks.

## Telegram

Join the FKIAB Telegram channel to get notified as soon as a new release is available: [t.me/fkiab](https://t.me/+j3i-S8nMy_o0MThk)

## Disclaimer

This project is provided for educational and research purposes only. Use it responsibly and only on applications and environments you own or have permission to test. The authors and contributors are not responsible for any misuse, damage, or violations caused by using this project. Please respect developers, software licenses, and platform rules.

---

**Feel free to open an issue if you have any problems or suggestions.**
