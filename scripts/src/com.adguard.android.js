// author: Fexture69
// description: Removes premium restrictions in AdGuard, unlocking all premium features without a paid license.

Java.perform(function () {
    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");
    if (!FKIAB) return;
    const bridge = FKIAB.use("com.fuck.iab.NativeBridge").dexKitBridge.value;

    // --- helpers ---
    const FindClass = FKIAB.use("org.luckypray.dexkit.query.FindClass");
    const ClassMatcher = FKIAB.use("org.luckypray.dexkit.query.matchers.ClassMatcher");
    const ClassData = FKIAB.use("org.luckypray.dexkit.result.ClassData");
    const FindMethod = FKIAB.use("org.luckypray.dexkit.query.FindMethod");
    const MethodMatcher = FKIAB.use("org.luckypray.dexkit.query.matchers.MethodMatcher");
    const MethodData = FKIAB.use("org.luckypray.dexkit.result.MethodData");

    function findClasses(strings) {
        const m = ClassMatcher.create().usingStrings(Java.array("java.lang.String", strings));
        const r = bridge.findClass(FindClass.create().matcher(m));
        const o = [];
        for (let i = 0; i < r.size(); i++) o.push(Java.cast(r.get(i), ClassData));
        return o;
    }
    function findMethods(param, ret) {
        const m = MethodMatcher.create()
            .paramTypes(Java.array("java.lang.String", [param]))
            .returnType(ret);
        const r = bridge.findMethod(FindMethod.create().matcher(m));
        const o = [];
        for (let i = 0; i < r.size(); i++) o.push(Java.cast(r.get(i), MethodData));
        return o;
    }
    function baseName(name) {
        let n = name.replace(/^L|;$/g, "").replace(/\//g, ".");
        const d = n.indexOf("$");
        return d > 0 ? n.slice(0, d) : n;
    }

    // --- locate license state class ---
    let stateClass = null;
    try {
        const cs = findClasses(["BlockedLicense(licenseKey="]);
        for (const c of cs) {
            if (c.getName().indexOf("$b") !== -1) { stateClass = c; break; }
        }
        if (!stateClass && cs.length) stateClass = cs[0];
    } catch (e) {}
    if (!stateClass) return;

    const stateBase = baseName(stateClass.getName());

    // --- hook boolean state checks ---
    let hooked = 0;
    try {
        for (const md of findMethods(stateBase, "boolean")) {
            try {
                getMethodOverloadFromMethodData(md).implementation = function () { return true; };
                hooked++;
            } catch (e) {}
        }
    } catch (e) {}

    // --- neutralise PlusManager setters ---
    try {
        const pcs = findClasses([
            "null cannot be cast to non-null type com.adguard.android.management.plus.support.PlusState.BlockedLicense"
        ]);
        if (pcs.length) {
            const plusName = pcs[0].getName();
            for (const md of findMethods(stateBase, "void")) {
                if (md.getDeclaredClass().getName() !== plusName || md.getName() === "<init>") continue;
                try {
                    getMethodOverloadFromMethodData(md).implementation = function () {};
                } catch (e) {}
            }
        }
    } catch (e) {}

    // --- fallback if DexKit found nothing ---
    if (hooked === 0) {
        try {
            const checker = Java.use("J0.a");
            const methods = checker.class.getDeclaredMethods();
            for (let i = 0; i < methods.length; i++) {
                const m = methods[i];
                const p = m.getParameterTypes();
                if (m.getReturnType().getName() === "boolean" && p.length === 1) {
                    try {
                        checker[m.getName()].overload(p[0].getName()).implementation =
                            function () { return true; };
                        hooked++;
                    } catch (e) {}
                }
            }
        } catch (e) {}
    }

    // one status line at the end — no per-hook spam
    console.log("[+] AdGuard bypass: " + hooked + " state methods hooked");
});
