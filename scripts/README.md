## Setup
```
npm install
```

## Build
```
npm run build:all
npm run build -- com.kiloo.subwaysurf
npm run build -- test
```

`build.js` compiles everything in `src/` into `../app/src/main/assets/scripts/` and then
rewrites `meta.json` from the `// version:` headers.

- building one script updates only that entry of `meta.json`
- `--all` rewrites the whole file and drops entries whose script no longer exists

A name resolves to `src/` first and to `global/` second. Scripts in `global/` are never
compiled: the app downloads them as plain source, so only their `meta.json` entry is synced.

## Layout

```
src/       bundled package scripts, compiled into the apk
global/    downloaded on demand, run on every app
meta.json  published versions, pulled by the app on pull to refresh
```

Every script in `src/` carries a `// version:` header, except `global.js`, which is the
bundled runtime and is never served over the network:

```js
// name: Subway Surf
// url: https://play.google.com/store/apps/details?id=com.kiloo.subwaysurf
// author: staleroot
// description: Makes all purchases in Subway Surfers appear valid.
// added-in: 1.4.9
// version: 1
```

`meta.json` mirrors those numbers so the app can tell a newer download from the copy it
already ships:

```json
{
	"global": {
		"test": 3,
		"test2": 1
	},
	"com.kiloo.subwaysurf": 1
}
```

The app fetches `meta.json` from the `master` branch of this repository. A download replaces
the bundled copy only while its version is strictly higher; a tie always uses the bundle.
Bump `// version:` and run the build to ship an update that reaches users before the next
apk release.

Scripts in `global/` are pasted into `global.js` at `REMOTE_SCRIPT_GLOBAL`, each in its own
`try`/`catch`, and carry one extra tag that the scripts screen shows under the name:

```js
// name: test.js
// version: 3
// summary: just a test script
```

## Run
```
frida -U -f com.kiloo.subwaysurf -l ../app/src/main/assets/scripts/com.kiloo.subwaysurf.js
```
