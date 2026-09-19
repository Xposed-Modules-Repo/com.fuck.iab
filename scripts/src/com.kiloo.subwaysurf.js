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
    console.log("Hooking GetCurrency method...");
    Il2Cpp.perform(() => {
        Il2Cpp.domain.assemblies.forEach(a => {
            a.image.classes.forEach(c => {
                c.methods.forEach(m => {
                    if(m.name.includes("GetCurrency") && m.returnType.name === "System.Int32") {
                        m.implementation = function(arg) {
                            return 999999;
                        };
                    }
                });
            });
        });

        // const AssemblyCSharp = Il2Cpp.domain.assembly("Assembly-CSharp").image
        // const IAPValidateResponse = AssemblyCSharp.class("SYBO.Subway.Services.IAPValidateResponse");
        // const GetResult = Il2Cpp.corlib.class("System.Runtime.CompilerServices.TaskAwaiter`1").inflate(IAPValidateResponse).method("GetResult");
        // const None = AssemblyCSharp.class("SYBO.Subway.Meta.PurchaseError").field("None").value

        // GetResult.implementation = function () {
        //     try {
        //         const result = GetResult.invokeRaw(this)
        //         if(result && !result.isNull() && result.class.name == "IAPValidateResponse") {
        //             result.field("PurchaseError").value = None
        //             result.field("ProductId").value = Il2Cpp.string("subwaysurfers.coins.01")
        //         }
        //         return result
        //     } catch(e) {
        //         return ptr(0)
        //     }
        // };
    });
}