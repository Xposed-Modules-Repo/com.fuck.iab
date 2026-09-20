const observer = Process.attachModuleObserver({
    onAdded(module) {
        if (module.name === "libil2cpp.so") {
            console.log("Loaded:", module.name);
            setImmediate(() => {
                observer.detach();
                Hook();
            });
        }
    }
});

function Hook() {
    Il2Cpp.perform(() => {
        const AssemblyCSharp = Il2Cpp.domain.assembly("Assembly-CSharp").image
        const IAPValidateResponse = AssemblyCSharp.class("SYBO.Subway.Services.IAPValidateResponse");
        const GetResult = Il2Cpp.corlib.class("System.Runtime.CompilerServices.TaskAwaiter`1").inflate(IAPValidateResponse).method("GetResult");
        const PurchaseStatus = AssemblyCSharp.class("SYBO.Subway.Meta.PurchaseEventData/PurchaseStatus")
        const PurchaseStatusSuccess = PurchaseStatus.field("Success").value

        GetResult.implementation = function () {
            try {
                const result = GetResult.invokeRaw(this)
                if(result && !result.isNull()) {
                    if(result.class.name == "PurchaseEventData") {
                        result.field("<Status>k__BackingField").value = PurchaseStatusSuccess
                    }
                }
                return result
            } catch(e) {
                return ptr(0)
            }
        };
    });
}