package com.example.onetapsos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class RegisterActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val client = OkHttpClient()
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var passwordError: TextView

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
            account?.let {
                Toast.makeText(this, "Signed in: ${it.email}", Toast.LENGTH_LONG).show()
                Log.d("RegisterActivity", "Google account: ${it.email}")
                sendGoogleRegistrationToBackend(it.displayName ?: "", it.email ?: "")
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("RegisterActivity", "Google Sign-In error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        loadingSpinner = findViewById(R.id.loadingSpinner)
        loadingSpinner.visibility = View.GONE
        passwordError = findViewById(R.id.passwordError)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            startActivity(Intent(this, GuestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
        )

        findViewById<Button>(R.id.googleRegisterBtn).setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val confirmPasswordInput = findViewById<EditText>(R.id.confirmPasswordInput)
        setupPasswordToggle(passwordInput)
        setupPasswordToggle(confirmPasswordInput)

        findViewById<Button>(R.id.registerBtn).setOnClickListener {
            val fullName = findViewById<EditText>(R.id.fullNameInput).text.toString().trim()
            val email = findViewById<EditText>(R.id.emailInput).text.toString().trim()
            val phone = findViewById<EditText>(R.id.phoneInput).text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (validateManualRegistration(fullName, email, phone, password, confirmPassword)) {
                sendRegistrationToBackend(fullName, email, phone, password)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPasswordToggle(editText: EditText) {
        var isPasswordVisible = false
        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = 2
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

    private fun showSuccessDialog(email: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sucess, null)
        val countdownText = dialogView.findViewById<TextView>(R.id.countdownText)
        val proceedButton = dialogView.findViewById<ImageButton>(R.id.proceedButtons)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "${millisUntilFinished / 1000} Seconds"
            }

            override fun onFinish() {
                dialog.dismiss()
                navigateToVerification(email)
            }
        }.start()

        proceedButton.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
            navigateToVerification(email)
        }

        dialog.show()
    }

    private fun sendRegistrationToBackend(fullName: String, email: String, phone: String, password: String) {
        loadingSpinner.visibility = View.VISIBLE

        val normalizedPhone = if (phone.startsWith("09") && phone.length == 11) "+63${phone.substring(1)}" else phone

        val json = JSONObject().apply {
            put("full_name", fullName)
            put("email", email)
            put("phone", normalizedPhone)
            put("password", password)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/register/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("RegisterActivity", "Network error", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Log.d("RegisterActivity", "Response: $responseBody")

                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "")
                            val email = jsonResponse.getString("email")
                            val fullName = jsonResponse.getString("full_name")
                            val phone = jsonResponse.optString("phone", "")

                            // Save user locally
                            val sharedPrefs = getSharedPreferences("OneTapSOS", Context.MODE_PRIVATE)
                            with(sharedPrefs.edit()) {
                                putString("userFullName", fullName)
                                putString("userEmail", email)
                                putString("phone", phone)
                                apply()
                            }

                            Log.d("RegisterActivity", "Saved to SharedPrefs: fullName=$fullName, email=$email, phone=$phone")

                            val message = jsonResponse.optString("message", "Registration successful, please verify your account")
                            Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_SHORT).show()
                            showSuccessDialog(email)
                        } catch (e: Exception) {
                            Toast.makeText(this@RegisterActivity, "Error parsing response: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("RegisterActivity", "JSON parse error", e)
                        }
                    } else {
                        try {
                            val errorJson = JSONObject(responseBody ?: "")
                            val errorMessage = errorJson.optString("error", responseBody ?: "Registration failed: ${response.code}")
                            Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                            Log.e("RegisterActivity", "Error JSON: $errorJson")
                        } catch (e: Exception) {
                            Toast.makeText(this@RegisterActivity, "Registration failed: ${response.code}", Toast.LENGTH_LONG).show()
                            Log.e("RegisterActivity", "Response error", e)
                        }
                    }
                }
            }
        })
    }

    private fun sendGoogleRegistrationToBackend(fullName: String, email: String) {
        loadingSpinner.visibility = View.VISIBLE

        val json = JSONObject().apply {
            put("full_name", fullName)
            put("email", email)
            put("phone", "")
            put("password", UUID.randomUUID().toString())
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${Constants.BASE_URL}/api/users/register/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("RegisterActivity", "Network error", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    Log.d("RegisterActivity", "Google Response: $responseBody")

                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "")
                            val email = jsonResponse.getString("email")
                            val fullName = jsonResponse.getString("full_name")
                            val phone = jsonResponse.optString("phone", "")

                            val sharedPrefs = getSharedPreferences("OneTapSOS", Context.MODE_PRIVATE)
                            with(sharedPrefs.edit()) {
                                putString("userFullName", fullName)
                                putString("userEmail", email)
                                putString("phone", phone)
                                apply()
                            }

                            val message = jsonResponse.optString("message", "Registration successful via Google, please verify your account")
                            Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_SHORT).show()
                            showSuccessDialog(email)

                        } catch (e: Exception) {
                            Toast.makeText(this@RegisterActivity, "Error parsing response: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("RegisterActivity", "JSON parse error", e)
                        }
                    } else {
                        val errorMessage = "Google registration failed: ${response.code}"
                        Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                        Log.e("RegisterActivity", "Error: $responseBody")
                    }
                }
            }
        })
    }

    private fun navigateToVerification(email: String) {
        val intent = Intent(this, AccountVerificationActivity::class.java)
        intent.putExtra("USER_EMAIL", email)
        startActivity(intent)
        finish()
    }

    private fun validateManualRegistration(
        fullName: String, email: String, phone: String,
        password: String, confirmPassword: String
    ): Boolean {
        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirmPassword) {
            passwordError.visibility = View.VISIBLE
            passwordError.text = "Passwords do not match"
            return false
        }

        passwordError.visibility = View.GONE
        return true
    }
}
