package fh

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.fuck.iab.INAPP_DATA_SIGNATURE
import com.fuck.iab.INAPP_PURCHASE_DATA
import com.fuck.iab.Purchase_Successful
import com.fuck.iab.RESPONSE_CODE
import com.fuck.iab.data
import com.fuck.iab.signature

class d : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.getStringExtra(data())
        val signature = intent.getStringExtra(signature())

        val resultIntent = Intent()
        resultIntent.putExtra(RESPONSE_CODE(), 0)
        resultIntent.putExtra(INAPP_PURCHASE_DATA(), data)
        resultIntent.putExtra(INAPP_DATA_SIGNATURE(), signature)

        Toast.makeText(this, Purchase_Successful(), Toast.LENGTH_SHORT).show()

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}