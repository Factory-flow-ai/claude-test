package com.momenttrack.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.momenttrack.scanner.data.ScanRecord
import com.momenttrack.scanner.data.ScanRepository
import com.momenttrack.scanner.data.SettingsRepository
import com.momenttrack.scanner.databinding.ActivityMainBinding
import com.momenttrack.scanner.service.ScannerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanRepository: ScanRepository
    private lateinit var settingsRepository: SettingsRepository

    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    
    // Debounce tracking
    private val lastScanTimes = mutableMapOf<String, Long>()
    private var debounceMs: Long = 30_000L // Default 30 seconds
    
    // Current location context
    private var currentLocation: String? = null
    
    // Stats
    private var todayCount = 0
    private var pendingCount = 0

    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanRepository = ScanRepository(this)
        settingsRepository = SettingsRepository(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        loadSettings()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Update stats periodically
        lifecycleScope.launch {
            scanRepository.getTodayScans().collect { scans ->
                todayCount = scans.size
                updateStats()
            }
        }

        lifecycleScope.launch {
            scanRepository.getPendingScans().collect { scans ->
                pendingCount = scans.size
                updateStats()
            }
        }

        // Recent scans list
        lifecycleScope.launch {
            scanRepository.getRecentScans(50).collect { scans ->
                updateScanLog(scans)
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                debounceMs = settings.debounceSeconds * 1000L
                binding.tvDebounce.text = "${settings.debounceSeconds}s"
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startCamera()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Image analysis for barcode scanning
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcodes ->
                        processBarcodes(barcodes)
                    })
                }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                
                // Update status
                binding.statusDot.setBackgroundResource(R.drawable.status_dot_active)
                binding.tvLocation.text = "Scanning..."
                
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processBarcodes(barcodes: List<Barcode>) {
        val now = System.currentTimeMillis()

        for (barcode in barcodes) {
            val value = barcode.rawValue ?: continue
            
            // Check debounce
            val lastScan = lastScanTimes[value]
            if (lastScan != null && (now - lastScan) < debounceMs) {
                continue // Skip - within debounce window
            }
            
            lastScanTimes[value] = now

            // Check if this is a location code
            if (isLocationCode(value)) {
                updateLocation(value)
                continue
            }

            // Process as a regular scan
            val scan = ScanRecord(
                code = value,
                format = barcode.format.toString(),
                timestamp = now,
                deviceId = getAppDeviceId(),
                locationId = currentLocation,
                latitude = null, // TODO: Add GPS
                longitude = null,
                synced = false
            )

            // Save and provide feedback
            lifecycleScope.launch {
                scanRepository.insert(scan)
                runOnUiThread {
                    flashDetection()
                    playSound()
                    vibrate()
                }
            }
        }
    }

    private fun isLocationCode(code: String): Boolean {
        return code.startsWith("LOC:") || 
               code.startsWith("STATION:") || 
               code.startsWith("MT-LOC:")
    }

    private fun updateLocation(code: String) {
        val locationId = code
            .removePrefix("LOC:")
            .removePrefix("STATION:")
            .removePrefix("MT-LOC:")
        
        currentLocation = locationId
        runOnUiThread {
            binding.tvLocation.text = locationId
            binding.locationBadge.visibility = View.VISIBLE
        }

        // TODO: Fetch location config from API to update debounce
    }

    private fun flashDetection() {
        binding.detectionFlash.alpha = 0.4f
        binding.detectionFlash.animate()
            .alpha(0f)
            .setDuration(150)
            .start()
    }

    private fun playSound() {
        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.soundEnabled) {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to play sound", e)
                }
            }
        }
    }

    private fun vibrate() {
        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.vibrateEnabled) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    manager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        }
    }

    private fun updateStats() {
        binding.tvTodayCount.text = todayCount.toString()
        binding.tvPendingCount.text = pendingCount.toString()
    }

    private fun updateScanLog(scans: List<ScanRecord>) {
        if (scans.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.scanLogContainer.visibility = View.GONE
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.scanLogContainer.visibility = View.VISIBLE
        
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        
        for (scan in scans.take(10)) {
            val time = dateFormat.format(Date(scan.timestamp))
            val status = if (scan.synced) "✓" else "⏳"
            sb.append("$status $time  ${scan.code.take(30)}\n")
        }
        
        binding.tvScanLog.text = sb.toString()
        binding.tvScanCount.text = "${scans.size} scans"
    }

    private fun getAppDeviceId(): String {
        val prefs = getSharedPreferences("momenttrack", MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = "MT-" + UUID.randomUUID().toString().take(8).uppercase()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MomentTrackScanner"
    }

    // Inner class for barcode analysis
    private class BarcodeAnalyzer(
        private val onBarcodesDetected: (List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage, 
                    imageProxy.imageInfo.rotationDegrees
                )
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            onBarcodesDetected(barcodes)
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
