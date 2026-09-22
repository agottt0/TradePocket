package com.tradelog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.data.perf.CurrencyPerformance
import com.tradelog.data.perf.PerformanceCalculator
import com.tradelog.data.prefs.MailSettings
import com.tradelog.data.prefs.SettingsStore
import com.tradelog.data.prefs.ThemeMode
import com.tradelog.data.repo.TradeRepository
import com.tradelog.sync.MailSyncWorker
import com.tradelog.ui.theme.ColorTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Appearance settings, resolved from storage into the types the theme actually takes. */
data class Appearance(
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
)

data class DashboardState(
    val tradeCount: Int = 0,
    val reviewCount: Int = 0,
    val recent: List<Trade> = emptyList(),
    val settings: MailSettings = MailSettings(),
    /** One entry per currency. Never summed across entries — there are no exchange rates. */
    val performance: List<CurrencyPerformance> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TradeRepository.get(app)
    private val store = SettingsStore.get(app)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val toasts = messages.receiveAsFlow()

    /**
     * Realized performance per currency, recomputed whenever the ledger changes.
     *
     * FIFO matching is O(n) per symbol and the ledger is small (a personal trading history),
     * so recomputing on every change is cheaper and far simpler than maintaining incremental
     * state that could drift out of sync with the trades table.
     */
    val performance: StateFlow<List<CurrencyPerformance>> = repo.observeTrades()
        .map { PerformanceCalculator.compute(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboard: StateFlow<DashboardState> = combine(
        repo.observeCount(),
        repo.observeReviewCount(),
        repo.observeRecent(8),
        repo.observeSettings(),
        performance,
    ) { count, review, recent, settings, perf ->
        DashboardState(
            tradeCount = count,
            reviewCount = review,
            recent = recent,
            settings = settings,
            performance = perf,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    val trades: StateFlow<List<Trade>> = repo.observeTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<MailSettings> = repo.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MailSettings())

    /**
     * Appearance is collected eagerly and never unsubscribed: it decides the colours of the
     * whole tree, so it must have a real value before the first frame rather than a default
     * that flashes and then corrects itself.
     */
    val appearance: StateFlow<Appearance> = repo.observeSettings()
        .map {
            Appearance(
                colorTheme = ColorTheme.fromName(it.colorThemeName),
                themeMode = ThemeMode.fromName(it.themeModeName),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    fun setColorTheme(theme: ColorTheme) = store.saveColorTheme(theme.name)

    fun setThemeMode(mode: ThemeMode) = store.saveThemeMode(mode.name)

    fun sync(fullRescan: Boolean = false) {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            val outcome = repo.sync(fullRescan)
            messages.trySend(outcome.summary())
            _syncing.value = false
        }
    }

    fun testConnection(host: String, port: Int, username: String, password: String?, folder: String) {
        viewModelScope.launch {
            val error = repo.testConnection(host, port, username, password, folder)
            messages.trySend(error?.let { "连接失败：$it" } ?: "连接成功")
        }
    }

    fun saveConnection(
        host: String,
        port: Int,
        username: String,
        password: String?,
        folder: String,
        senderFilter: String,
    ) {
        store.saveConnection(host, port, username, password, folder, senderFilter)
        messages.trySend("已保存邮箱设置")
    }

    fun saveSyncPrefs(autoSync: Boolean, intervalHours: Int, lookbackDays: Int) {
        val app = getApplication<Application>()
        store.saveSyncPrefs(autoSync, intervalHours, lookbackDays)
        if (autoSync && store.read().isConfigured) {
            MailSyncWorker.schedule(app, intervalHours)
            messages.trySend("已开启后台自动同步，每 $intervalHours 小时一次")
        } else {
            MailSyncWorker.cancel(app)
            if (autoSync) messages.trySend("请先完成邮箱配置") else messages.trySend("已关闭后台同步")
        }
    }

    fun clearPassword() {
        store.clearPassword()
        MailSyncWorker.cancel(getApplication())
        messages.trySend("已清除保存的密码")
    }

    fun addManualTrade(
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double,
    ) {
        viewModelScope.launch {
            repo.addManual(
                tradeDate = tradeDate,
                symbol = symbol,
                name = name,
                side = side,
                quantity = quantity,
                price = price,
                currency = currency,
                fees = fees,
            )
            messages.trySend("已添加 " + symbol.trim().uppercase() + " " + Format.quantity(quantity) + " 股")
        }
    }

    /**
     * Applies a hand-edit to an existing trade.
     *
     * The cash effect is recomputed from the edited quantity, price and fees rather than kept
     * from the old row, so [Trade.netAmount] and [Trade.gross] can never drift out of step with
     * the fields the user just changed. [Trade.messageKey] is left alone: it is the
     * de-duplication key, so preserving it is what stops a later rescan from re-importing the
     * original alongside the correction. Editing also clears `needsReview` — a human has now
     * looked at the row, which is exactly what that flag was asking for.
     */
    fun editTrade(
        original: Trade,
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double,
    ) {
        viewModelScope.launch {
            repo.updateFields(
                original = original,
                tradeDate = tradeDate,
                symbol = symbol,
                name = name,
                side = side,
                quantity = quantity,
                price = price,
                currency = currency,
                fees = fees,
            )
            messages.trySend("已更新该笔记录")
        }
    }

    fun markReviewed(trade: Trade) {
        viewModelScope.launch { repo.update(trade.copy(needsReview = false)) }
    }

    fun delete(trade: Trade) {
        viewModelScope.launch {
            repo.delete(trade)
            messages.trySend("已删除该笔记录")
        }
    }
}
