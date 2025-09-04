package com.example.onetapsos

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class GuestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest)

        // Make system bars red (fix “purple” bar)
        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        // UI references
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val registerBtn = findViewById<Button>(R.id.registerButton)
        val loginBtn = findViewById<Button>(R.id.LoginButton)

        // Back -> return to MainActivity (bring main to front)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // Register -> open RegisterActivity (do NOT finish so user can back)
        registerBtn.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Login -> open LoginActivity
        loginBtn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
