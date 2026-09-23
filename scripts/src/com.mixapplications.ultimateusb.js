setTimeout(() => {
    
    Java.perform(() => {

        const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge")
    
        const NativeBridge = FKIAB.use("com.fuck.iab.NativeBridge")

        const bridge = NativeBridge.dexKitBridge.value

        const FindMethod = FKIAB.use("org.luckypray.dexkit.query.FindMethod");

        const MethodMatcher = FKIAB.use("org.luckypray.dexkit.query.matchers.MethodMatcher");

        const strings = Java.array("java.lang.String", [
            "isValid",
            "error",
            "purchaseData",
            "purchaseState",
            "consumptionState"
        ]);

        const matcher = MethodMatcher.create().usingStrings(strings);

        const query = FindMethod.create().matcher(matcher);

        const results = bridge.findMethod(query);

        if(results.size() != 1) {
            throw new Error(`Found ${results.size()} methods`);
        }
        
        const methodData = Java.cast(results.get(0), FKIAB.use("org.luckypray.dexkit.result.MethodData"));

        const method = getMethodOverloadFromMethodData(methodData)

        method.implementation = function () {
            return Java.use("java.lang.Integer").valueOf(0)
        };
    })
}, 0)