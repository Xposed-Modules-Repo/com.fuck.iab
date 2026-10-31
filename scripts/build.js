import { execSync } from "node:child_process";
import { readdirSync, existsSync, unlinkSync, readFileSync, writeFileSync, mkdirSync, rmSync } from "node:fs";
import { createHash } from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.join(__dirname, "src");
const ASSETS_DIR = path.join(__dirname, "..", "app", "src", "main", "assets", "scripts");
const DIST_DIR = path.join(__dirname, "dist");
const DIST_SCRIPTS_DIR = path.join(DIST_DIR, "scripts");
// App names for the store, so the manager can name a script's app when it is not installed.
const NAMES_FILE = path.join(__dirname, "names.json");
const appNames = existsSync(NAMES_FILE) ? JSON.parse(readFileSync(NAMES_FILE, "utf8")) : {};

// The global script carries the bridge and is compiled into the apk. Every per-app script
// comes from the store in dist/, which the manager downloads from, so the apk holds one
// copy of each script rather than two.
const GLOBAL = "global";

function cleanOutputDir(dir) {
    if (!existsSync(dir)) return;

    const staleFiles = readdirSync(dir).filter(f => f.endsWith(".js"));
    staleFiles.forEach(f => {
        unlinkSync(path.join(dir, f));
        console.log(`Removed stale: ${f}`);
    });
}

function compile(packageName, outDir) {
    const input = path.join(SRC_DIR, `${packageName}.js`);
    const output = path.join(outDir, `${packageName}.js`);

    if (!existsSync(input)) {
        console.error(`Not found: ${input}`);
        process.exitCode = 1;
        return null;
    }

    console.log(`Compiling: ${packageName}`);

    const inputFirstLine = readFileSync(input, "utf8").split("\n", 1)[0];
    const authorLine = inputFirstLine.startsWith("// author: ") ? inputFirstLine : null;

    if (packageName == GLOBAL) {
        execSync(`npx frida-compile "${input}" -o "${output}" -S -B iife -T none`, { stdio: "inherit" });
    } else {
        execSync(`npx frida-compile "${input}" -o "${output}" -c -S -B iife -T none`, { stdio: "inherit" });

        if (authorLine !== null) {
            const compiled = readFileSync(output, "utf8");
            writeFileSync(output, `${authorLine}\n${compiled}`);
        }
    }

    console.log(`Built: ${path.relative(__dirname, output)}`);

    const bytes = readFileSync(output);

    return {
        package: packageName,
        name: appNames[packageName] ?? null,
        file: `${packageName}.js`,
        sha256: createHash("sha256").update(bytes).digest("hex"),
        size: bytes.length,
        author: authorLine !== null ? authorLine.replace("// author: ", "").trim() : null
    };
}

function writeDistManifest(entries) {
    const manifest = {
        format: 1,
        generatedAt: new Date().toISOString(),
        scripts: entries
    };

    writeFileSync(path.join(DIST_DIR, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
    console.log(`Manifest: ${entries.length} script(s) -> dist/manifest.json`);
}

const args = process.argv.slice(2);

if (args.includes("--dist")) {
    const packages = args.filter(a => a !== "--dist");

    if (existsSync(DIST_DIR)) {
        rmSync(DIST_DIR, { recursive: true, force: true });
    }
    mkdirSync(DIST_SCRIPTS_DIR, { recursive: true });

    const names = (packages.length > 0
        ? packages
        : readdirSync(SRC_DIR).filter(f => f.endsWith(".js")).map(f => f.replace(/\.js$/, "")))
        .filter(name => name !== GLOBAL);

    if (names.length === 0) {
        console.warn("No .js files found.");
    }

    const entries = names.map(name => compile(name, DIST_SCRIPTS_DIR)).filter(e => e !== null);

    writeDistManifest(entries);
} else if (args.includes("--all") || args.length === 0) {
    // Only the global script belongs in the apk; the rest are downloaded from the store.
    cleanOutputDir(ASSETS_DIR);
    compile(GLOBAL, ASSETS_DIR);
} else {
    // A single script is built for trying it out, so it goes to the store copy instead of
    // being packaged into the apk.
    mkdirSync(DIST_SCRIPTS_DIR, { recursive: true });
    args.forEach(name => compile(name, name === GLOBAL ? ASSETS_DIR : DIST_SCRIPTS_DIR));

    if (args.some(name => name !== GLOBAL)) {
        console.log("Run `npm run build:dist` to refresh dist/manifest.json before committing.");
    }
}
