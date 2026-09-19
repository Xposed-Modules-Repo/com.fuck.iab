# FKIAB

An Xposed module for hooking and customizing in-app billing functionality.

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

Scripts live under `scripts/src/` and are named after the target app's package name:

```
scripts/src/
├── global.js
├── com.kiloo.subwaysurf.js
└── com.example.game.js
```

`global.js` is loaded for every app, before anything else. If a matching `<package_name>.js` file also exists, it runs right after the global script.

### Custom Script Examples

- Subway Surfers (`com.kiloo.subwaysurf`) - Infinite coins (just for fun, added as an example)

<img width="270" height="362" alt="ss0" src="https://github.com/user-attachments/assets/8b527efd-b4d9-47e8-a213-e09ef9f937c1" />

### Contributing Custom Scripts

Want to add support for another game or app? Feel free to open a pull request with a custom script.

Scripts should focus on in-app purchase functionality (unlocking premium content, bypassing purchase checks, etc.)

Please include:
- The package name of the app/game
- The custom script
- A brief description of what it does

## Disclaimer

This project is provided for educational and research purposes only. Use it responsibly and only on applications and environments you own or have permission to test. The authors and contributors are not responsible for any misuse, damage, or violations caused by using this project. Please respect developers, software licenses, and platform rules.

---

**Feel free to open an issue if you have any problems or suggestions.**
