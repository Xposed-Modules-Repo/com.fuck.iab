// name: آميرزا
// url: https://cafebazaar.ir/app/com.BrainLadder.Amirza
// author: staleroot
// description: Gives you 999,999 coins in Amirza.

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