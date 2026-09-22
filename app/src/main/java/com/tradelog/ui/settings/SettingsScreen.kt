package com.tradelog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradelog.data.prefs.MailSettings
import com.tradelog.data.prefs.ThemeMode
import com.tradelog.ui.Panel
import com.tradelog.ui.theme.ColorTheme
import com.tradelog.ui.theme.Viz

/**
 * The settings menu: one row per sub-page.
 *
 * Each row carries its current state on the right, so the thing most often being checked —
 * which theme is on, whether the mailbox is connected — is answered without opening anything.
 */
@Composable
fun SettingsScreen(
    settings: MailSettings,
    colorTheme: ColorTheme,
    themeMode: ThemeMode,
    onOpenAppearance: () -> Unit,
    onOpenMailbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column {
                MenuRow(
                    icon = Icons.Filled.Palette,
                    title = "主题与配色",
                    subtitle = "深浅主题、涨跌颜色",
                    status = themeMode.label + " · " + colorTheme.label,
                    onClick = onOpenAppearance,
                )
                Spacer(Modifier.height(14.dp))
                MenuRow(
                    icon = Icons.Filled.Mail,
                    title = "邮箱设置",
                    subtitle = "IMAP 连接、自动同步",
                    status = if (settings.isConfigured) settings.username else "未配置",
                    statusColor = if (settings.isConfigured) Viz.colors.ok else Viz.colors.warning,
                    onClick = onOpenMailbox,
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("关于解析")
                Text(
                    text = "解析按字段标签匹配（指示類別 / 已成交數量 / 成交價 等），" +
                        "买入记为现金流出、卖出记为现金流入，" +
                        "成交额按 数量 × 成交价 计算。\n\n" +
                        "汇丰的执行通知不含费用字段，所以费用一律记为 0，" +
                        "实际佣金请以月结单或交割单为准。" +
                        "日期取邮件发送时间，与交易所成交时刻可能有偏差。\n\n" +
                        "如果某封邮件缺字段，记录会标记为「待核对」，不会静默写入错误数字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Viz.colors.secondaryInk,
                )
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit,
    statusColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Viz.colors.primaryInk)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Viz.colors.mutedInk)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor ?: Viz.colors.secondaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.width(120.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Viz.colors.mutedInk,
        )
    }
}
