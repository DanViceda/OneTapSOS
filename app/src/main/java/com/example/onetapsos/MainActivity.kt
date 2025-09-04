package com.example.onetapsos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var messageInput: EditText
    private lateinit var gpsStatusText: TextView
    private lateinit var accountStatusText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var recordButton: ImageButton
    private lateinit var recordTimer: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Audio recording
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var outputFile: String

    // Timer
    private val timerHandler = Handler()
    private var timerRunnable: Runnable? = null
    private var secondsElapsed = 0

    // Speech-to-Text
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isListening = false
    private var transcriptBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val red = ContextCompat.getColor(this, R.color.red)
        window.statusBarColor = red
        window.navigationBarColor = red
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false


        initViews()
        initSpeechToText()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateGPSStatus()
        getLastKnownLocation()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        if (isRecording) stopRecording()
        stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun initViews() {
        val hamburgerButton = findViewById<ImageButton>(R.id.buttonIcon)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)

        messageInput = findViewById(R.id.messageInput)
        gpsStatusText = findViewById(R.id.gpsStatus)
        accountStatusText = findViewById(R.id.accountStatusText)
        networkStatusText = findViewById(R.id.networkStatusText)
        recordButton = findViewById(R.id.recordButton)
        recordTimer = findViewById(R.id.recordTimer)

        recordTimer.visibility = View.GONE

        val sharedPrefs = getSharedPreferences("OneTapSOS", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false)

        // Prefer intent extra after login, fallback to saved session
        val userFullName = intent.getStringExtra("FULL_NAME")
            ?: sharedPrefs.getString("userFullName", "Guest")

        accountStatusText.text = if (isLoggedIn) {
            "Account Status: $userFullName"
        } else {
            "Account Status: Guest"
        }
        networkStatusText.text = "Network Status: Connected"


        outputFile = "${externalCacheDir?.absolutePath}/recorded_audio.3gp"

        recordButton.setOnClickListener {
            if (!isRecording) {
                if (checkMicrophonePermission()) {
                    transcriptBuilder.clear()
                    startRecording()
                    startListening()
                    recordButton.setImageResource(R.drawable.ic_play)
                    isRecording = true
                } else {
                    requestMicrophonePermission()
                }
            } else {
                stopRecording()
                stopListening()
                recordButton.setImageResource(R.drawable.ic_record)
                if (transcriptBuilder.isNotBlank()) {
                    val existing = messageInput.text?.toString()?.trim().orEmpty()
                    val combined = listOf(existing, transcriptBuilder.toString().trim())
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    messageInput.setText(combined)
                }
                showRecordOptionsDialog()
                isRecording = false
            }
        }

        sendButton.setOnClickListener { sendSOSMessage() }
        hamburgerButton.setOnClickListener { handleHamburgerClick() }
    }

    private fun initSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device.", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.w("STT", "Speech error: $error")
                if (isRecording) restartListening()
            }
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = data?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    if (transcriptBuilder.isNotEmpty()) transcriptBuilder.append(' ')
                    transcriptBuilder.append(text)
                }
                if (isRecording) restartListening()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = data?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
    }

    private fun startListening() {
        if (speechRecognizer == null || speechIntent == null) return
        if (isListening) return
        try {
            speechRecognizer?.startListening(speechIntent)
            isListening = true
            Toast.makeText(this, "Listening…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("STT", "Failed to start listening", e)
            isListening = false
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
    }

    private fun restartListening() {
        stopListening()
        recordTimer.postDelayed({ startListening() }, 150)
    }

    private fun handleHamburgerClick() {
        val sharedPrefs = getSharedPreferences("OneTapSOS", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false)
        val target = if (isLoggedIn) ProfileActivity::class.java else GuestActivity::class.java
        startActivity(Intent(this, target))
    }

    private fun startRecording() {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(outputFile)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                prepare()
                start()
            }

            recordTimer.visibility = View.VISIBLE
            startTimer()

            Toast.makeText(this, "Recording started…", Toast.LENGTH_SHORT).show()
            isRecording = true
        } catch (e: Exception) {
            isRecording = false
            recordButton.clearAnimation()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("Recorder", "startRecording error", e)
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Toast.makeText(this, "Recording saved: $outputFile", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Stop failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("Recorder", "stopRecording error", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            recordButton.clearAnimation()
            stopTimer()
        }
    }

    private fun startTimer() {
        secondsElapsed = 0
        timerRunnable = object : Runnable {
            override fun run() {
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                recordTimer.text = String.format("%02d:%02d", minutes, seconds)
                secondsElapsed++
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        recordTimer.text = "00:00"
        recordTimer.visibility = View.GONE
    }

    private fun showRecordOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_layout_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val sendBtn = dialogView.findViewById<Button>(R.id.sendBtn)
        val discardBtn = dialogView.findViewById<Button>(R.id.discardBtn)
        val transcriptText = dialogView.findViewById<TextView>(R.id.transcriptText)
        val countdownText = dialogView.findViewById<TextView>(R.id.countdownText)

        transcriptText.text = transcriptBuilder.toString().trim().ifEmpty { "No transcription available. Please type a message." }

        val countdownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "Sending in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                sendSOSMessage()
                dialog.dismiss()
            }
        }.start()

        sendBtn.setOnClickListener {
            countdownTimer.cancel()
            sendSOSMessage()
            dialog.dismiss()
        }

        discardBtn.setOnClickListener {
            countdownTimer.cancel()
            val recordedFile = File(outputFile)
            if (recordedFile.exists()) recordedFile.delete()
            Toast.makeText(this, "Recording discarded", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendSOSMessage() {
        val typed = messageInput.text?.toString()?.trim().orEmpty()
        val stt = transcriptBuilder.toString().trim()
        val combinedMessage = listOf(typed, stt)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

        val locationText = gpsStatusText.text.toString().substringAfter("Location:", "").trim()

        if (combinedMessage.isNotEmpty() && locationText.isNotEmpty()) {
            val structuredMessage = """
                OneTapSOS Alert

                $combinedMessage

                Location: $locationText
            """.trimIndent()

            if (hasSmsPermission()) {
                sendSMS("09766213164", structuredMessage)
            } else {
                Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 1001)
            }
        } else {
            Toast.makeText(this, "Missing message or location info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLastKnownLocation() {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        updateLocationUI(location.latitude, location.longitude)
                    } else {
                        gpsStatusText.text = getString(R.string.fetching_location)
                    }
                }
            } catch (se: SecurityException) {
                Log.e("Location", "Permission denied", se)
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocationUI(location.latitude, location.longitude)
                }
            }
        }

        if (hasLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    mainLooper
                )
            } catch (se: SecurityException) {
                Log.e("Location", "Permission denied", se)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationUI(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val list: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)

            if (!list.isNullOrEmpty()) {
                val a = list[0]
                val fullAddress = listOfNotNull(
                    a.thoroughfare, a.subLocality, a.locality, a.adminArea, a.countryName
                ).joinToString(", ")

                val gpsStatus = updateGPSStatus()
                gpsStatusText.text = "$gpsStatus\nLocation: $fullAddress"
            } else {
                gpsStatusText.text = getString(R.string.unable_to_find_address)
            }
        } catch (e: Exception) {
            gpsStatusText.text = "Location: lat=$latitude, lon=$longitude"
            Log.e("Geocoder", "Reverse geocoding failed", e)
        }
    }

    private fun updateGPSStatus(): String {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        return if (isGpsEnabled) {
            gpsStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
            getString(R.string.gps_status_enabled)
        } else {
            gpsStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
            getString(R.string.gps_status_disabled)
        }
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        if (!hasSmsPermission()) {
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "SMS sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("SMS", "Error sending SMS", e)
        }
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (!checkMicrophonePermission()) needed += Manifest.permission.RECORD_AUDIO
        if (!hasLocationPermission()) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (!hasSmsPermission()) needed += Manifest.permission.SEND_SMS
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }
    }

    private fun checkMicrophonePermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                getLastKnownLocation()
                startLocationUpdates()
            } else {
                handlePermissionDenied(permissions)
            }
        }
    }

    private fun handlePermissionDenied(permissions: Array<out String>) {
        val shouldShowRationale = permissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }
        if (!shouldShowRationale) {
            showPermissionDeniedDialog()
        } else {
            Toast.makeText(this, "Permissions are required to use this app.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To fully use OneTapSOS, please enable Microphone, Location, and SMS in your device settings.")
            .setPositiveButton("Go to Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }
}