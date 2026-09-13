## Setup
```
npm install
```

## Build
```
npm run build:all
npm run build -- com.kiloo.subwaysurf
```

## Run
```
frida -U -f com.kiloo.subwaysurf -l ../app/src/main/assets/scripts/com.kiloo.subwaysurf.js
```