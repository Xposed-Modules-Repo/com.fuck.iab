// name: gameguardian-api-tracer.js
// summary: Traces GameGuardian Lua API calls.
// author: AbdolAziz
// description: Hooks selected GameGuardian API methods using Frida and logs memory operations, search parameters, value modifications, UI interactions, and Lua table contents.
// opt-in: true
// version: 4

'use strict';

const GG_CONST = {
    TYPE: {
        127: "gg.TYPE_AUTO", 1: "gg.TYPE_BYTE", 2: "gg.TYPE_WORD",
        4: "gg.TYPE_DWORD", 8: "gg.TYPE_XOR", 16: "gg.TYPE_FLOAT",
        32: "gg.TYPE_QWORD", 64: "gg.TYPE_DOUBLE"
    },
    SIGN: {
        0x20000000: "gg.SIGN_EQUAL",
        0x10000000: "gg.SIGN_NOT_EQUAL",
        0x08000000: "gg.SIGN_LESS_OR_EQUAL",
        0x04000000: "gg.SIGN_GREATER_OR_EQUAL"
    },
    REGION: {
        1: "gg.REGION_C_HEAP", 2: "gg.REGION_JAVA_HEAP",
        4: "gg.REGION_C_ALLOC", 8: "gg.REGION_C_DATA",
        16: "gg.REGION_C_BSS", 32: "gg.REGION_ANONYMOUS",
        64: "gg.REGION_STACK", 16384: "gg.REGION_CODE_APP",
        32768: "gg.REGION_CODE_SYS", 65536: "gg.REGION_JAVA",
        131072: "gg.REGION_BAD", 262144: "gg.REGION_PPSSPP",
        524288: "gg.REGION_ASHMEM", 1048576: "gg.REGION_VIDEO",
        [-2080896]: "gg.REGION_OTHER"
    },
    POINTER: {
        0: "0", 1: "gg.POINTER_EXECUTABLE_WRITABLE",
        2: "gg.POINTER_EXECUTABLE", 4: "gg.POINTER_NO",
        8: "gg.POINTER_READ_ONLY", 16: "gg.POINTER_WRITABLE"
    }
};

function formatConst(map, val) {
    return map[val] !== undefined ? map[val] : val.toString();
}
function formatFlags(map, bitmask) {
    if (map[bitmask] !== undefined) return map[bitmask];
    const parts = [];
    for (const [k, name] of Object.entries(map)) {
        const num = parseInt(k, 10);
        if (num > 0 && (bitmask & num) === num) parts.push(name);
    }
    return parts.length > 0 ? parts.join(" | ") : bitmask.toString();
}
function toHex(val) {
    const num = BigInt(val.toString());
    if (num === -1n) return "-0x1";
    return "0x" + num.toString(16).toUpperCase();
}
function quoteStr(val) {
    if (val === null || val === undefined || val.toString() === "nil") return "nil";
    return JSON.stringify(val.toString());
}

// ============================================================
//  1) محوّل جداول LuaJ
// ============================================================
let LuaHelper = null;

function initLuaTableHelper() {
    if (LuaHelper) return LuaHelper;

    try {
        const LuaValue  = Java.use("luaj.LuaValue");
        const LuaTable  = Java.use("luaj.LuaTable");
        const Varargs   = Java.use("luaj.Varargs");
        const Modifier  = Java.use("java.lang.reflect.Modifier");

        // --- البحث عن LuaValue.NIL ---
        let nilObj = null;
        for (const f of LuaValue.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType().getName() !== "luaj.LuaValue") continue;
            f.setAccessible(true);
            try {
                const candidate = f.get(null);
                if (candidate && candidate.toString() === "nil") {
                    nilObj = candidate;
                    break;
                }
            } catch (_) { /* حقل غير مقروء – تخطّي */ }
        }
        if (!nilObj) {
            console.log("[!] لم يتم العثور على LuaValue.NIL");
            return null;
        }

        // --- البحث عن next(LuaValue) -> Varargs ---
        const nextCandidates = [];
        for (const m of LuaTable.class.getDeclaredMethods()) {
            const params = m.getParameterTypes();
            if (params.length === 1 &&
                params[0].getName() === "luaj.LuaValue" &&
                m.getReturnType().getName() === "luaj.Varargs") {
                m.setAccessible(true);
                nextCandidates.push(m);
            }
        }
        if (nextCandidates.length === 0) {
            console.log("[!] لم يتم العثور على next(LuaValue)");
            return null;
        }
        const nextMethod = nextCandidates[nextCandidates.length - 1];

        LuaHelper = { nilObj, nextMethod, Varargs };
        console.log(`[+] LuaJ helper مهيّأ (next → ${nextMethod.getName()})`);
        return LuaHelper;
    } catch (e) {
        console.log(`[!] فشل initLuaTableHelper: ${e.message}`);
        return null;
    }
}

