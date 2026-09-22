package com.tradelog.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradelog.ui.dashboard.DashboardScreen
import com.tradelog.ui.returns.ReturnsScreen
import com.tradelog.ui.settings.AppearanceScreen
import com.tradelog.ui.settings.MailboxScreen
import com.tradelog.ui.settings.SettingsScreen
import com.tradelog.ui.theme.TradeLogTheme
import com.tradelog.ui.theme.Viz
import com.tradelog.ui.trades.TradesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Collected outside TradeLogTheme so the whole tree recomposes on change.
            val vm: AppViewModel = viewModel()
            val appearance by vm.appearance.collectAsState()
            TradeLogTheme(
                colorTheme = appearance.colorTheme,
                themeMode = appearance.themeMode,
            ) {
                TradeLogApp(vm)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("总览", Icons.Filled.Dashboard),
    RETURNS("收益", Icons.AutoMirrored.Filled.TrendingUp),
    TRADES("流水", Icons.AutoMirrored.Filled.ReceiptLong),
    SETTINGS("设置", Icons.Filled.Settings),
}

/** A settings sub-page pushed over the tab, or [NONE] when the menu itself is showing. */
private enum class SettingsPage(val title: String) {
    NONE(""),
    APPEARANCE("主题与配色"),
    MAILBOX("邮箱设置"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeLogApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var settingsPage by remember { mutableStateOf(SettingsPage.NONE) }
    var showManualSheet by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val dashboard by vm.dashboard.collectAsState()
    val performance by vm.performance.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val trades by vm.trades.collectAsState()
    val settings by vm.settings.collectAsState()
    val syncing by vm.syncing.collectAsState()

    LaunchedEffect(Unit) {
        vm.toasts.collect { message -> snackbar.showSnackbar(message) }
    }

    val onSettingsPage = tab == Tab.SETTINGS && settingsPage != SettingsPage.NONE

    // System back leaves a sub-page before it leaves the app.
    BackHandler(enabled = onSettingsPage) { settingsPage = SettingsPage.NONE }

    Scaffold(
        containerColor = Viz.colors.plane,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (onSettingsPage) {
                            settingsPage.title
                        } else {
                            when (tab) {
                                Tab.DASHBOARD -> "交易总览"
                                Tab.RETURNS -> "收益统计"
                                Tab.TRADES -> "交易流水"
                                Tab.SETTINGS -> "设置"
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (onSettingsPage) {
                        IconButton(onClick = { settingsPage = SettingsPage.NONE }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Viz.colors.plane,
                    titleContentColor = Viz.colors.primaryInk,
                    navigationIconContentColor = Viz.colors.primaryInk,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Viz.colors.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            // Leaving and re-entering settings starts at the menu, not the
                            // sub-page that happened to be open last time.
                            settingsPage = SettingsPage.NONE
                        },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
        floatingActionButton = {
            // The action follows the tab: the dashboard is about pulling mail in, the
            // ledger is where a missing trade gets typed in by hand.
            when {
                tab == Tab.DASHBOARD -> ExtendedFloatingActionButton(
                    onClick = { vm.sync(fullRescan = false) },
                    icon = {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(2.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    },
                    text = { Text(if (syncing) "同步中" else "同步邮件") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )

                tab == Tab.TRADES -> ExtendedFloatingActionButton(
                    onClick = { showManualSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("手动添加") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )

                else -> Unit
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(
                    state = dashboard,
                    onOpenTrades = { tab = Tab.TRADES },
                    onOpenReturns = { tab = Tab.RETURNS },
                )

                Tab.RETURNS -> ReturnsScreen(performance = performance)

                Tab.TRADES -> TradesScreen(
                    trades = trades,
                    onMarkReviewed = vm::markReviewed,
                    onDelete = vm::delete,
                    onAddManual = vm::addManualTrade,
                    onEdit = vm::editTrade,
                    showManualSheet = showManualSheet,
                    onManualSheetDismiss = { showManualSheet = false },
                )

                Tab.SETTINGS -> when (settingsPage) {
                    SettingsPage.NONE -> SettingsScreen(
                        settings = settings,
                        colorTheme = appearance.colorTheme,
                        themeMode = appearance.themeMode,
                        onOpenAppearance = { settingsPage = SettingsPage.APPEARANCE },
                        onOpenMailbox = { settingsPage = SettingsPage.MAILBOX },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    SettingsPage.APPEARANCE -> AppearanceScreen(
                        colorTheme = appearance.colorTheme,
                        themeMode = appearance.themeMode,
                        onColorThemeChange = vm::setColorTheme,
                        onThemeModeChange = vm::setThemeMode,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    SettingsPage.MAILBOX -> MailboxScreen(
                        settings = settings,
                        onSaveConnection = vm::saveConnection,
                        onTestConnection = vm::testConnection,
                        onSaveSyncPrefs = vm::saveSyncPrefs,
                        onClearPassword = vm::clearPassword,
                        onFullRescan = { vm.sync(fullRescan = true) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
