package com.tradelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tradelog.data.prefs.MailSettings
import com.tradelog.ui.Format
import com.tradelog.ui.Panel
import com.tradelog.ui.theme.Viz
import kotlin.math.roundToInt

private data class Preset(val label: String, val host: String, val port: Int)

private val PRESETS = listOf(
    Preset("Gmail", "imap.gmail.com", 993),
    Preset("Outlook", "outlook.office365.com", 993),
    Preset("QQ 邮箱", "imap.qq.com", 993),
    Preset("iCloud", "imap.mail.me.com", 993),
)

/** Mailbox connection, background sync and the import status, on their own page. */
@Composable
fun MailboxScreen(
    settings: MailSettings,
    onSaveConnection: (host: String, port: Int, username: String, password: String?, folder: String, senderFilter: String) -> Unit,
    onTestConnection: (host: String, port: Int, username: String, password: String?, folder: String) -> Unit,
    onSaveSyncPrefs: (autoSync: Boolean, intervalHours: Int, lookbackDays: Int) -> Unit,
    onClearPassword: () -> Unit,
    onFullRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by remember(settings.host) { mutableStateOf(settings.host) }
    var port by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var username by remember(settings.username) { mutableStateOf(settings.username) }
    // Never pre-filled from storage: an empty field means "keep the saved password".
    var password by remember { mutableStateOf("") }
    var folder by remember(settings.folder) { mutableStateOf(settings.folder) }
    var senderFilter by remember(settings.senderFilter) { mutableStateOf(settings.senderFilter) }

    var autoSync by remember(settings.autoSyncEnabled) { mutableStateOf(settings.autoSyncEnabled) }
    var interval by remember(settings.syncIntervalHours) { mutableStateOf(settings.syncIntervalHours.toFloat()) }
    var lookback by remember(settings.lookbackDays) { mutableStateOf(settings.lookbackDays.toFloat()) }

    val portValue = port.toIntOrNull() ?: 993
    val passwordArg = password.takeIf { it.isNotEmpty() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("邮箱连接")
                Text(
                    text = "使用 IMAP 只读连接。请在邮箱服务商那里生成「应用专用密码」，不要填登录密码。" +
                        "密码用系统密钥库加密后保存在本机，不会上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Viz.colors.secondaryInk,
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = {
                                host = preset.host
                                port = preset.port.toString()
                            },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IMAP 服务器") },
                    placeholder = { Text("imap.gmail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("端口（SSL）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("邮箱地址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (settings.hasPassword) "应用专用密码（留空表示不修改）" else "应用专用密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("邮箱文件夹") },
                    placeholder = { Text("INBOX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = senderFilter,
                    onValueChange = { senderFilter = it },
                    label = { Text("发件人过滤（逗号分隔）") },
                    placeholder = { Text("hsbc.com") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            "只解析来自这些地址的邮件，避免误抓其他邮件。",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveConnection(host, portValue, username, passwordArg, folder, senderFilter)
                            password = ""
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                    OutlinedButton(
                        onClick = { onTestConnection(host, portValue, username, passwordArg, folder) },
                        modifier = Modifier.weight(1f),
                    ) { Text("测试连接") }
                }

                if (settings.hasPassword) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onClearPassword) {
                        Text("清除已保存的密码", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("自动同步")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "后台定时扫描邮箱",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Viz.colors.primaryInk,
                        )
                        Text(
                            "由系统调度，实际执行时间可能有偏移。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Viz.colors.mutedInk,
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = {
                            autoSync = it
                            onSaveSyncPrefs(it, interval.roundToInt(), lookback.roundToInt())
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                LabelledSlider(
                    label = "同步间隔",
                    valueText = "${interval.roundToInt()} 小时",
                    value = interval,
                    range = 1f..24f,
                    steps = 22,
                    onValueChange = { interval = it },
                    onValueChangeFinished = {
                        onSaveSyncPrefs(autoSync, interval.roundToInt(), lookback.roundToInt())
                    },
                )

                Spacer(Modifier.height(8.dp))
                LabelledSlider(
                    label = "全量重扫回溯",
                    valueText = "${lookback.roundToInt()} 天",
                    value = lookback,
                    range = 7f..730f,
                    steps = 0,
                    onValueChange = { lookback = it },
                    onValueChangeFinished = {
                        onSaveSyncPrefs(autoSync, interval.roundToInt(), lookback.roundToInt())
                    },
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onFullRescan, modifier = Modifier.fillMaxWidth()) {
                    Text("立即全量重扫")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "重复的邮件会按 Message-ID 自动跳过，重扫不会产生重复记录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.mutedInk,
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("状态")
                InfoRow("上次同步", Format.relative(settings.lastAttemptAt))
                if (settings.lastSyncSummary.isNotEmpty()) {
                    InfoRow("结果", settings.lastSyncSummary)
                }
                InfoRow("配置状态", if (settings.isConfigured) "已就绪" else "未完成")
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Viz.colors.secondaryInk)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = Viz.colors.primaryInk)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Viz.colors.secondaryInk)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Viz.colors.primaryInk)
    }
}
