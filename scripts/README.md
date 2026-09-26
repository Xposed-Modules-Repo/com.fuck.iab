## Setup

Install the dependencies:

```bash
npm install
```

## Build `frida-java-bridge`

This project requires a built version of [`frida-java-bridge`](https://github.com/staleroot/frida-java-bridge).

Build it with:

```bash
npx frida-compile index.js -o out/frida-java-bridge.js -c -S -B esm -T none
```

The generated file will be:

```text
out/frida-java-bridge.js
```

Copy it to the project's `libs/` directory:

```text
libs/frida-java-bridge.js
```

Alternatively, you can use the npm package instead. Add this to your project's `package.json`:

```json
"frida-java-bridge": "^7.0.13"
```

and then run:

```bash
npm install
```

## Test the Script

Before running the script manually, make sure the automatic `startScript()` call in the app is commented out.

Otherwise, the Frida script will be started twice: once manually by you and once automatically by the app. This can cause conflicts and unexpected issues.

Comment out these lines:

```kotlin
//                    log("before start script package name: $packageName")
//                    startScript(packageName, script)
//                    log("after start script")
```

Since `frida-il2cpp-bridge` and `frida-java-bridge` are not imported automatically when testing the script directly, add the following to the beginning of your script:

```javascript
import "frida-il2cpp-bridge";

import JavaBridge from "../libs/frida-java-bridge.js";

export const Java = JavaBridge;

globalThis.FRIDA_JAVA_BRIDGE_DISABLE_JVMTI = true;
```

Now you can run the script directly with Frida:

```bash
frida -U -f com.kiloo.subwaysurf -l ../app/src/main/assets/scripts/com.kiloo.subwaysurf.js
```

Test the script thoroughly and make sure everything works correctly.

## Add the Script to the Module

Once the script has been tested successfully and you're ready to add it to the module, uncomment the automatic script start:

```kotlin
startScript(packageName, script)
```

You should also remove the imports and `globalThis` setup you added at the beginning of the script:

```javascript
import "frida-il2cpp-bridge";

import JavaBridge from "../libs/frida-java-bridge.js";

export const Java = JavaBridge;

globalThis.FRIDA_JAVA_BRIDGE_DISABLE_JVMTI = true;
```

These are already available globally through `global.js`, so keeping them in the script would just be redundant.

After that, build the project again:

```bash
npm run build:all
```

This time, test the script through the module itself rather than running it directly with Frida.

Make sure everything still works correctly when launched by the module.

Once you've confirmed that everything is working, feel free to send a pull request so everyone can use the awesome script you came up with.
