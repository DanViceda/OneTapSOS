package com.example.onetapsos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AccountVerificationActivity : AppCompatActivity() {

    private lateinit var verifyEmailBtn: Button
    private lateinit var verifyMobileBtn: Button
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var backButton: ImageButton
    private val client = OkHttpClient()

    private var userEmail: String = ""
    private var userPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_verification)

        verifyEmailBtn = findViewById(R.id.verifyEmailBtn)
        verifyMobileBtn = findViewById(R.id.verifyMobileBtn)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        backButton = findViewById(R.id.backButton)

        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        loadingSpinner.visibility = View.GONE

        val prefs = getSharedPreferences("OneTapSOS", MODE_PRIVATE)
        userEmail = prefs.getString("userEmail", "") ?: ""
        userPhone = prefs.getString("phone", "") ?: ""

        backButton.setOnClickListener { finish() }

        verifyEmailBtn.setOnClickListener {
            if (userEmail.isNotEmpty()) {
                sendEmailOTP(userEmail)
            } else {
                Toast.makeText(this, "No email available", Toast.LENGTH_SHORT).show()
            }
        }

        verifyMobileBtn.setOnClickListener {
            if (userPhone.isNotEmpty()) {
                sendPhoneOTP(userPhone)
            } else {
                Toast.makeText(this, "No phone available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendEmailOTP(email: String) {
        loadingSpinner.visibility = View.VISIBLE
        val json = JSONObject().apply { put("email", email) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/send-email-otp/")
            .post(body)
            .build()

        client.newCall(request).enqueue(makeCallback {
            Toast.makeText(this, "OTP sent to $email", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, EmailVerificationActivity::class.java)
            intent.putExtra("USER_EMAIL", email)
            startActivity(intent)
            finish()
        })
    }

    private fun sendPhoneOTP(phone: String) {
        loadingSpinner.visibility = View.VISIBLE
        val json = JSONObject().apply { put("phone", phone) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/send-phone-otp/")
            .post(body)
            .build()

        client.newCall(request).enqueue(makeCallback {
            Toast.makeText(this, "OTP sent to $phone", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, PhoneVerificationActivity::class.java)
            intent.putExtra("USER_PHONE", phone)
            startActivity(intent)
            finish()
        })
    }

    private fun makeCallback(onSuccess: () -> Unit): Callback {
        return object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@AccountVerificationActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        Toast.makeText(this@AccountVerificationActivity, "Failed to send OTP", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
