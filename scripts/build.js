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

// The global script carries the bridge and is compiled into the apk, so the store that the
// manager downloads from holds per-app scripts only.
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
    cleanOutputDir(ASSETS_DIR);

    const files = readdirSync(SRC_DIR).filter(f => f.endsWith(".js"));
    if (files.length === 0) {
        console.warn("No .js files found.");
    }
    files.forEach(f => compile(f.replace(/\.js$/, ""), ASSETS_DIR));
} else {
    args.forEach(name => compile(name, ASSETS_DIR));
}
