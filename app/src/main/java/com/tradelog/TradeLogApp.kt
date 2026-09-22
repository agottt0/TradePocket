package com.tradelog

import android.app.Application
import com.tradelog.data.prefs.SettingsStore
import com.tradelog.sync.MailSyncWorker

class TradeLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Re-arm the periodic scan after an app update or a reboot-triggered cold start.
        val settings = SettingsStore.get(this).read()
        if (settings.autoSyncEnabled && settings.isConfigured) {
            MailSyncWorker.schedule(this, settings.syncIntervalHours)
        }
    }
}
