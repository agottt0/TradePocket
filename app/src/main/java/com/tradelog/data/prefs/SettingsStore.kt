package com.tradelog.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tradelog.data.mail.ImapConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Light / dark appearance. [SYSTEM] follows the device setting. */
enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("跟随系统", "与系统深色模式保持一致"),
    LIGHT("浅色", "始终使用浅色界面"),
    DARK("深色", "始终使用深色界面"),
    ;

    companion object {
        val DEFAULT = SYSTEM

        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class MailSettings(
    val host: String = "",
    val port: Int = 993,
    val username: String = "",
    val hasPassword: Boolean = false,
    val folder: String = "INBOX",
    /** Comma-separated sender filters; empty means "any HSBC-looking sender". */
    val senderFilter: String = "hsbc.com",
    val autoSyncEnabled: Boolean = false,
    val syncIntervalHours: Int = 6,
    /** How far back a manual "full rescan" reaches. */
    val lookbackDays: Int = 90,
    /** Watermark for incremental scans: only ever advanced by a successful sync. */
    val lastSyncAt: Long = 0L,
    /** When a sync was last attempted, successful or not — this is what the UI shows. */
    val lastAttemptAt: Long = 0L,
    val lastSyncSummary: String = "",
    /** Name of the chosen [com.tradelog.ui.theme.ColorTheme]; resolved leniently on read. */
    val colorThemeName: String = "RED_GAIN",
    /** Name of the chosen [ThemeMode]; resolved leniently on read. */
    val themeModeName: String = "SYSTEM",
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && username.isNotBlank() && hasPassword
}

/**
 * Settings + credential storage.
 *
 * The IMAP app password is a real secret, so it lives in [EncryptedSharedPreferences]
 * (AES-256-GCM under a keystore-backed master key) and is never logged or copied into
 * app state. Everything else is ordinary preferences.
 *
 * **One instance per process, via [get].** [EncryptedSharedPreferences] keeps its change
 * listeners on the wrapper object and notifies them only from that same wrapper's editor, so a
 * second wrapper writing the same file is silent. Constructing a store per write — which is
 * what the UI used to do — meant no observer ever saw a change and the screen never updated.
 * [changes] is a second belt: the flow re-reads on every write here and does not depend on the
 * platform listener firing at all.
 */
class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "tradelog_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Bumped after every write, so observers re-read regardless of platform listeners. */
    private val changes = MutableStateFlow(0)

    fun observe(): Flow<MailSettings> = changes.map { read() }

    fun read(): MailSettings = MailSettings(
        host = prefs.getString(KEY_HOST, "") ?: "",
        port = prefs.getInt(KEY_PORT, 993),
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        hasPassword = !prefs.getString(KEY_PASSWORD, "").isNullOrEmpty(),
        folder = prefs.getString(KEY_FOLDER, "INBOX") ?: "INBOX",
        senderFilter = prefs.getString(KEY_SENDER_FILTER, "hsbc.com") ?: "hsbc.com",
        autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, false),
        syncIntervalHours = prefs.getInt(KEY_INTERVAL, 6),
        lookbackDays = prefs.getInt(KEY_LOOKBACK, 90),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT, 0L),
        lastSyncSummary = prefs.getString(KEY_LAST_SUMMARY, "") ?: "",
        colorThemeName = prefs.getString(KEY_COLOR_THEME, "RED_GAIN") ?: "RED_GAIN",
        themeModeName = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM",
    )

    fun saveColorTheme(name: String) = write { putString(KEY_COLOR_THEME, name) }

    fun saveThemeMode(name: String) = write { putString(KEY_THEME_MODE, name) }

    fun saveConnection(
        host: String,
        port: Int,
        username: String,
        password: String?,
        folder: String,
        senderFilter: String,
    ) = write {
        putString(KEY_HOST, host.trim())
        putInt(KEY_PORT, port)
        putString(KEY_USERNAME, username.trim())
        // A null password means "leave the stored one alone" — the UI never round-trips it.
        if (password != null) putString(KEY_PASSWORD, password)
        putString(KEY_FOLDER, folder.trim().ifEmpty { "INBOX" })
        putString(KEY_SENDER_FILTER, senderFilter.trim())
    }

    fun saveSyncPrefs(autoSync: Boolean, intervalHours: Int, lookbackDays: Int) = write {
        putBoolean(KEY_AUTO_SYNC, autoSync)
        putInt(KEY_INTERVAL, intervalHours.coerceIn(1, 24))
        putInt(KEY_LOOKBACK, lookbackDays.coerceIn(1, 3650))
    }

    fun clearPassword() = write { remove(KEY_PASSWORD) }

    /**
     * Records a sync attempt.
     *
     * [advanceWatermark] must be false for a failed run: the watermark is what the next
     * incremental scan starts from, so advancing it past a window that was never actually
     * read would silently skip every confirmation in that window.
     */
    fun recordSync(at: Long, summary: String, advanceWatermark: Boolean) = write {
        putLong(KEY_LAST_ATTEMPT, at)
        putString(KEY_LAST_SUMMARY, summary)
        if (advanceWatermark) putLong(KEY_LAST_SYNC, at)
    }

    /** Builds a usable [ImapConfig], or null when the user has not finished setup. */
    fun imapConfig(): ImapConfig? {
        val s = read()
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        if (s.host.isBlank() || s.username.isBlank() || password.isEmpty()) return null
        return ImapConfig(
            host = s.host,
            port = s.port,
            useSsl = true,
            username = s.username,
            password = password,
            folder = s.folder,
        )
    }

    /** Used by the settings screen's "test connection" before anything is persisted. */
    fun draftConfig(host: String, port: Int, username: String, password: String?, folder: String): ImapConfig? {
        val pwd = password?.takeIf { it.isNotEmpty() } ?: prefs.getString(KEY_PASSWORD, "").orEmpty()
        if (host.isBlank() || username.isBlank() || pwd.isEmpty()) return null
        return ImapConfig(host.trim(), port, true, username.trim(), pwd, folder.trim().ifEmpty { "INBOX" })
    }

    /** Applies an edit and then tells every observer, in that order. */
    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        changes.value = changes.value + 1
    }

    companion object {
        private const val KEY_HOST = "imap_host"
        private const val KEY_PORT = "imap_port"
        private const val KEY_USERNAME = "imap_username"
        private const val KEY_PASSWORD = "imap_password"
        private const val KEY_FOLDER = "imap_folder"
        private const val KEY_SENDER_FILTER = "sender_filter"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_INTERVAL = "sync_interval_hours"
        private const val KEY_LOOKBACK = "lookback_days"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_LAST_ATTEMPT = "last_attempt_at"
        private const val KEY_LAST_SUMMARY = "last_sync_summary"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_THEME_MODE = "theme_mode"

        @Volatile
        private var instance: SettingsStore? = null

        /**
         * The one store for the process. Never construct a second one: another
         * [EncryptedSharedPreferences] wrapper writes the same file but notifies only its own
         * listeners, which is how a saved setting silently fails to reach the UI.
         */
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
