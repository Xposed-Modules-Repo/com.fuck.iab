import { execSync } from "node:child_process";
import { readdirSync, existsSync, unlinkSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.join(__dirname, "src");
const OUT_DIR = path.join(__dirname, "..", "app", "src", "main", "assets", "scripts");

function cleanOutputDir() {
    if (!existsSync(OUT_DIR)) return;

    const staleFiles = readdirSync(OUT_DIR).filter(f => f.endsWith(".js"));
    staleFiles.forEach(f => {
        unlinkSync(path.join(OUT_DIR, f));
        console.log(`Removed stale: ${f}`);
    });
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

    const inputLines = readFileSync(input, "utf8").split("\n", 3);
    const authorLine = inputLines[0]?.startsWith("// author: ") ? inputLines[0] : "// author: ";
//    const versionLine = inputLines[1]?.startsWith("// version: ") ? inputLines[1] : "// version: ";
    const descriptionLine = inputLines[1]?.startsWith("// description: ") ? inputLines[1] : "// description: ";

    execSync(`npx frida-compile "${input}" -o "${output}" -c -S -B iife -T none`, { stdio: "inherit" });

    const compiled = readFileSync(output, "utf8");
//    writeFileSync(output, `${authorLine}\n${versionLine}\n${descriptionLine}\n${compiled}`);
    writeFileSync(output, `${authorLine}\n${descriptionLine}\n${compiled}`);

    console.log(`Built: assets/scripts/${packageName}.js`);
}

const args = process.argv.slice(2);

if (args.includes("--all") || args.length === 0) {
    cleanOutputDir();

    const files = readdirSync(SRC_DIR).filter(f => f.endsWith(".js"));
    if (files.length === 0) {
        console.warn("No .js files found.");
    }
    files.forEach(f => compile(f.replace(/\.js$/, "")));
} else {
    args.forEach(compile);
}