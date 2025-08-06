package com.example.onetapsos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.telephony.SmsManager
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.util.*

class MainActivity : AppCompatActivity() {
    // UI elements
    private lateinit var messageInput: EditText
    private lateinit var gpsStatusText: TextView
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    // Location service
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var capturedText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements from layout
        val recordButton = findViewById<ImageButton>(R.id.imageButton)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        messageInput = findViewById(R.id.messageInput)
        gpsStatusText = findViewById(R.id.gpsStatus)

        // Set up location provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check and request required permissions
        checkAndRequestPermissions()

        // Initialize voice-to-text launcher
        speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val results = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                capturedText = results?.get(0) ?: ""
                messageInput.setText(capturedText) // Populate textbox with result
                Log.d("SpeechToText", "Captured text: $capturedText")
            } else {
                Toast.makeText(this, "Speech recognition failed", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle mic button click
        recordButton.setOnClickListener {
            if (checkMicrophonePermission()) {
                startVoiceRecognition()
            } else {
                requestMicrophonePermission()
            }
        }

        // Handle send button click
        sendButton.setOnClickListener {
            val userMessage = messageInput.text.toString()
            val locationText = gpsStatusText.text.toString().substringAfter("Location:").trim()

            // Check if message and location are not empty
            if (userMessage.isNotEmpty() && locationText.isNotEmpty()) {
                // Structured message format
                val structuredMessage = """
                    Hello this is from OneTapSOS

                    $userMessage

                    $locationText

                    From
                    [Not Available]

                    Sent via OneTapSOS
                """.trimIndent()

                // ⚠️ TEST NUMBER: Replace with your own number for testing.
                // Do not commit real numbers in production for privacy.
                sendSMS("09766213164", structuredMessage)
            } else {
                Toast.makeText(this, "Missing message or location info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateGPSStatus() // Update GPS label when app resumes
        startLocationUpdates() // Start real-time location updates
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback) // Stop tracking when paused
    }

    // Check and request permissions
    private fun checkAndRequestPermissions() {
        val neededPermissions = mutableListOf<String>()

        if (!checkMicrophonePermission()) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // Check location permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            neededPermissions.addAll(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        // Check SMS permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            neededPermissions.add(Manifest.permission.SEND_SMS)
        }

        // Request all missing permissions
        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 1001)
        } else {
            startLocationUpdates()
        }
    }

    // Check microphone permission
    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    // Start voice recognition (speech-to-text)
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60000)
        }
        speechLauncher.launch(intent)
    }

    // Set up location tracking
    @SuppressLint("SetTextI18n")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        locationCallback = object : LocationCallback() {
            @SuppressLint("SetTextI18n")
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val latitude = location.latitude
                    val longitude = location.longitude

                    // Convert coordinates to address
                    val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                    val addressList: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addressList.isNullOrEmpty()) {
                        val address = addressList[0]

                        val street = address.thoroughfare ?: ""
                        val barangay = address.subLocality ?: ""
                        val city = address.locality ?: ""
                        val province = address.adminArea ?: ""
                        val country = address.countryName ?: ""

                        val gpsStatus = updateGPSStatus()
                        val fullAddress = "$street, $barangay, $city, $province, $country"

                        // Display real-time address + GPS status
                        gpsStatusText.text = "$gpsStatus\nLocation: $fullAddress"
                        Log.d("GeoLocation", "Full Address: $fullAddress")
                    } else {
                        gpsStatusText.text = "Unable to find address"
                    }
                }
            }
        }

        // Check permission before requesting updates
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } else {
            gpsStatusText.text = "Location permission denied"
            gpsStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
        }
    }

    // Update GPS status label
    private fun updateGPSStatus(): String {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        return if (isGpsEnabled) {
            gpsStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
            "GPS Status: Enabled"
        } else {
            gpsStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
            "GPS Status: Disabled"
        }
    }

    // Send SMS
    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            // ⚠️ Reminder: Replace with your own number for testing.
            // Do not commit real numbers in production for privacy.
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "SMS sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Handle permission request results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationUpdates()
            } else {
                val shouldShowRationale = permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (!shouldShowRationale) {
                    showPermissionDeniedDialog()
                } else {
                    Toast.makeText(
                        this,
                        "Permissions are required to use this app.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Show dialog when permissions are permanently denied
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To fully use OneTapSOS, please enable Microphone and Location in your device settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Redirect to app settings
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}