function luaTableToString(tableObj, depth = 0, maxDepth = 3, maxItems = 50) {
    if (tableObj === null || tableObj === undefined) return "nil";
    try {
        if (tableObj.toString() === "nil") return "nil";
    } catch (_) { return "nil"; }

    try {
        const helper = initLuaTableHelper();
        if (!helper) return tableObj.toString();

        const { nilObj, nextMethod, Varargs } = helper;
        if (depth >= maxDepth) return "{...}";

        let key = nilObj;
        const entries = [];
        let seqIndex = 1;
        let isArray  = true;
        let count    = 0;

        while (true) {
            const rawPair = nextMethod.invoke(tableObj, [key]);
            if (!rawPair) break;

            const pair = Java.cast(rawPair, Varargs);
            if (pair.U() === 0 || pair.Q(1)) break;   // نهاية الجدول

            if (count >= maxItems) {
                entries.push("...");
                break;
            }

            const k = pair.v(1);   // المفتاح
            const v = pair.v(2);   // القيمة
            key = k;

            // ---- تنسيق المفتاح ----
            let keyStr = "";
            let rawKeyName = "";
            if (k.R(1)) {                         // isnumber
                const numKey = k.D(1);            // checkint
                if (isArray && numKey === seqIndex) seqIndex++;
                else isArray = false;
                keyStr = `[${numKey}] = `;
            } else {
                isArray = false;
                rawKeyName = k.E(1).toString();   // checkjstring
                keyStr = /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(rawKeyName)
                    ? `${rawKeyName} = `
                    : `[${JSON.stringify(rawKeyName)}] = `;
            }

            // ---- تنسيق القيمة ----
            let valStr = "";
            if (v.T(1)) {                          // istable
                valStr = luaTableToString(v, depth + 1, maxDepth, maxItems);
            } else if (v.R(1)) {                   // isnumber
                const numText = v.E(1).toString();
                if (rawKeyName === "address") {
                    valStr = toHex(numText);
                } else if (rawKeyName === "flags" && GG_CONST.TYPE[numText]) {
                    valStr = GG_CONST.TYPE[numText];
                } else {
                    valStr = numText;
                }
            } else if (v.S(1)) {                   // isstring
                valStr = JSON.stringify(v.E(1).toString());
            } else {
                valStr = v.toString();             // boolean / nil
            }

            entries.push({ isSequential: isArray && k.R(1), keyStr, valStr });
            count++;
        }

        const formatted = entries.map(item =>
            typeof item === "string"
                ? item
                : (isArray ? item.valStr : item.keyStr + item.valStr)
        );
        return `{${formatted.join(", ")}}`;
    } catch (e) {
        try { return `${tableObj.toString()} --[[err: ${e.message}]]`; }
        catch (_) { return `{...} --[[err: ${e.message}]]`; }
    }
}

// ============================================================
//  2) hookGGFunc (مع تمرير instanceClass)
// ============================================================
function hookGGFunc(funcName, formatter, printReturn = false) {
    const fullClassName = "android.ext.Script$" + funcName;
    try {
        const clazz = Java.use(fullClassName);
        const declared = clazz.class.getDeclaredMethods().map(m => m.getName());
        const targetMethodName = declared.includes("c3") ? "c3"
                              : declared.includes("b3") ? "b3"
                              : null;

        if (!targetMethodName) {
            console.log("[-] لم يتم العثور على b3 أو c3 في: " + fullClassName);
            return;
        }

        const targetMethod = clazz[targetMethodName].overload("luaj.Varargs");
        targetMethod.implementation = function (varArgs) {
            const retval = targetMethod.call(this, varArgs);
            try {
                const instanceClass = this.getClass().getName();
                const logLine = formatter(varArgs, instanceClass);
                const retSuffix = printReturn ? ` --[[${retval.tojstring()}]]` : "";
                console.log(logLine + retSuffix);
            } catch (err) {
                console.log(`[!] خطأ في ${funcName}: ${err}`);
            }
            return retval;
        };
        console.log(`[+] تم اعتراض: gg.${funcName} (${targetMethodName})`);
    } catch (e) {
        console.log(`[-] تخطي ${fullClassName}: ${e.message}`);
    }
}

