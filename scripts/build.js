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

    // find headers anywhere in the top comment block so `// name:`/`// url:` can come first
    const srcLines = readFileSync(input, "utf8").split("\n").slice(0, 10);
    const findLine = (key) => srcLines.find(l => l.startsWith(`// ${key}: `));
    const nameLine = findLine("name");
    const urlLine = findLine("url");
    const authorLine = findLine("author") ?? "// author: ";
//    const versionLine = findLine("version");
    const descriptionLine = findLine("description") ?? "// description: ";

    if(packageName == "global") {
        execSync(`npx frida-compile "${input}" -o "${output}" -S -B iife -T none`, { stdio: "inherit" });
    } else {
        execSync(`npx frida-compile "${input}" -o "${output}" -c -S -B iife -T none`, { stdio: "inherit" });
    }

    const compiled = readFileSync(output, "utf8");
    const headerLines = [nameLine, urlLine, authorLine, descriptionLine].filter(Boolean);
    writeFileSync(output, `${headerLines.join("\n")}\n${compiled}`);

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