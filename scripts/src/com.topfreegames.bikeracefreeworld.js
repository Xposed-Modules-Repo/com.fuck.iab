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