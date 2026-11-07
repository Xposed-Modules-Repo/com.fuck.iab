// Android 17 returns opaque JNI field IDs, so the ART class-model fast path cannot
// map a jfieldID back to an ArtField. Use the bridge's JNI reflection path there.
// The bridge is installed from the pinned tarball, so this is applied to
// node_modules before frida-compile bundles it.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const dir = path.dirname(fileURLToPath(import.meta.url));
const file = path.join(dir, "node_modules", "frida-java-bridge", "lib", "class-model.js");

function fail(what) {
    console.error(`patch-bridge: could not find ${what} in the installed bridge`);
    process.exit(1);
}

if (!existsSync(file)) {
    console.warn("patch-bridge: frida-java-bridge is not installed, skipping");
    process.exit(0);
}

let source = readFileSync(file, "utf8");

if (source.includes("androidSdkLevel")) {
    process.exit(0);
}

const importLine = "from './android.js';";
const importIndex = source.indexOf(importLine);
if (importIndex === -1) fail("the bridge import");

const helper = `
function androidSdkLevel () {
  try {
    const getprop = new NativeFunction(
      Process.getModuleByName('libc.so').getExportByName('__system_property_get'),
      'int',
      ['pointer', 'pointer']);
    const value = Memory.alloc(92);
    getprop(Memory.allocUtf8String('ro.build.version.sdk'), value);
    return parseInt(value.readUtf8String() || '0', 10);
  } catch (e) {
    return 0;
  }
}
`;

const importEnd = source.indexOf("\n", importIndex);
source = source.slice(0, importEnd + 1) + helper + source.slice(importEnd + 1);

const artBlock = `  if (api.flavor === 'art') {
    let artClassOffsets;
    if (jvmti !== null) {
      artClassOffsets = [0, 0, 0, 0];
    } else {
      const c = getArtClassSpec(vm).offset;
      artClassOffsets = [c.ifields, c.methods, c.sfields, c.copiedMethodsOffset];
    }`;

if (!source.includes(artBlock)) fail("the ART class-model block");

source = source.replace(artBlock, `  if (api.flavor === 'art') {
    let artClassOffsets;
    // Android 17 hands out opaque JNI field IDs, which the ART fast path cannot map
    // back to ArtField pointers. Fall back to the JNI reflection path there.
    const useArt = jvmti !== null || androidSdkLevel() < 37;
    if (!useArt || jvmti !== null) {
      artClassOffsets = [0, 0, 0, 0];
    } else {
      const c = getArtClassSpec(vm).offset;
      artClassOffsets = [c.ifields, c.methods, c.sfields, c.copiedMethodsOffset];
    }`);

const flagPattern = /(\n\s*)1,(\s*\n\s*\.\.\.artClassOffsets,)/;
if (!flagPattern.test(source)) fail("the ART availability flag");
source = source.replace(flagPattern, "$1useArt ? 1 : 0,$2");

writeFileSync(file, source);
console.log("patch-bridge: Android 17 class-model fallback applied");
