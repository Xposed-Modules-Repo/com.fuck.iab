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
    });
}