// ============================================================
//  3) تسجيل الدوال
// ============================================================
Java.perform(function () {
    const PRINT_RETURN = false;

    initLuaTableHelper();   // تسخين مبكر

    // ---- دوال الأرقام والذاكرة ----
    hookGGFunc("searchNumber", (args, clsName) => {
        const name = (clsName.endsWith("$refineNumber"))
            ? "gg.refineNumber" : "gg.searchNumber";
        const text      = quoteStr(args.E(1));
        const type      = formatConst(GG_CONST.TYPE, args.Y(2, 127));
        const encrypted = args.V(3, false);
        const sign      = formatConst(GG_CONST.SIGN, args.Y(4, 0x20000000));
        const memFrom   = toHex(args.a0(5, 0));
        const memTo     = toHex(args.a0(6, -1));
        return `${name}(${text}, ${type}, ${encrypted}, ${sign}, ${memFrom}, ${memTo})`;
    }, PRINT_RETURN);

    hookGGFunc("setRanges", (args) =>
        `gg.setRanges(${formatFlags(GG_CONST.REGION, args.D(1))})`, PRINT_RETURN);

    hookGGFunc("getResults", (args) => {
        const maxCount   = args.D(1);
        const skip       = args.Y(2, 0);
        const addrMin    = toHex(args.a0(3, 0));
        const addrMax    = toHex(args.a0(4, -1));
        const valMin     = quoteStr(args.Z(5, "nil"));
        const valMax     = quoteStr(args.Z(6, "nil"));
        const type       = formatConst(GG_CONST.TYPE, args.Y(7, 0));
        const fractional = quoteStr(args.Z(8, "nil"));
        const pointer    = formatConst(GG_CONST.POINTER, args.Y(9, 0));
        return `gg.getResults(${maxCount}, ${skip}, ${addrMin}, ${addrMax}, ${valMin}, ${valMax}, ${type}, ${fractional}, ${pointer})`;
    }, PRINT_RETURN);

    hookGGFunc("clearResults", () => "gg.clearResults()", PRINT_RETURN);

    hookGGFunc("editAll", (args) => {
        const value = quoteStr(args.E(1));
        const type  = formatConst(GG_CONST.TYPE, args.D(2));
        return `gg.editAll(${value}, ${type})`;
    }, PRINT_RETURN);

    hookGGFunc("getRangesList", (args) =>
        `gg.getRangesList(${quoteStr(args.Z(1, ""))})`, PRINT_RETURN);

    // ---- دوال التنبيه ----
    hookGGFunc("alert", (args) => {
        const text = quoteStr(args.E(1));
        const pos  = quoteStr(args.Z(2, "ok"));
        const neg  = quoteStr(args.Z(3, "nil"));
        const neu  = quoteStr(args.Z(4, "nil"));
        return `gg.alert(${text}, ${pos}, ${neg}, ${neu})`;
    }, PRINT_RETURN);

    hookGGFunc("toast", (args) => {
        const text = quoteStr(args.E(1));
        const fast = args.V(2, false);
        return `gg.toast(${text}, ${fast})`;
    }, PRINT_RETURN);

    // ---- دوال الجداول (جديدة) ----
    hookGGFunc("setValues", (args) =>
        `gg.setValues(${luaTableToString(args.I(1))})`, PRINT_RETURN);

    hookGGFunc("addListItems", (args) =>
        `gg.addListItems(${luaTableToString(args.I(1))})`, PRINT_RETURN);

    hookGGFunc("choice", (args) => {
        const items    = luaTableToString(args.I(1));
        const selected = args.v(2).toString();     // number أو nil
        const message  = quoteStr(args.Z(3, "nil"));
        return `gg.choice(${items}, ${selected}, ${message})`;
    }, PRINT_RETURN);

    hookGGFunc("multiChoice", (args) => {
        const items     = luaTableToString(args.I(1));
        const selection = luaTableToString(args.c0(2, null));
        const message   = quoteStr(args.Z(3, "nil"));
        return `gg.multiChoice(${items}, ${selection}, ${message})`;
    }, PRINT_RETURN);

    hookGGFunc("prompt", (args) => {
        const items    = luaTableToString(args.I(1));
        const defaults = luaTableToString(args.c0(2, null));
        const types    = luaTableToString(args.c0(3, null));
        return `gg.prompt(${items}, ${defaults}, ${types})`;
    }, PRINT_RETURN);
});