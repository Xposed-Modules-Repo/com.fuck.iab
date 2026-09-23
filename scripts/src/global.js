import "frida-il2cpp-bridge";

import JavaBridge from "../libs/frida-java-bridge.js";

export const Java = JavaBridge

globalThis.FRIDA_JAVA_BRIDGE_DISABLE_JVMTI = true;

try { APP_SCRIPT_GOES_HERE } catch(e) { console.log(e) }

try { USER_SCRIPT_GOES_HERE } catch(e) { console.log(e) }

if(globalThis["DONT_SEND_READY"] === undefined) {
    send("ready")
}

export function getMethodOverloadFromMethodData(methodData) {
    const declaredClassName = methodData.getDeclaredClass().getName();

    const targetClass = Java.use(declaredClassName);

    const reflectMethod = methodData.getMethodInstance(Java.classFactory.loader);

    const methodName = reflectMethod.getName();

    const parameterClasses = reflectMethod.getParameterTypes();

    const parameterTypes = [];

    for (const parameterClass of parameterClasses) {
        parameterTypes.push(parameterClass.getName());
    }

    return targetClass[methodName].overload(...parameterTypes);
}

export function findFactoryByClassName(className) {

    const loaders = Java.enumerateClassLoadersSync();

    for (const loader of loaders) {
        try {
            const factory = Java.ClassFactory.get(loader);

            factory.use(className);

            return factory;

        } catch (e) {
        }
    }

    return null;
}

export function findClass(name) {
    for (const loader of Java.enumerateClassLoadersSync()) {
        try {
            return Java.ClassFactory.get(loader).use(name);
        } catch (e) {
        }
    }

    return null
}

export function toast(text) {
    var Toast = Java.use("android.widget.Toast");
    Java.scheduleOnMainThread(function () {
        Toast.makeText(getContext(), Java.use("java.lang.String").$new(text), Toast.LENGTH_SHORT.value).show();
    });
}

export function getContext() {
    var ActivityThread = Java.use("android.app.ActivityThread");
    var currentApplication = ActivityThread.currentApplication();
    var context = currentApplication.getApplicationContext();
    return context;
}