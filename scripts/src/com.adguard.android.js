// author: Fexture69
// AdGuard premium bypass that does not rely on obfuscated class or method names.
// It finds the sealed license-state class from a stable string, hooks every boolean
// method that accepts that state, and neutralises the PlusManager state setters.
Java.perform(function () {
    console.log("[*] AdGuard premium bypass starting...");

    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");
    if (!FKIAB) {
        console.log("[-] NativeBridge not found; DexKit is unavailable");
        return;
    }

    const NativeBridge = FKIAB.use("com.fuck.iab.NativeBridge");
    const bridge = NativeBridge.dexKitBridge.value;

    function findClassDataByStrings(strings) {
        const FindClass = FKIAB.use("org.luckypray.dexkit.query.FindClass");
        const ClassMatcher = FKIAB.use("org.luckypray.dexkit.query.matchers.ClassMatcher");
        const ClassData = FKIAB.use("org.luckypray.dexkit.result.ClassData");

        const matcher = ClassMatcher.create().usingStrings(
            Java.array("java.lang.String", strings)
        );
        const results = bridge.findClass(FindClass.create().matcher(matcher));

        const out = [];
        for (let i = 0; i < results.size(); i++) {
            out.push(Java.cast(results.get(i), ClassData));
        }
        return out;
    }

    function findMethodsByParamAndReturn(paramType, returnType) {
        const FindMethod = FKIAB.use("org.luckypray.dexkit.query.FindMethod");
        const MethodMatcher = FKIAB.use("org.luckypray.dexkit.query.matchers.MethodMatcher");
        const MethodData = FKIAB.use("org.luckypray.dexkit.result.MethodData");

        const matcher = MethodMatcher.create()
            .paramTypes(Java.array("java.lang.String", [paramType]))
            .returnType(returnType);
        const results = bridge.findMethod(FindMethod.create().matcher(matcher));

        const out = [];
        for (let i = 0; i < results.size(); i++) {
            out.push(Java.cast(results.get(i), MethodData));
        }
        return out;
    }

    // DexKit normally returns "K0.i$b"; some builds may return "LK0/i$b".
    // Strip the descriptor syntax and the sealed-class suffix to get the base class.
    function javaNameFromDexKit(name) {
        let n = name;
        if (n.charAt(0) === "L" && n.charAt(n.length - 1) === ";") {
            n = n.slice(1, n.length - 1);
        }
        n = n.replace(/\//g, ".");
        const dollar = n.indexOf("$");
        if (dollar > 0) {
            n = n.slice(0, dollar);
        }
        return n;
    }

    // The BlockedLicense state is unique to the license sealed class in every
    // AdGuard release checked (4.6.5, 4.12.3, 4.15.2).
    let stateClass = null;
    try {
        const classes = findClassDataByStrings(["BlockedLicense(licenseKey="]);
        for (const c of classes) {
            if (c.getName().indexOf("$b") !== -1) {
                stateClass = c;
                break;
            }
        }
        if (!stateClass && classes.length > 0) {
            stateClass = classes[0];
        }
    } catch (e) {
        console.log("[-] License state lookup failed: " + e);
    }

    if (!stateClass) {
        console.log("[-] License state class not found");
        return;
    }

    const stateBase = javaNameFromDexKit(stateClass.getName());
    console.log("[+] License state base: " + stateBase);

    let hooked = 0;
    try {
        const methods = findMethodsByParamAndReturn(stateBase, "boolean");
        for (const md of methods) {
            try {
                const overload = getMethodOverloadFromMethodData(md);
                overload.implementation = function (state) {
                    return true;
                };
                hooked++;
                console.log("[+] Hooked boolean state check: " + md.getDeclaredClass().getName() + "." + md.getName());
            } catch (e) {
                console.log("[-] Boolean hook failed: " + e);
            }
        }
    } catch (e) {
        console.log("[-] Boolean method lookup failed: " + e);
    }
    console.log("[+] Hooked " + hooked + " boolean license-state methods");

    // The PlusManager updates the state from the network. Neutralise its setters so
    // a refresh cannot put the app back into the free/expired state.
    try {
        const plusClasses = findClassDataByStrings([
            "null cannot be cast to non-null type com.adguard.android.management.plus.support.PlusState.BlockedLicense"
        ]);
        if (plusClasses.length > 0) {
            const plusName = plusClasses[0].getName();
            const voidMethods = findMethodsByParamAndReturn(stateBase, "void");
            let blocked = 0;

            for (const md of voidMethods) {
                if (md.getDeclaredClass().getName() !== plusName || md.getName() === "<init>") {
                    continue;
                }
                try {
                    const overload = getMethodOverloadFromMethodData(md);
                    overload.implementation = function (state) {
                        console.log("[*] Ignored PlusManager state update: " + md.getName());
                    };
                    blocked++;
                } catch (e) {
                    console.log("[-] PlusManager hook failed: " + e);
                }
            }
            console.log("[+] Neutralised " + blocked + " PlusManager state setters");
        }
    } catch (e) {
        console.log("[-] PlusManager lookup failed: " + e);
    }

    // Fallback for a build where DexKit cannot locate the state class yet.
    if (hooked === 0) {
        try {
            const checker = Java.use("J0.a");
            const methods = checker.class.getDeclaredMethods();
            for (let i = 0; i < methods.length; i++) {
                const m = methods[i];
                const params = m.getParameterTypes();
                if (m.getReturnType().getName() === "boolean" && params.length === 1) {
                    try {
                        checker[m.getName()].overload(params[0].getName()).implementation =
                            function (state) { return true; };
                        hooked++;
                    } catch (e) {}
                }
            }
            console.log("[+] Fallback J0.a hooks: " + hooked);
        } catch (e) {
            console.log("[-] Fallback J0.a unavailable: " + e);
        }
    }

    console.log("[+] AdGuard injection complete. Force-stop AdGuard and reopen it.");
});
