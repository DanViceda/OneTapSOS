package com.example.onetapsos

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
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

class EmailVerificationActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var otpInput: EditText
    private lateinit var submitBtn: Button
    private lateinit var resendBtn: TextView
    private var userEmail: String = ""
    private var resendTimer: CountDownTimer? = null
    private val RESEND_COOLDOWN = 60000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accverify_email)

        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        otpInput = findViewById(R.id.edit_code_input)
        submitBtn = findViewById(R.id.submitCodeBtn)
        resendBtn = findViewById(R.id.text_use_phone)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        loadingSpinner.visibility = View.GONE

        userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
        Log.d("EmailVerification", "User email: $userEmail")

        submitBtn.setOnClickListener {
            val otp = otpInput.text.toString().trim()
            if (otp.length == 6) {
                verifyOTP(userEmail, otp)
            } else {
                Toast.makeText(this, "Enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
            }
        }

        resendBtn.setOnClickListener {
            if (resendBtn.isEnabled) {
                sendOTP(userEmail)
                startResendCooldown()
            }
        }
    }

    private fun startResendCooldown() {
        resendBtn.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(RESEND_COOLDOWN, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                resendBtn.text = "Resend OTP (${millisUntilFinished / 1000}s)"
            }
            override fun onFinish() {
                resendBtn.text = "Resend OTP"
                resendBtn.isEnabled = true
            }
        }.start()
    }

    private fun sendOTP(email: String) {
        loadingSpinner.visibility = View.VISIBLE
        val json = JSONObject().apply { put("email", email) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/send-otp/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@EmailVerificationActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@EmailVerificationActivity, "OTP sent", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@EmailVerificationActivity, "Failed to send OTP", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun verifyOTP(email: String, otp: String) {
        loadingSpinner.visibility = View.VISIBLE
        val json = JSONObject().apply {
            put("email", email)
            put("verification_code", otp)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/verify-email/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@EmailVerificationActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@EmailVerificationActivity, "Email verified!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@EmailVerificationActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@EmailVerificationActivity, "Invalid OTP", Toast.LENGTH_LONG).show()
                        Log.e("EmailVerification", "Response: $responseBody")
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}
