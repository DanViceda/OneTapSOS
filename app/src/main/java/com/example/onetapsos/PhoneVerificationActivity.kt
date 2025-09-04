package com.example.onetapsos

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

class PhoneVerificationActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var loadingSpinner: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accverify_phonenum)

        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
        loadingSpinner = findViewById(R.id.loadingSpinner)
        loadingSpinner.visibility = View.GONE

        findViewById<ImageButton>(R.id.backButton)?.setOnClickListener {
            startActivity(Intent(this, AccountVerificationActivity::class.java).apply {
                putExtra("USER_EMAIL", userEmail)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }

        findViewById<TextView>(R.id.text_use_email).setOnClickListener {
            startActivity(Intent(this, EmailVerificationActivity::class.java).apply {
                putExtra("USER_EMAIL", userEmail)
            })
            finish()
        }

        findViewById<TextView>(R.id.text_change_number).setOnClickListener {
            Toast.makeText(this, "Change number functionality not implemented", Toast.LENGTH_SHORT).show()
        }

        val codeInput = findViewById<EditText>(R.id.edit_phone_code)
        findViewById<Button>(R.id.submitCodeBtn).setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isNotEmpty()) {
                verifyOTP(userEmail, code)
            } else {
                Toast.makeText(this, "Please enter the OTP code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verifyOTP(email: String, code: String) {
        loadingSpinner.visibility = View.VISIBLE

        val json = JSONObject().apply {
            put("email", email)
            put("otp_code", code)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/verify-otp/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@PhoneVerificationActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    if (response.isSuccessful) {
                        val sharedPrefs = getSharedPreferences("OneTapSOS", MODE_PRIVATE)
                        with(sharedPrefs.edit()) {
                            putBoolean("isLoggedIn", true)
                            apply()
                        }
                        Toast.makeText(this@PhoneVerificationActivity, "Phone verified successfully", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@PhoneVerificationActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        try {
                            val errorJson = JSONObject(responseBody ?: "")
                            val errorMessage = errorJson.optString("error", "Verification failed")
                            Toast.makeText(this@PhoneVerificationActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@PhoneVerificationActivity, "Verification failed: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }
}