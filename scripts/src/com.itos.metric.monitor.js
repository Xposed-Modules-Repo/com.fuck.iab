// name: Metric
// url: https://t.me/metric_official/10/3298
// author: staleroot
// description: Unlocks premium membership in Metric Monitor by forcing the membership state to active.

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