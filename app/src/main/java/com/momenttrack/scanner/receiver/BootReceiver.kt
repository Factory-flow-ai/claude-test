package com.momenttrack.scanner.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.momenttrack.scanner.MainActivity
import com.momenttrack.scanner.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            CoroutineScope(Dispatchers.IO).launch {
                val settingsRepository = SettingsRepository(context)
                val settings = settingsRepository.settingsFlow.first()
                
                if (settings.autoStartEnabled) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
