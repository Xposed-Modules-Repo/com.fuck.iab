import { execSync } from "node:child_process";
import { readdirSync, existsSync, unlinkSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.join(__dirname, "src");
// scripts that are never bundled: the app downloads them from the repository on demand
const GLOBAL_DIR = path.join(__dirname, "global");
const OUT_DIR = path.join(__dirname, "..", "app", "src", "main", "assets", "scripts");
const MANIFEST_FILE = path.join(__dirname, "manifest.json");

// top level of manifest.json holds package scripts; the "global" key holds the table of
// scripts from scripts/global, so `src/global.js` must never be written there
const MANIFEST_GLOBAL = "global";

function cleanOutputDir() {
    if (!existsSync(OUT_DIR)) return;

    const staleFiles = readdirSync(OUT_DIR).filter(f => f.endsWith(".js"));
    staleFiles.forEach(f => {
        unlinkSync(path.join(OUT_DIR, f));
        console.log(`Removed stale: ${f}`);
    });
}

/** `// key: value` of the top comment block, or `null` when the tag is missing. */
function readHeader(file, key) {
    const prefix = `// ${key}: `;
    const line = readFileSync(file, "utf8").split("\n").slice(0, 10).find(l => l.startsWith(prefix));
    return line === undefined ? null : line.slice(prefix.length).trim();
}

/** The `// version:` of a script. Missing or unreadable headers count as 1. */
function readVersion(file) {
    const raw = readHeader(file, "version");
    const parsed = raw === null ? Number.NaN : Number.parseInt(raw, 10);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

function requireVersion(file, scriptName) {
    if (readHeader(file, "version") === null) {
        console.warn(`Missing "// version: 1" in ${scriptName}; assuming 1.`);
    }
}

function compile(packageName) {
    const input = path.join(SRC_DIR, `${packageName}.js`);
    const output = path.join(OUT_DIR, `${packageName}.js`);

    if (!existsSync(input)) {
        console.error(`Not found: ${input}`);
        process.exitCode = 1;
        return;
    }

    console.log(`Compiling: ${packageName}`);

    // find headers anywhere in the top comment block so `// name:`/`// url:` can come first
    const srcLines = readFileSync(input, "utf8").split("\n").slice(0, 10);
    const findLine = (key) => srcLines.find(l => l.startsWith(`// ${key}: `));
    const nameLine = findLine("name");
    const urlLine = findLine("url");
    const authorLine = findLine("author")
    const descriptionLine = findLine("description")
    const addedInLine = findLine("added-in");
    const versionLine = findLine("version");
    const summaryLine = findLine("summary");

    if (packageName == "global") {
        execSync(`npx frida-compile "${input}" -o "${output}" -S -B iife -T none`, { stdio: "inherit" });
    } else {
        requireVersion(input, packageName);
        execSync(`npx frida-compile "${input}" -o "${output}" -c -S -B iife -T none`, { stdio: "inherit" });
    }

    const compiled = readFileSync(output, "utf8");
    const headerLines = [nameLine, urlLine, authorLine, descriptionLine, addedInLine, versionLine, summaryLine].filter(Boolean);
    writeFileSync(output, `${headerLines.join("\n")}\n${compiled}`);

    console.log(`Built: assets/scripts/${packageName}.js`);
}

function readManifest() {
    if (!existsSync(MANIFEST_FILE)) return {};

    try {
        return JSON.parse(readFileSync(MANIFEST_FILE, "utf8"));
    } catch (e) {
        console.error(`Could not parse manifest.json: ${e.message}`);
        process.exitCode = 1;
        return {};
    }
}

/** manifest.json is generated, so keep it stable: `global` first, then the packages, both sorted. */
function writeManifest(manifest) {
    const ordered = {};

    const globalTable = manifest[MANIFEST_GLOBAL];
    if (globalTable !== undefined && globalTable !== null && typeof globalTable === "object") {
        ordered[MANIFEST_GLOBAL] = {};
        Object.keys(globalTable).sort().forEach(name => {
            ordered[MANIFEST_GLOBAL][name] = globalTable[name];
        });
    }

    Object.keys(manifest)
        .filter(key => key !== MANIFEST_GLOBAL)
        .sort()
        .forEach(key => { ordered[key] = manifest[key]; });

    writeFileSync(MANIFEST_FILE, JSON.stringify(ordered, null, "\t") + "\n");
}

/** Rewrites manifest.json from every script on disk, dropping entries whose script is gone. */
function rebuildManifest() {
    const manifest = {};

    if (existsSync(GLOBAL_DIR)) {
        const table = {};
        readdirSync(GLOBAL_DIR)
            .filter(f => f.endsWith(".js"))
            .sort()
            .forEach(f => { table[f.replace(/\.js$/, "")] = readVersion(path.join(GLOBAL_DIR, f)); });
        manifest[MANIFEST_GLOBAL] = table;
    }

    readdirSync(SRC_DIR)
        .filter(f => f.endsWith(".js"))
        .sort()
        .forEach(f => {
            const name = f.replace(/\.js$/, "");
            if (name === MANIFEST_GLOBAL) return;
            manifest[name] = readVersion(path.join(SRC_DIR, f));
        });

    writeManifest(manifest);
    console.log(`Updated: scripts/manifest.json`);
}

/** Updates only the compiled package, leaving every other entry alone. */
function syncManifestPackage(packageName) {
    // top level "global" is the table of global scripts, not the bundled global.js
    if (packageName === MANIFEST_GLOBAL) {
        console.log("manifest.json: global.js is bundled, it has no remote entry");
        return;
    }

    const manifest = readManifest();
    manifest[packageName] = readVersion(path.join(SRC_DIR, `${packageName}.js`));
    writeManifest(manifest);
    console.log(`manifest.json: ${packageName} = ${manifest[packageName]}`);
}

/** Updates one entry of the manifest.json "global" table. */
function syncManifestGlobal(name) {
    const manifest = readManifest();
    const table = manifest[MANIFEST_GLOBAL] !== null && typeof manifest[MANIFEST_GLOBAL] === "object" ? manifest[MANIFEST_GLOBAL] : {};

    table[name] = readVersion(path.join(GLOBAL_DIR, `${name}.js`));
    manifest[MANIFEST_GLOBAL] = table;

    writeManifest(manifest);
    console.log(`manifest.json: global.${name} = ${table[name]}`);
}

/** A name resolves to scripts/src first and to scripts/global second. */
function locate(name) {
    const src = path.join(SRC_DIR, `${name}.js`);
    if (existsSync(src)) return { dir: "src", file: src };

    const globalScript = path.join(GLOBAL_DIR, `${name}.js`);
    if (existsSync(globalScript)) return { dir: "global", file: globalScript };

    return null;
}

const args = process.argv.slice(2);

if (args.includes("--all") || args.length === 0) {
    cleanOutputDir();

    const files = readdirSync(SRC_DIR).filter(f => f.endsWith(".js")).sort();
    if (files.length === 0) {
        console.warn("No .js files found.");
    }
    files.forEach(f => compile(f.replace(/\.js$/, "")));

    rebuildManifest();
} else {
    args.forEach(name => {
        const found = locate(name);

        if (found === null) {
            console.error(`Not found: ${name}.js`);
            process.exitCode = 1;
            return;
        }

        if (found.dir === "src") {
            compile(name);
            syncManifestPackage(name);
        } else {
            // global scripts are downloaded as plain source, they are never bundled
            syncManifestGlobal(name);
        }
    });
}
