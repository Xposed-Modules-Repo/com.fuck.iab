// Publishes scripts/dist to the branch the manager downloads scripts from, so a new
// script goes live without building and releasing a new apk.
//
// Usage:
//   npm run build:dist     # compile every per-app script and write dist/manifest.json
//   npm run publish        # push dist/ to the scripts-dist branch
//
// SCRIPTS_REMOTE / SCRIPTS_BRANCH override the remote and branch.
import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DIST_DIR = path.join(__dirname, "dist");
const MANIFEST = path.join(DIST_DIR, "manifest.json");

const remote = process.env.SCRIPTS_REMOTE || "origin";
const branch = process.env.SCRIPTS_BRANCH || "scripts-dist";

if (!existsSync(MANIFEST)) {
    console.error("No dist build found. Run `npm run build:dist` first.");
    process.exit(1);
}

const manifest = JSON.parse(readFileSync(MANIFEST, "utf8"));

if (!Array.isArray(manifest.scripts) || manifest.scripts.length === 0) {
    console.error("dist/manifest.json lists no scripts, refusing to publish.");
    process.exit(1);
}

function git(args, cwd) {
    return execFileSync("git", args, { cwd, stdio: "inherit" });
}

function gitOutput(args, cwd) {
    return execFileSync("git", args, { cwd, encoding: "utf8" }).trim();
}

const url = gitOutput(["remote", "get-url", remote], path.join(__dirname, ".."));

if (url.length === 0) {
    console.error(`Cannot resolve remote "${remote}".`);
    process.exit(1);
}

const workDir = mkdtempSync(path.join(tmpdir(), "fkiab-scripts-"));

try {
    cpSync(DIST_DIR, workDir, { recursive: true });

    git(["init", "-q", `--initial-branch=${branch}`], workDir);
    git(["add", "-A"], workDir);
    git(
        ["-c", "user.name=fkiab", "-c", "user.email=fkiab@users.noreply.github.com",
            "commit", "-qm", `scripts: publish ${manifest.generatedAt}`],
        workDir
    );
    git(["push", "-f", url, `HEAD:refs/heads/${branch}`], workDir);
} finally {
    rmSync(workDir, { recursive: true, force: true });
}

const web = url.replace(/\.git$/, "").replace(/^git@([^:]+):/, "https://$1/");
const raw = web.match(/^https:\/\/github\.com\/([^/]+\/[^/]+)$/);

console.log(`Published ${manifest.scripts.length} script(s) to ${remote}/${branch}`);
console.log(`Manifest: ${raw ? `https://raw.githubusercontent.com/${raw[1]}/${branch}/manifest.json` : `${web}/raw/${branch}/manifest.json`}`);

// Publishing is also run by .github/workflows/publish-scripts.yml.
