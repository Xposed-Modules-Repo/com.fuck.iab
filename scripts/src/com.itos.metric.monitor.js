// author: staleroot
Java.perform(() => {

    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge")

    const NativeBridge = FKIAB.use("com.fuck.iab.NativeBridge")

    const bridge = NativeBridge.dexKitBridge.value

    const n14 = Java.use(findClassByStrings(bridge, ["MembershipState(isActive="]).getName())

    n14["$init"].overload('boolean', 'java.lang.Long', 'java.lang.String', 'boolean', 'java.lang.Double', 'java.lang.String').implementation = function (isActive, activatedAtEpochMillis, paymentOrderNumber, homeHeroDismissed, productPriceYuan, productPriceText) {
        // console.log(`n14.$init is called: isActive=${isActive}, activatedAtEpochMillis=${activatedAtEpochMillis}, paymentOrderNumber=${paymentOrderNumber}, homeHeroDismissed=${homeHeroDismissed}, productPriceYuan=${productPriceYuan}, productPriceText=${productPriceText}`);
        
        isActive = true;
        // activatedAtEpochMillis = Java.use("java.lang.Long").valueOf(Date.now());
        // paymentOrderNumber = "FAKE_ORDER_123456";
        // homeHeroDismissed = false;
        // productPriceYuan = Java.use("java.lang.Double").valueOf(9.99);
        // productPriceText = "￥9.99";
        
        this["$init"](isActive, activatedAtEpochMillis, paymentOrderNumber, homeHeroDismissed, productPriceYuan, productPriceText);
    };
});