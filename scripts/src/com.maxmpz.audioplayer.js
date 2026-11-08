// author: staleroot
setTimeout(() => {

    Java.perform(() => {

        const TAG = "[Poweramp]";

        const PRO_LEVEL = 272;      // >= 229 = licensed

        // Names that are not obfuscated.
        const APPLICATION_CLASS = "com.maxmpz.audioplayer.Application";
        const SYNC_CLASS = "com.maxmpz.audioplayer.Sync";
        const BASE_ACTIVITY_CLASS = "com.maxmpz.audioplayer.common.BaseActivity";
        const MAIN_CLASS = "com.maxmpz.audioplayer.MainActivity";
        const EXPIRED_CLASS = "com.maxmpz.audioplayer.dialogs.ExpiredActivity";
        const MSG_BUS_HELPER_CLASS = "com.maxmpz.widget.MsgBusHelper";
        const R_ID_CLASS = "com.maxmpz.audioplayer.R$id";
        const PACKAGE_NAME = "com.maxmpz.audioplayer";

        const seen = {};

        function logOnce(key, text) {
            if (seen[key] === text) return;
            seen[key] = text;
            console.log(TAG + " " + text);
        }

        // Bus ids are looked up by their resource name, never by value: the
        // numbers are regenerated on every build.
        const busIds = {};

        function busId(name) {
            if (busIds[name] !== undefined) return busIds[name];
            let value = -1;
            try {
                const field = Java.use(R_ID_CLASS)[name];
                if (field !== undefined && field !== null) value = field.value;
            } catch (e) {
            }
            if (value <= 0) {
                try {
                    value = Java.use("android.app.ActivityThread")
                        .currentApplication()
                        .getResources()
                        .getIdentifier(name, "id", PACKAGE_NAME);
                } catch (e) {
                    logOnce("id-" + name, "id " + name + ": " + e.message);
                }
            }
            busIds[name] = value;
            return value;
        }

        // The native licence verdict is the licence-store response: a Bundle
        // carrying "res" (level) and "store" (where it came from).
        function isLicenceResponse(payload) {
            if (payload === null || payload === undefined) return false;
            try {
                const Bundle = Java.use("android.os.Bundle");
                if (!Bundle.class.isInstance(payload)) return false;
                const bundle = Java.cast(payload, Bundle);
                return bundle.containsKey("res") && bundle.containsKey("store");
            } catch (e) {
                return false;
            }
        }

        // -------------------------------------------------------------------
        // Locate the obfuscated classes by their content, not by their names.
        // -------------------------------------------------------------------
        const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");
        const bridge = FKIAB.use("com.fuck.iab.NativeBridge").dexKitBridge.value;

        // Licence prefs holder: keeps a KProperty accessor for the shared
        // native ByteBuffer ("sbuf").
        const licenceClassName = findClassByStrings(
            bridge, ["getSbuf()Ljava/nio/ByteBuffer;"]
        ).getName();

        // Licence helper: works on the licence response bundle.
        const helperClassName = findClassByStrings(
            bridge, ["pending_end_ts"]
        ).getName();

        console.log(TAG + " licence class " + licenceClassName);
        console.log(TAG + " helper class " + helperClassName);

        const Modifier = Java.use("java.lang.reflect.Modifier");

        function hierarchy(clazz) {
            const chain = [];
            let current = clazz;
            while (current !== null) {
                chain.push(current);
                current = current.getSuperclass();
            }
            return chain;
        }

        function isPlainName(name) {
            return /^[A-Za-z0-9_$]+$/.test(name);
        }

        // The int the pref stores its value in: instance, non-final int.
        function findValueField(clazz) {
            const chain = hierarchy(clazz);
            for (let i = 0; i < chain.length; i++) {
                const fields = chain[i].getDeclaredFields();
                for (let j = 0; j < fields.length; j++) {
                    if (fields[j].getType().getName() !== "int") continue;
                    const modifiers = fields[j].getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
                    return {
                        className: "" + chain[i].getName(),
                        fieldName: "" + fields[j].getName()
                    };
                }
            }
            return null;
        }

        // setValue(int): the void (int) method, preferring the obfuscated one
        // (the readable sibling just delegates to it).
        function findSetter(clazz) {
            const chain = hierarchy(clazz);
            let best = null;
            for (let i = 0; i < chain.length; i++) {
                const methods = chain[i].getDeclaredMethods();
                for (let j = 0; j < methods.length; j++) {
                    if (methods[j].getReturnType().getName() !== "void") continue;
                    const params = methods[j].getParameterTypes();
                    if (params.length !== 1 || params[0].getName() !== "int") continue;
                    const candidate = {
                        className: "" + chain[i].getName(),
                        methodName: "" + methods[j].getName()
                    };
                    if (best === null || isPlainName(candidate.methodName) === false) {
                        best = candidate;
                    }
                }
            }
            return best;
        }

        // Playback gate: the readable static boolean of the helper class (the
        // obfuscated one is the signature/tamper flag).
        function findGateField(clazz) {
            const fields = clazz.class.getDeclaredFields();
            for (let i = 0; i < fields.length; i++) {
                if (fields[i].getType().getName() !== "boolean") continue;
                if (!Modifier.isStatic(fields[i].getModifiers())) continue;
                const name = "" + fields[i].getName();
                if (isPlainName(name)) return name;
            }
            return null;
        }

        const helperClass = Java.use(helperClassName);
        const gateField = findGateField(helperClass);

        let pref = null;         // licence level pref instance
        let prefBase = null;     // same instance, viewed through the declaring class
        let valueField = null;
        let setterClass = null;
        let setterName = null;
        let bufAddress = null;

        function resolvePref() {
            if (pref !== null) return true;
            try {
                const holder = Java.use(licenceClassName);
                const instance = holder.H.value;
                if (instance === null || instance === undefined) return false;

                const value = findValueField(instance.getClass());
                const setter = findSetter(instance.getClass());
                if (value === null || setter === null) return false;

                pref = instance;
                valueField = value.fieldName;
                setterClass = Java.use(setter.className);
                setterName = setter.methodName;
                // The value and the setter live on a superclass, so read and
                // write through that class rather than the pref's own one.
                prefBase = Java.cast(instance, Java.use(value.className));
                return true;
            } catch (e) {
                return false;
            }
        }

        // -------------------------------------------------------------------
        // Licence state.
        // -------------------------------------------------------------------
        // Every write to the level goes through the setter, so pin it there
        // instead of racing the native re-check.
        function hookLevelWrites() {
            const setter = setterClass[setterName].overload("int");
            setter.implementation = function (value) {
                let current = value;
                try {
                    if (current < 229 && this.equals(pref)) {
                        current = PRO_LEVEL;
                    }
                } catch (e) {
                    logOnce("equals", "setter compare: " + e.message);
                }
                return setter.call(this, current);
            };
        }

        function bufferAddress(byteBuffer) {
            const chain = hierarchy(byteBuffer.getClass());
            for (let i = 0; i < chain.length; i++) {
                const fields = chain[i].getDeclaredFields();
                for (let j = 0; j < fields.length; j++) {
                    const name = "" + fields[j].getName();
                    if (name !== "address" && name !== "effectiveDirectAddress") continue;
                    try {
                        fields[j].setAccessible(true);
                        const value = fields[j].get(byteBuffer);
                        if (value !== null && value !== undefined && Number(value) > 0) {
                            return Number(value);
                        }
                    } catch (e) {
                    }
                }
            }
            return null;
        }

        function enforce() {
            if (!resolvePref()) return;

            if (prefBase[valueField].value < 229) {
                prefBase[setterName](PRO_LEVEL);
                logOnce("level", "licence level -> " + PRO_LEVEL);
                // The edition name and the rest of the licence UI are derived
                // state, refreshed only on this notification.
                try {
                    const ActivityThread = Java.use("android.app.ActivityThread");
                    const bus = Java.use(MSG_BUS_HELPER_CLASS)
                        .fromContextOrNoop(ActivityThread.currentApplication(), busId("bus_app"));
                    bus.post(busId("msg_version_info_changed"), 0, 0, null);
                } catch (e) {
                    logOnce("refresh", "refresh: " + e.message);
                }
            }

            // The player service drops every play command while this is set.
            if (gateField !== null && helperClass[gateField].value !== false) {
                helperClass[gateField].value = false;
            }

            // Keep the native copy of the level in sync.
            try {
                const buffer = Java.use(licenceClassName).C.value.A.value;
                if (bufAddress === null) {
                    bufAddress = bufferAddress(buffer);
                }
                if (bufAddress !== null && buffer.getInt(4) < 229) {
                    ptr(bufAddress).add(4).writeU32(PRO_LEVEL);
                }
            } catch (e) {
                logOnce("buffer", "buffer: " + e.message);
            }
        }

        // -------------------------------------------------------------------
        // Playback: the native re-check stops the audio engine.
        // -------------------------------------------------------------------
        // The player's own state: 1 == playing. AudioManager.isMusicActive()
        // stays true for a paused engine, so it cannot be used here.
        function isPlaying() {
            try {
                const context = Java.use("android.app.ActivityThread").currentApplication();
                const bus = Java.use("com.maxmpz.widget.StateBusHelper")
                    .fromContextOrThrow(context, busId("bus_player"));
                return bus.getIntState(busId("state_player_playing_state")) === 1;
            } catch (e) {
                return false;
            }
        }

        function togglePlayback() {
            try {
                const ActivityThread = Java.use("android.app.ActivityThread");
                const context = ActivityThread.currentApplication();
                const bus = Java.use(MSG_BUS_HELPER_CLASS)
                    .fromContextOrNoop(context, busId("bus_player_cmd"));
                bus.post(busId("cmd_player_toggle_playing"), 0, 0, null);
                console.log(TAG + " playback resumed");
            } catch (e) {
                logOnce("resume", "resume: " + e.message);
            }
        }

        let lastPlayingAt = 0;

        function resumeAfterExpiryFlow() {
            // Only restart what was already playing; never start a paused player.
            if (Date.now() - lastPlayingAt > 3000) return;
            setTimeout(() => {
                if (!isPlaying()) togglePlayback();
            }, 900);
        }

        // -------------------------------------------------------------------
        // Trial-expired flow.
        // -------------------------------------------------------------------
        let expiryFlowUntil = 0;

        function isExpiredIntent(intent) {
            try {
                const component = intent.getComponent();
                return component !== null &&
                    ("" + component.getClassName()).indexOf(EXPIRED_CLASS) >= 0;
            } catch (e) {
                return false;
            }
        }

        function blockedLaunch() {
            expiryFlowUntil = Date.now() + 3000;
            console.log(TAG + " blocked trial dialog launch");
            resumeAfterExpiryFlow();
        }

        function hookExpiry() {
            const Activity = Java.use("android.app.Activity");
            const ContextWrapper = Java.use("android.content.ContextWrapper");
            const Instrumentation = Java.use("android.app.Instrumentation");

            function wrap(overload) {
                overload.implementation = function () {
                    if (isExpiredIntent(arguments[0])) {
                        blockedLaunch();
                        return;
                    }
                    return overload.apply(this, arguments);
                };
            }

            wrap(Activity.startActivity.overload("android.content.Intent"));
            wrap(Activity.startActivity.overload("android.content.Intent", "android.os.Bundle"));
            wrap(Activity.startActivityForResult.overload("android.content.Intent", "int"));
            wrap(Activity.startActivityForResult.overload("android.content.Intent", "int", "android.os.Bundle"));
            wrap(ContextWrapper.startActivity.overload("android.content.Intent"));
            wrap(ContextWrapper.startActivity.overload("android.content.Intent", "android.os.Bundle"));

            const exec = Instrumentation.execStartActivity.overload(
                "android.content.Context", "android.os.IBinder", "android.os.IBinder",
                "android.app.Activity", "android.content.Intent", "int", "android.os.Bundle"
            );
            exec.implementation = function (who, thread, token, target, intent, requestCode, options) {
                if (isExpiredIntent(intent)) {
                    blockedLaunch();
                    return null;
                }
                return exec.call(this, who, thread, token, target, intent, requestCode, options);
            };

            // The flow finishes MainActivity right after trying to show the
            // dialog; only that call is swallowed, so back/exit keep working.
            const finish = Activity.finish.overload();
            finish.implementation = function () {
                if (Date.now() < expiryFlowUntil && ("" + this.getClass().getName()) === MAIN_CLASS) {
                    console.log(TAG + " blocked MainActivity finish");
                    return;
                }
                return finish.call(this);
            };
        }

        function hookBus() {
            const Application = Java.use(APPLICATION_CLASS);
            const onBusMsg = Application.onBusMsg.overload(
                "com.maxmpz.widget.MsgBus", "int", "int", "int", "java.lang.Object"
            );
            onBusMsg.implementation = function (bus, id, i2, i3, obj) {
                // The native re-check reports the trial level and re-arms the
                // playback gate; the level is forced, so skip the verdict.
                if (isLicenceResponse(obj)) {
                    return;
                }
                return onBusMsg.call(this, bus, id, i2, i3, obj);
            };
        }

        // The expiry flow closes every activity of the running session. Hook the
        // action itself instead of the bus message that triggers it.
        function hookCloseActivities() {
            const BaseActivity = Java.use(BASE_ACTIVITY_CLASS);
            const close = BaseActivity.closeActivities.overload("boolean");
            close.implementation = function (all) {
                console.log(TAG + " blocked close-activities");
            };
        }

        // -------------------------------------------------------------------
        // Native routing gate: BaseApplication.K() passes every routed
        // activity launch to Sync.native_release(context, intent, op), which
        // reports success without starting anything while unlicensed, so
        // Settings and every other screen silently did nothing.
        // -------------------------------------------------------------------
        function hookActivityRouting() {
            const Sync = Java.use(SYNC_CLASS);
            const Intent = Java.use("android.content.Intent");
            const Context = Java.use("android.content.Context");

            function patch(signature) {
                let overload;
                try {
                    overload = Sync.native_release.overload.apply(Sync.native_release, signature);
                } catch (e) {
                    return;
                }
                overload.implementation = function () {
                    const context = arguments[0];
                    const intent = arguments[1];
                    let result = 0;
                    try {
                        result = overload.apply(Sync, arguments);
                    } catch (e) {
                        logOnce("release", "native_release: " + e.message);
                    }
                    try {
                        if (intent !== null && Context.class.isInstance(context) &&
                            Intent.class.isInstance(intent)) {
                            const pending = Java.cast(intent, Intent);
                            if (!isExpiredIntent(pending)) {
                                Java.cast(context, Context).startActivity(pending);
                            }
                        }
                    } catch (e) {
                        logOnce("route", "route: " + e.message);
                    }
                    return result;
                };
            }

            patch(["java.lang.Object", "java.lang.Object", "int"]);
            patch(["java.lang.Object", "java.lang.Object", "java.lang.Object", "int"]);
        }

        // If the expiry flow closed everything before the hooks landed, bring
        // the player back up.
        function ensureUi() {
            try {
                const ActivityThread = Java.use("android.app.ActivityThread");
                const records = ActivityThread.currentActivityThread().mActivities.value;
                if (records.size() > 0) return;

                const intent = Java.use("android.content.Intent").$new();
                intent.setComponent(Java.use("android.content.ComponentName").$new(
                    "com.maxmpz.audioplayer", MAIN_CLASS
                ));
                intent.addFlags(0x10000000);
                Java.cast(
                    ActivityThread.currentApplication(),
                    Java.use("android.content.Context")
                ).startActivity(intent);
            } catch (e) {
                logOnce("recover", "recover: " + e.message);
            }
        }

        // -------------------------------------------------------------------
        function install() {
            console.log(TAG + " Poweramp Pro Bypass active");

            // Suppression first: the native flow posts ~0.5 s in.
            hookExpiry();
            hookBus();
            hookCloseActivities();

            setTimeout(ensureUi, 1500);
            setTimeout(ensureUi, 4000);

            setInterval(() => {
                if (isPlaying()) lastPlayingAt = Date.now();
            }, 1000);

            let attempts = 0;
            const arm = () => {
                attempts++;
                try {
                    if (!resolvePref()) {
                        if (attempts < 60) setTimeout(arm, 250);
                        return;
                    }
                    hookLevelWrites();
                    hookActivityRouting();
                    enforce();
                    console.log(TAG + " licence armed");
                    setInterval(() => {
                        try {
                            enforce();
                        } catch (e) {
                            logOnce("enforce", "enforce: " + e.message);
                        }
                    }, 1000);
                } catch (e) {
                    logOnce("arm", "arm: " + e.message);
                    if (attempts < 60) setTimeout(arm, 250);
                }
            };
            arm();
        }

        install();

    });

}, 0);
