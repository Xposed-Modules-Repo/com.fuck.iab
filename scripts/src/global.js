import "frida-il2cpp-bridge";

import JavaBridge from "../libs/frida-java-bridge.js";

export const Java = JavaBridge

// Android 17 needs the JVMTI plugin for the bridge to find the Thread fields, while
// loading that plugin late on older releases can abort ART, so it is only asked for
// where it is required.
function androidSdkInt() {
    try {
        const libc = Process.findModuleByName('libc.so');
        const getprop = new NativeFunction(
            libc.findExportByName('__system_property_get'),
            'int',
            ['pointer', 'pointer']
        );
        const value = Memory.alloc(92); // PROP_VALUE_MAX

        getprop(Memory.allocUtf8String('ro.build.version.sdk'), value);

        return parseInt(value.readUtf8String() || '0', 10);
    } catch (e) {
        return 0;
    }
}

const androidSdk = androidSdkInt();

globalThis.FRIDA_JAVA_BRIDGE_DISABLE_JVMTI = androidSdk > 0 && androidSdk < 37;

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

export function findClassByStrings(bridge, strings, notStrings = []) {
    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");

    const FindClass = FKIAB.use(
        "org.luckypray.dexkit.query.FindClass"
    );

    const ClassMatcher = FKIAB.use(
        "org.luckypray.dexkit.query.matchers.ClassMatcher"
    );

    const ClassData = FKIAB.use(
        "org.luckypray.dexkit.result.ClassData"
    );

    const matcher = ClassMatcher.create();

    if (strings.length > 0) {
        matcher.usingStrings(
            Java.array("java.lang.String", strings)
        );
    }

    for (const s of notStrings) {
        const excludedMatcher = ClassMatcher.create().usingStrings(
            Java.array("java.lang.String", [s])
        );

        matcher.addNoneOf(excludedMatcher);
    }

    const query = FindClass.create().matcher(matcher);
    const results = bridge.findClass(query);

    if (results.size() !== 1) {
        throw new Error(`Found ${results.size()} classes`);
    }

    return Java.cast(results.get(0), ClassData);
}

export function findMethodInClassByReturnType(classData, returnType) {
    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");

    const FindMethod = FKIAB.use(
        "org.luckypray.dexkit.query.FindMethod"
    );

    const MethodMatcher = FKIAB.use(
        "org.luckypray.dexkit.query.matchers.MethodMatcher"
    );

    const MethodData = FKIAB.use(
        "org.luckypray.dexkit.result.MethodData"
    );

    const matcher = MethodMatcher.create().returnType(returnType);
    const query = FindMethod.create().matcher(matcher);
    const results = classData.findMethod(query);

    if (results.size() !== 1) {
        throw new Error(
            `Found ${results.size()} methods with return type "${returnType}"`
        );
    }

    return Java.cast(results.get(0), MethodData);
}

export function findMethodInClassByStringsAndNumbers(classData, strings, numbers) {
    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");

    const FindMethod = FKIAB.use(
        "org.luckypray.dexkit.query.FindMethod"
    );

    const MethodMatcher = FKIAB.use(
        "org.luckypray.dexkit.query.matchers.MethodMatcher"
    );

    const MethodData = FKIAB.use(
        "org.luckypray.dexkit.result.MethodData"
    );

    const matcher = MethodMatcher.create();

    if (strings.length > 0) {
        matcher.usingStrings(
            Java.array("java.lang.String", strings)
        );
    }

    if (numbers.length > 0) {
        const ArrayList = Java.use("java.util.ArrayList");
        const Integer = Java.use("java.lang.Integer");
        const Double = Java.use("java.lang.Double");

        const numberList = ArrayList.$new();

        numbers.forEach(n => {
            const boxed = Number.isInteger(n)
                ? Integer.valueOf(n)
                : Double.valueOf(n);

            numberList.add(boxed);
        });

        matcher.usingNumbers
            .overload("java.util.Collection")
            .call(matcher, numberList);
    }

    const query = FindMethod.create().matcher(matcher);
    const results = classData.findMethod(query);

    if (results.size() !== 1) {
        throw new Error(
            `Found ${results.size()} methods for strings=${JSON.stringify(strings)}, ` +
            `numbers=${JSON.stringify(numbers)}`
        );
    }

    return Java.cast(results.get(0), MethodData);
}

export function findMethodInClassByParamTypes(classData, paramTypes) {
    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");

    const FindMethod = FKIAB.use(
        "org.luckypray.dexkit.query.FindMethod"
    );

    const MethodMatcher = FKIAB.use(
        "org.luckypray.dexkit.query.matchers.MethodMatcher"
    );

    const MethodData = FKIAB.use(
        "org.luckypray.dexkit.result.MethodData"
    );

    const matcher = MethodMatcher.create().paramTypes(
        Java.array("java.lang.String", paramTypes)
    );

    const query = FindMethod.create().matcher(matcher);
    const results = classData.findMethod(query);

    if (results.size() !== 1) {
        throw new Error(
            `Found ${results.size()} methods with parameters: (${paramTypes.join(", ")})`
        );
    }

    return Java.cast(results.get(0), MethodData);
}

export function printMethods(clazz) {
    const methods = clazz.class.getDeclaredMethods();

    console.log(`\n=== Methods of ${clazz.$className} ===`);

    for (let i = 0; i < methods.length; i++) {
        console.log(methods[i].toString());
    }

    console.log(`=== Total: ${methods.length} methods ===\n`);
}

export function findMethodInClassByInvokedMethodNames(classData, methodNames) {
    const FKIAB = findFactoryByClassName("com.fuck.iab.NativeBridge");

    const FindMethod = FKIAB.use(
        "org.luckypray.dexkit.query.FindMethod"
    );

    const MethodMatcher = FKIAB.use(
        "org.luckypray.dexkit.query.matchers.MethodMatcher"
    );

    const MethodsMatcher = FKIAB.use(
        "org.luckypray.dexkit.query.matchers.MethodsMatcher"
    );

    const MethodData = FKIAB.use(
        "org.luckypray.dexkit.result.MethodData"
    );

    const invokedMethods = MethodsMatcher.create();

    methodNames.forEach(name => {
        invokedMethods.add(
            MethodMatcher.create().name(name)
        );
    });

    const matcher = MethodMatcher.create()
        .invokeMethods(invokedMethods);

    const query = FindMethod.create().matcher(matcher);
    const results = classData.findMethod(query);

    if (results.size() !== 1) {
        throw new Error(
            `Found ${results.size()} methods invoking: ${methodNames.join(", ")}`
        );
    }

    return Java.cast(results.get(0), MethodData);
}