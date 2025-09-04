package com.example.onetapsos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 🔴 Apply red system bars
        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        // 🔹 UI references
        val fullNameText = findViewById<TextView>(R.id.fullNameText)
        val emailText = findViewById<TextView>(R.id.emailText)
        val phoneText = findViewById<TextView>(R.id.phoneText)
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val reportSection = findViewById<LinearLayout>(R.id.reportSection) // ✅ Reports section

        // 🔹 Retrieve user data
        val sharedPrefs = getSharedPreferences("OneTapSOS", Context.MODE_PRIVATE)
        val fullName = sharedPrefs.getString("userFullName", "Unknown User")
        val email = sharedPrefs.getString("userEmail", "Unknown Email")
        val phoneNumber = sharedPrefs.getString("phone", "Unknown Phone")

        // Debug logging
        Log.d("ProfileActivity", "FullName: $fullName, Email: $email, Phone: $phoneNumber")

        // Set text, handle missing phone
        fullNameText.text = fullName
        emailText.text = email
        phoneText.text = if (phoneNumber.isNullOrEmpty() || phoneNumber == "Unknown Phone") {
            "Phone not set"
        } else {
            phoneNumber
        }

        // 🔹 Back button → return to MainActivity
        backButton.setOnClickListener {
            finish() // just go back
        }

        // 🔹 Reports section → go to ReportActivity
        reportSection.setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }

        // 🔹 Logout button
        logoutBtn.setOnClickListener {
            with(sharedPrefs.edit()) {
                clear()
                apply()
            }
            val intent = Intent(this, GuestActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
