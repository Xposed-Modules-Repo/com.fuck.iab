// author: staleroot
Java.perform(() => {

    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge")

    const NativeBridge = FKIAB.use("com.fuck.iab.NativeBridge")

    const bridge = NativeBridge.dexKitBridge.value

    const MembershipState = Java.use(findClassByStrings(bridge, ["MembershipState(isActive="]).getName())

    MembershipState.$init.overloads.forEach(ctor => {
        ctor.implementation = function () {
            arguments[0] = true

            ctor.apply(this, arguments);
        };
    });
});