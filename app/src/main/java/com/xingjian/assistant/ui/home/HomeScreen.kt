package com.xingjian.assistant.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.ui.components.EmptyState
import com.xingjian.assistant.ui.components.SectionCard
import com.xingjian.assistant.ui.components.StatCard
import com.xingjian.assistant.ui.components.TagChip
import com.xingjian.assistant.ui.theme.TagImportant
import com.xingjian.assistant.ui.theme.TagNeutral
import com.xingjian.assistant.ui.theme.TagNotice
import com.xingjian.assistant.ui.theme.TagSpam

@Composable
fun HomeScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: HomeViewModel = viewModel(factory = container.viewModelFactory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val today = remember { DateTimeUtils.formatDate(System.currentTimeMillis()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(
                title = "今日概览",
                trailing = {
                    Text(
                        text = today,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "今日支出",
                        value = DateTimeUtils.formatMoney(state.todayExpense),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                    StatCard(
                        label = "今日收入",
                        value = DateTimeUtils.formatMoney(state.todayIncome),
                        modifier = Modifier.weight(1f),
                        accent = TagImportant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "本月支出",
                        value = DateTimeUtils.formatMoney(state.monthExpense),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "本月结余",
                        value = DateTimeUtils.formatMoney(state.monthBalance),
                        hint = "收入 ${DateTimeUtils.formatMoney(state.monthIncome)}",
                        modifier = Modifier.weight(1f),
                        accent = TagNeutral
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "重要短信",
                    value = "${state.smsImportant}",
                    hint = "共 ${state.smsTotal} 条",
                    modifier = Modifier.weight(1f),
                    accent = TagImportant
                )
                StatCard(
                    label = "骚扰通话",
                    value = "${state.callSpam}",
                    hint = "共 ${state.callTotal} 条",
                    modifier = Modifier.weight(1f),
                    accent = TagSpam
                )
                StatCard(
                    label = "账单邮件",
                    value = "${state.mailBill}",
                    hint = "共 ${state.mailTotal} 封",
                    modifier = Modifier.weight(1f),
                    accent = TagNotice
                )
            }
        }

        item {
            SectionCard(
                title = "近期提醒",
                trailing = {
                    Text(
                        text = "${state.highlights.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ) {
                if (state.highlights.isEmpty()) {
                    EmptyState("暂无提醒")
                } else {
                    state.highlights.forEachIndexed { index, item ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TagChip(text = item.tag, color = tagColor(item.tagKind))
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = DateTimeUtils.formatDateTime(item.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (index != state.highlights.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        state.latestProfile?.let { profile ->
            item {
                SectionCard(
                    title = "最新个人画像",
                    trailing = {
                        Text(
                            text = profile.providerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ) {
                    Text(
                        text = profile.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = profile.content.trim().take(260),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun tagColor(kind: String) = when (kind) {
    "SMS" -> TagImportant
    "MAIL" -> TagNotice
    "CALL" -> TagSpam
    else -> TagNeutral
}
