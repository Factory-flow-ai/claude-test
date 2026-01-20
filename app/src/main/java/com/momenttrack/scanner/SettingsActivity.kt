package com.momenttrack.scanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.momenttrack.scanner.data.AppSettings
import com.momenttrack.scanner.data.ScanRepository
import com.momenttrack.scanner.data.SettingsRepository
import com.momenttrack.scanner.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scanRepository: ScanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        scanRepository = ScanRepository(this)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            
            binding.etApiEndpoint.setText(settings.apiEndpoint)
            binding.etDeviceId.setText(settings.deviceId)
            binding.etDebounce.setText(settings.debounceSeconds.toString())
            binding.switchSound.isChecked = settings.soundEnabled
            binding.switchVibrate.isChecked = settings.vibrateEnabled
            binding.switchAutoStart.isChecked = settings.autoStartEnabled
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnClearData.setOnClickListener {
            clearLocalData()
        }

        binding.btnSyncNow.setOnClickListener {
            triggerSync()
        }
    }

    private fun saveSettings() {
        val apiEndpoint = binding.etApiEndpoint.text.toString().trim()
        val deviceId = binding.etDeviceId.text.toString().trim()
        val debounce = binding.etDebounce.text.toString().toIntOrNull() ?: 30

        if (apiEndpoint.isEmpty()) {
            binding.etApiEndpoint.error = "API endpoint required"
            return
        }

        if (deviceId.isEmpty()) {
            binding.etDeviceId.error = "Device ID required"
            return
        }

        val settings = AppSettings(
            apiEndpoint = apiEndpoint,
            deviceId = deviceId,
            debounceSeconds = debounce.coerceIn(1, 3600),
            soundEnabled = binding.switchSound.isChecked,
            vibrateEnabled = binding.switchVibrate.isChecked,
            autoStartEnabled = binding.switchAutoStart.isChecked
        )

        lifecycleScope.launch {
            settingsRepository.updateSettings(settings)
            Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun clearLocalData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear Local Data")
            .setMessage("This will delete all local scan records. Synced data on the server will not be affected. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    scanRepository.deleteAll()
                    Toast.makeText(this@SettingsActivity, "Local data cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun triggerSync() {
        Toast.makeText(this, "Sync started...", Toast.LENGTH_SHORT).show()
        // TODO: Trigger WorkManager sync
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
