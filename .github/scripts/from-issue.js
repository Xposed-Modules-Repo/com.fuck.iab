// Turns the "Add a script" issue form into files in the tree: the script itself, its entry in
// scripts/names.json and its package in the module scope. The workflow then builds the store
// and opens a pull request.
//
// Reads ISSUE_BODY, ISSUE_NUMBER and GITHUB_ENV from the environment.
import { appendFileSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, "..", "..");
const SRC_DIR = path.join(ROOT, "scripts", "src");
const NAMES_FILE = path.join(ROOT, "scripts", "names.json");
const SCOPE_FILE = path.join(ROOT, "app", "src", "main", "resources", "META-INF", "xposed", "scope.list");

const PACKAGE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/;

function fail(message) {
    console.error(message);

    if (process.env.GITHUB_ENV) {
        appendFileSync(process.env.GITHUB_ENV, `ERROR=${message.replace(/\n/g, " ")}\n`);
    }

    process.exit(1);
}

/** The issue form renders as "### Label" headings, one block per field. */
function sections(body) {
    const result = {};
    const blocks = body.split(/^### /m).slice(1);

    for (const block of blocks) {
        const newline = block.indexOf("\n");
        const label = (newline === -1 ? block : block.slice(0, newline)).trim();
        result[label] = newline === -1 ? "" : block.slice(newline + 1).trim();
    }

    return result;
}

/** Drops the code fence a rendered textarea adds around its value. */
function unfence(text) {
    const match = text.match(/^```[a-zA-Z]*\n([\s\S]*?)\n```$/);
    return (match ? match[1] : text).trim();
}

const body = process.env.ISSUE_BODY;

if (!body) fail("The issue has no body to read.");

const form = sections(body);
const packageName = (form["Package name"] || "").trim();
const appName = (form["App name"] || "").trim();
const author = (form["Your name or handle"] || "").trim();
const notes = (form["What it does"] || "").trim();
const script = unfence(form["The script"] || "");

if (packageName === "global") {
    fail("The global script is not handled by this form.");
}

if (!PACKAGE_PATTERN.test(packageName)) {
    fail(`\`${packageName || "(empty)"}\` is not a valid package name.`);
}

if (appName.length === 0) {
    fail("The app name is empty.");
}

if (script.length < 10) {
    fail("The script is empty.");
}

if (existsSync(path.join(SRC_DIR, `${packageName}.js`))) {
    fail(`A script for \`${packageName}\` already exists. Send a pull request to change that one.`);
}

// Keep an author line the script brought with it, otherwise credit whoever filled the form.
const firstLine = script.split("\n", 1)[0];
const text = firstLine.startsWith("// author: ") || author.length === 0
    ? script
    : `// author: ${author}\n${script}`;

writeFileSync(path.join(SRC_DIR, `${packageName}.js`), `${text}\n`);

const names = existsSync(NAMES_FILE) ? JSON.parse(readFileSync(NAMES_FILE, "utf8")) : {};
names[packageName] = appName;
writeFileSync(NAMES_FILE, `${JSON.stringify(names, null, 2)}\n`);

// The module only loads into apps it is scoped to, so a new script needs its package there.
const scope = existsSync(SCOPE_FILE) ? readFileSync(SCOPE_FILE, "utf8") : "";
const scoped = scope.split("\n").map(line => line.trim()).filter(Boolean);

if (!scoped.includes(packageName)) {
    writeFileSync(SCOPE_FILE, `${scope.replace(/\s*$/, "")}\n${packageName}\n`);
}

appendFileSync(process.env.GITHUB_ENV, [
    `PACKAGE=${packageName}`,
    `APP_NAME=${appName}`,
    `NOTES=${notes.replace(/\n/g, " ")}`,
    ""
].join("\n"));

console.log(`Wrote scripts/src/${packageName}.js and its name in scripts/names.json`);
