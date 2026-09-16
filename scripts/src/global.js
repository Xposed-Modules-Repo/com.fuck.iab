import "frida-il2cpp-bridge";

import JavaBridge from "frida-java-bridge";

export const Java = JavaBridge;

JavaBridge.perform(() => {
    send("java_ready");

    try { APP_SCRIPT_GOES_HERE } catch(e) {}

    try { USER_SCRIPT_GOES_HERE } catch(e) {}
});

export function toast(text) {
    var Toast = JavaBridge.use("android.widget.Toast");
    JavaBridge.scheduleOnMainThread(function () {
        Toast.makeText(getContext(), JavaBridge.use("java.lang.String").$new(text), Toast.LENGTH_SHORT.value).show();
    });
}

function getContext() {
    var ActivityThread = JavaBridge.use("android.app.ActivityThread");
    var currentApplication = ActivityThread.currentApplication();
    var context = currentApplication.getApplicationContext();
    return context;
}
