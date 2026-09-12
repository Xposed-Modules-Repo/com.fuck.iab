# FuckIAB

An Xposed module that basically fucks in app billing.

## Showcase

https://github.com/user-attachments/assets/baa25c2c-baaf-4e84-86e8-51e9f4545a4a

https://github.com/user-attachments/assets/3a6a2acc-4305-4006-9600-d5b8c7599663

https://github.com/user-attachments/assets/df3f6d44-da88-4576-8128-d944d7041942

## Compatibility
- [x] Google Play Store
- [x] Bazaar (Iranian App Market)
- [x] Myket (Iranian App Market)
- [ ] Samsung
- [ ] Amazon
- [ ] Huawei

## Features

- Support for restoring previously purchased items.
- Custom Frida Scripts.

## Custom Frida Scripts

FuckIAB supports custom Frida scripts.

A global script is loaded for all applications, followed by an app-specific script when available.

```
scripts/
├── global.js
├── com.kiloo.subwaysurf.js
└── com.example.game.js
```

`global.js` runs for all apps. If an app has its own script, it runs after the global script.

### Custom Script Examples

- Subway Surfers (`com.kiloo.subwaysurf`) - Infinite coins (just for fun, added as an example)

<img width="270" height="362" alt="ss0" src="https://github.com/user-attachments/assets/8b527efd-b4d9-47e8-a213-e09ef9f937c1" />

### Contributing Custom Scripts

Want to add support for another game or app? Feel free to open a pull request with a custom script.

Please include:
- The package name of the app/game
- The custom script
- A brief description of what it does

## Disclaimer

This project is for educational and research purposes only. Please use it responsibly and only with proper authorization. The author is not responsible for any misuse or damage caused by the use of this project.

---

**Feel free to open an issue if you have any problems or suggestions.**
