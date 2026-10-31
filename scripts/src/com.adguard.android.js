// author: Fexture69
// Hooks are installed straight away: the module runs this before AdGuard's onCreate, so the
// license checkers are already replaced when AdGuard starts its firewall threads. Installing
// them later raced those threads and crashed the app in ART's static invoke stub.
Java.perform(function () {
    console.log("[*] Starting Ultimate Logic Bypass...");
    try {
        // 1. Hook the ultimate premium checker class
        var LicenseChecker = Java.use("J0.a");
        var methods = LicenseChecker.class.getDeclaredMethods();
        var hookCount = 0;

        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            var params = m.getParameterTypes();
            var ret = m.getReturnType();

            // Hook any method that returns a boolean and takes 1 argument (the license state)
            if (ret.getName() === "boolean" && params.length === 1) {
                try {
                    LicenseChecker[m.getName()].overload(params[0].getName()).implementation = function (state) {
                        console.log("[+] Forced J0.a." + m.getName() + "() -> true");
                        return true;
                    };
                    hookCount++;
                } catch (e) {
                    // Ignore if overload fails
                }
            }
        }
        console.log("[+] Hooked " + hookCount + " license checker methods.");

        // 2. Stop the app from overwriting the state (just in case)
        try {
            var PlusManager = Java.use("H0.b");
            PlusManager.rb.overload('K0.i').implementation = function (state) {
                console.log("[*] Blocked network state update (rb)");
            };
        } catch (e) {}

        console.log("[+] Injection complete! Force Stop AdGuard and reopen it.");
    } catch (e) {
        console.log("[-] Fatal Error: " + e.message);
    }
});
