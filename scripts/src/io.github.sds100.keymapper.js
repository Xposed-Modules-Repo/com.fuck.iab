// author: you1mak, staleroot
// description: Unlocks KeyMapper Pro, removing premium restrictions on keymapping features without needing a paid account.

Java.perform(function () {

    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge")

    const NativeBridge = FKIAB.use("com.fuck.iab.NativeBridge")

    const bridge = NativeBridge.dexKitBridge.value

    console.log("[*] KeyMapper Premium patch starting...")


    const j05_classData = findClassByStrings(bridge, ["goog_dctxGEarLcMxLQElyvkZgWeHMbU"])
    console.log("j05_classData: " + j05_classData)
    const j05_name = j05_classData.getName()
    console.log("j05_name: " + j05_name)
    const t65_name = findClassByStrings(bridge, ["assistant_trigger", "floating_buttons"], ["support_coffee", "support_lunch"]).getName()
    console.log("t65_name: " + t65_name)
    const t65         = Java.use(t65_name)
    console.log("t65: " + t65)
    const vz5         = Java.use(findClassByStrings(bridge, ["Success(value=", ")"]).getName())
    console.log("vz5: " + vz5)
    const v65         = Java.use(findClassByStrings(bridge, ["RevenueCatState(entitlements=", ", offeringMetadata="]).getName())
    console.log("v65: " + v65)
    const ax5         = Java.use(findClassByStrings(bridge, ["Data(data=", ")"]).getName())
    console.log("ax5: " + ax5)
    const z80_classData = findClassByStrings(bridge, ["CLIENT_TELEMETRY", "onToggleRecordClick", "onToggleRecordClick()V", "onRefreshDeviceInfoClick"])
    console.log("z80_classData: " + z80_classData)
    const z80         = Java.use(z80_classData.getName())
    console.log("z80: " + z80)

    const HashSet     = Java.use("java.util.HashSet");
    const HashMap     = Java.use("java.util.HashMap");
    const BooleanCls  = Java.use("java.lang.Boolean");
    const ExceptionCls= Java.use("java.lang.Exception");
    const Log         = Java.use("android.util.Log");

    
    const j05_p_methodData = findMethodInClassByReturnType(j05_classData, "boolean")
    console.log("j05_p_methodData: " + j05_p_methodData)

    const j05_p = getMethodOverloadFromMethodData(j05_p_methodData)
    console.log("j05_p: " + j05_p)

    j05_p.implementation = function (_customerInfo, _entitlement) {
        return true;
    };

    const j05_c = getMethodOverloadFromMethodData(findMethodInClassByInvokedMethodNames(j05_classData, ["getSharedInstance", "valueOf", "awaitCustomerInfo$default", j05_p_methodData.getName()]))

    console.log("j05_c: " + j05_c)

    j05_c.implementation = function (_ent, _q61) {
        return vz5.$new(BooleanCls.TRUE.value);
    };

    const z80_j = getMethodOverloadFromMethodData(findMethodInClassByParamTypes(z80_classData, ["java.lang.Object"]))

    console.log("z80_j: " + z80_j)

    z80_j.implementation = function (obj) {
        const stack = Log.getStackTraceString(ExceptionCls.$new());
        if (stack.indexOf(j05_name + ".<init>") !== -1) {
            const set = HashSet.$new();
            set.add(t65.$new("ASSISTANT_TRIGGER", 0, "assistant_trigger"));
            set.add(t65.$new("FLOATING_BUTTONS", 1, "floating_buttons"))
            return z80_j.call(z80, ax5.$new(vz5.$new(v65.$new(set, HashMap.$new()))));
        }
        return z80_j.call(z80, obj);
    };

    console.log("[*] KeyMapper Premium patch applied.");
});