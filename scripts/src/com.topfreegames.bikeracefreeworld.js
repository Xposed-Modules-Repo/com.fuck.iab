// name: Bike Race
// url: https://play.google.com/store/apps/details?id=com.topfreegames.bikeracefreeworld
// author: staleroot
// description: Unlocks all paid features in Bike Race: Free World by bypassing receipt verification, making purchases appear valid.

setTimeout(() => {
    Java.perform(() => {
        var ReceiptVerifier = Java.use("com.tfg.libs.billing.google.verifier.ReceiptVerifier");
        ReceiptVerifier["verifyReceipt"].overload('com.tfg.libs.billing.google.PurchaseCompat', 'com.tfg.libs.billing.google.verifier.ReceiptVerifier$ReceiptValidatorListener').implementation = function (purchase, listener) {
            this.addVerifiedPurchase(purchase);
            this.stopTasksForPurchase(purchase);
            this.removeUnverifiedPurchase(purchase);
            listener.onValidReceipt(purchase);
        };
    })
}, 0)