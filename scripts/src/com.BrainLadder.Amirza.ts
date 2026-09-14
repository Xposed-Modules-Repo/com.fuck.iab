// @ts-nocheck

import "frida-il2cpp-bridge";

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
        const Decode = Il2Cpp.domain.assembly("Assembly-Csharp").image.class("StorageManager").method("Decode")

        Decode.implementation = function(key, def) {
            if(key.content == "co") {
                return Il2Cpp.string("999999")
            } else {
                return Decode.invokeRaw(this, key, def)
            }
        }
    });
}