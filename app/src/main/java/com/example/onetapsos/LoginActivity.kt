package com.example.onetapsos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        emailInput = findViewById(R.id.emailInput)
        phoneInput = findViewById(R.id.phoneInput)
        passwordInput = findViewById(R.id.passwordInput)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        loadingSpinner.visibility = View.GONE

        // 👁️ Setup password toggle
        setupPasswordToggle(passwordInput)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
        emailInput.setText(userEmail)

        findViewById<Button>(R.id.loginBtn).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            if (email.isNotEmpty() && phone.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, phone, password)
            } else {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 Copying the toggle logic from RegisterActivity
    private fun setupPasswordToggle(editText: EditText) {
        var isPasswordVisible = false
        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = 2 // right-side drawable
                if (event.rawX >= (editText.right - editText.compoundDrawables[drawableEnd].bounds.width())) {
                    isPasswordVisible = !isPasswordVisible
                    if (isPasswordVisible) {
                        editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_open, 0)
                    } else {
                        editText.transformationMethod = PasswordTransformationMethod.getInstance()
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_closed, 0)
                    }
                    editText.setSelection(editText.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun loginUser(email: String, phone: String, password: String) {
        loadingSpinner.visibility = View.VISIBLE

        val json = JSONObject().apply {
            put("email", email)
            put("phone", phone)
            put("password", password)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/login/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("Login", "Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "{}")
                            val user = jsonResponse.getJSONObject("user")
                            val fullName = user.getString("full_name")
                            val email = user.getString("email")
                            val phone = user.optString("phone", "")

                            val sharedPrefs = getSharedPreferences("OneTapSOS", Context.MODE_PRIVATE)
                            with(sharedPrefs.edit()) {
                                putBoolean("isLoggedIn", true)
                                putString("userFullName", fullName)
                                putString("userEmail", email)
                                putString("phone", phone)
                                apply()
                            }

                            Toast.makeText(this@LoginActivity, "Logged in as $fullName", Toast.LENGTH_SHORT).show()
                            Log.d("Login", "Login successful: $responseBody")

                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.putExtra("FULL_NAME", fullName)
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e("Login", "Parse error: ${e.message}, Response: $responseBody")
                        }
                    } else {
                        try {
                            val errorJson = JSONObject(responseBody ?: "{}")
                            val errorMessage = errorJson.optString("error", "Login failed: ${response.code}")
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                            Log.e("Login", "Error response: $responseBody")
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "Login failed: ${response.code}", Toast.LENGTH_LONG).show()
                            Log.e("Login", "Parse error: ${e.message}, Response: $responseBody")
                        }
                    }
                }
            }
        })
    }
}
