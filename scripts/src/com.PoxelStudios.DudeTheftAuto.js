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
        const AssemblyCsharp = Il2Cpp.domain.assembly("Assembly-Csharp")
        const Start = AssemblyCsharp.image.class("UIManager").method("Start")
        const UpdateMoneyText = AssemblyCsharp.image.class("UIManager").method("UpdateMoneyText")
        const Bank = AssemblyCsharp.image.class("Bank")

        Start.implementation = function() {
            Start.invokeRaw(this)
            Bank.field("Instance").value.field("MyMoney").value = 999999
            UpdateMoneyText.invokeRaw(this, 999999, false)
        }
    });
}