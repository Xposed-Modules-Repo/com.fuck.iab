## Setup
```
npm install
```

## Build
```sh
# every per-app script, into the store the manager downloads from
npm run build:dist

# one script, for trying it out
npm run build -- com.kiloo.subwaysurf
```

`npm run build:all` compiles only the global script, which is the one packaged into the
apk. The per-app scripts live in `dist/`, which is committed: the manager downloads them
with **Update scripts**, so they are not shipped in the apk as well.

## Run
```
frida -U -f com.kiloo.subwaysurf -l dist/scripts/com.kiloo.subwaysurf.js
```
