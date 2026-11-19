// name: screenshot-unlocker.js
// summary: Unlocks screenshots and screen recording
// author: staleroot
// description: Unlocks screenshots and screen recording in apps that normally block them, like banking and streaming apps.
// opt-in: true
// version: 1

Java.perform(function () {
    const Window = Java.use("android.view.Window");
    const FLAG_SECURE = 0x2000;

    Window.setFlags.overload("int", "int").implementation = function (flags, mask) {
        if ((flags & FLAG_SECURE) !== 0) {
            flags = flags & ~FLAG_SECURE;
        }
        return this.setFlags(flags, mask);
    };
});