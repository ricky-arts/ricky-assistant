package com.xingjian.assistant.ui.comms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.core.Permissions
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.CallCategory
import com.xingjian.assistant.domain.model.SmsCategory
import com.xingjian.assistant.ui.components.CategoryPickerDialog
import com.xingjian.assistant.ui.components.EmptyState
import com.xingjian.assistant.ui.components.MessageBanner
import com.xingjian.assistant.ui.components.PermissionDialog
import com.xingjian.assistant.ui.components.SearchField
import com.xingjian.assistant.ui.components.SectionCard
import com.xingjian.assistant.ui.components.TagChip
import com.xingjian.assistant.ui.mail.MailPane
import com.xingjian.assistant.ui.theme.TagImportant
import com.xingjian.assistant.ui.theme.TagNeutral
import com.xingjian.assistant.ui.theme.TagNotice
import com.xingjian.assistant.ui.theme.TagSpam

private enum class PermissionKind { SMS, CALL }

@Composable
fun CommsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: CommsViewModel = viewModel(factory = container.viewModelFactory)
    val smsList by vm.sms.collectAsStateWithLifecycle()
    val callList by vm.calls.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val smsFilter by vm.smsFilter.collectAsStateWithLifecycle()
    val callFilter by vm.callFilter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    val smsGranted = remember(refreshTick) { Permissions.hasSms(context) }
    val callGranted = remember(refreshTick) { Permissions.hasCallLog(context) }
    var pendingPermission by remember { mutableStateOf<PermissionKind?>(null) }
    var smsPicker by remember { mutableStateOf<SmsEntity?>(null) }
    var callPicker by remember { mutableStateOf<CallEntity?>(null) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshTick++
        if (granted) vm.sync(true, false) else vm.postMessage("短信权限被拒绝，可在系统设置中手动开启")
    }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshTick++
        if (granted) vm.sync(false, true) else vm.postMessage("通话记录权限被拒绝，可在系统设置中手动开启")
    }

    val filteredSms = remember(smsList, smsFilter, query) {
        smsList.filter { item ->
            val byCategory = smsFilter == null || item.category == smsFilter?.name
            val byQuery = query.isBlank() ||
                item.body.contains(query, ignoreCase = true) ||
                item.address.contains(query, ignoreCase = true)
            byCategory && byQuery
        }
    }
    val filteredCalls = remember(callList, callFilter, query) {
        callList.filter { item ->
            val byCategory = callFilter == null || item.category == callFilter?.name
            val byQuery = query.isBlank() ||
                item.number.contains(query) ||
                (item.cachedName?.contains(query, ignoreCase = true) == true)
            byCategory && byQuery
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionCard(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = tab == CommsTab.SMS,
                    onClick = { vm.selectTab(CommsTab.SMS) },
                    label = { Text("短信") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = tab == CommsTab.CALL,
                    onClick = { vm.selectTab(CommsTab.CALL) },
                    label = { Text("通话") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = tab == CommsTab.MAIL,
                    onClick = { vm.selectTab(CommsTab.MAIL) },
                    label = { Text("邮件") }
                )
                if (tab != CommsTab.MAIL) {
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            when (tab) {
                                CommsTab.SMS -> if (smsGranted) vm.sync(true, false) else pendingPermission = PermissionKind.SMS
                                CommsTab.CALL -> if (callGranted) vm.sync(false, true) else pendingPermission = PermissionKind.CALL
                                CommsTab.MAIL -> Unit
                            }
                        },
                        enabled = !syncing
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "同步")
                    }
                }
            }
            if (tab != CommsTab.MAIL) {
                Spacer(Modifier.height(4.dp))
                SearchField(
                    value = query,
                    onValueChange = { vm.setQuery(it) },
                    placeholder = if (tab == CommsTab.SMS) "搜索号码或短信内容" else "搜索号码或联系人"
                )
            }
        }

        if (tab == CommsTab.MAIL) {
            MailPane(container, Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message != null) {
                    item {
                        MessageBanner(message = message!!, onDismiss = { vm.clearMessage() })
                    }
                }

                if (tab == CommsTab.SMS) {
                    if (!smsGranted) {
                        item {
                            PermissionRequiredCard(
                                title = "需要读取短信权限",
                                onGrant = { pendingPermission = PermissionKind.SMS },
                                onOpenSettings = { Permissions.openAppSettings(context); refreshTick++ }
                            )
                        }
                    } else {
                        item {
                            CategoryFilterRow(
                                selectedName = smsFilter?.name,
                                allLabel = "全部短信",
                                options = SmsCategory.entries.map { it.name to it.label },
                                onSelect = { name ->
                                    vm.setSmsFilter(name?.let { runCatching { SmsCategory.valueOf(it) }.getOrNull() })
                                }
                            )
                        }
                        if (filteredSms.isEmpty()) {
                            item { SectionCard { EmptyState("暂无短信") } }
                        }
                        items(filteredSms, key = { it.id }) { item ->
                            SmsCard(
                                item = item,
                                onCategory = { smsPicker = item },
                                onStar = { vm.toggleSmsStar(item) },
                                onDelete = { vm.deleteSms(item.id) }
                            )
                        }
                    }
                } else {
                    if (!callGranted) {
                        item {
                            PermissionRequiredCard(
                                title = "需要读取通话记录权限",
                                onGrant = { pendingPermission = PermissionKind.CALL },
                                onOpenSettings = { Permissions.openAppSettings(context); refreshTick++ }
                            )
                        }
                    } else {
                        item {
                            CategoryFilterRow(
                                selectedName = callFilter?.name,
                                allLabel = "全部通话",
                                options = CallCategory.entries.map { it.name to it.label },
                                onSelect = { name ->
                                    vm.setCallFilter(name?.let { runCatching { CallCategory.valueOf(it) }.getOrNull() })
                                }
                            )
                        }
                        if (filteredCalls.isEmpty()) {
                            item { SectionCard { EmptyState("暂无通话记录") } }
                        }
                        items(filteredCalls, key = { it.id }) { item ->
                            CallCard(
                                item = item,
                                onCategory = { callPicker = item },
                                onStar = { vm.toggleCallStar(item) },
                                onDelete = { vm.deleteCall(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPermission?.let { kind ->
        PermissionDialog(
            title = if (kind == PermissionKind.SMS) "读取短信权限" else "读取通话记录权限",
            description = if (kind == PermissionKind.SMS) {
                "星简助手需要读取本机短信，用于离线分类、搜索与重要提醒。不会上传任何内容。"
            } else {
                "星简助手需要读取本机通话记录，用于离线识别骚扰、快递与官方来电。不会上传任何内容。"
            },
            onConfirm = {
                pendingPermission = null
                if (kind == PermissionKind.SMS) smsLauncher.launch(android.Manifest.permission.READ_SMS)
                else callLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
            },
            onDismiss = { pendingPermission = null }
        )
    }

    smsPicker?.let { item ->
        CategoryPickerDialog(
            title = "修改短信分类",
            options = SmsCategory.entries.map { it.name to it.label },
            selected = item.category,
            onSelect = { name ->
                runCatching { SmsCategory.valueOf(name) }.getOrNull()?.let { category ->
                    vm.updateSmsCategory(item.id, category)
                }
                smsPicker = null
            },
            onDismiss = { smsPicker = null }
        )
    }

    callPicker?.let { item ->
        CategoryPickerDialog(
            title = "修改通话分类",
            options = CallCategory.entries.map { it.name to it.label },
            selected = item.category,
            onSelect = { name ->
                runCatching { CallCategory.valueOf(name) }.getOrNull()?.let { category ->
                    vm.updateCallCategory(item.id, category)
                }
                callPicker = null
            },
            onDismiss = { callPicker = null }
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selectedName: String?,
    allLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedName == null,
                onClick = { onSelect(null) },
                label = { Text(allLabel) }
            )
        }
        items(options, key = { it.first }) { (name, label) ->
            FilterChip(
                selected = selectedName == name,
                onClick = { onSelect(name) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    title: String,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    SectionCard(title = title) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onGrant) {
                Icon(Icons.Filled.Lock, contentDescription = "去授权")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "系统设置")
            }
        }
    }
}

@Composable
private fun SmsCard(
    item: SmsEntity,
    onCategory: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.address,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TagChip(
                    text = runCatching { SmsCategory.valueOf(item.category).label }.getOrDefault("未分类"),
                    color = smsColor(item.category)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateTimeUtils.formatDateTime(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCategory) {
                    Icon(Icons.Filled.Edit, contentDescription = "分类")
                }
                IconButton(onClick = onStar) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "标记重要",
                        tint = if (item.starred) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CallCard(
    item: CallEntity,
    onCategory: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit
) {
    val name = item.cachedName?.takeIf { it.isNotBlank() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name ?: item.number,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TagChip(
                    text = runCatching { CallCategory.valueOf(item.category).label }.getOrDefault("未分类"),
                    color = callColor(item.category)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (name != null) append(item.number)
                    if (name != null) append(" · ")
                    append(callTypeLabel(item.callType))
                    if (item.durationSeconds > 0) {
                        append(" · ")
                        append(formatDuration(item.durationSeconds))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateTimeUtils.formatDateTime(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCategory) {
                    Icon(Icons.Filled.Edit, contentDescription = "分类")
                }
                IconButton(onClick = onStar) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "标记",
                        tint = if (item.starred) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun smsColor(category: String) = when (category) {
    SmsCategory.SPAM.name -> TagSpam
    SmsCategory.NOTICE.name -> TagNotice
    SmsCategory.IMPORTANT.name -> TagImportant
    else -> TagNeutral
}

private fun callColor(category: String) = when (category) {
    CallCategory.SPAM.name -> TagSpam
    CallCategory.EXPRESS.name -> TagNotice
    CallCategory.OFFICIAL.name -> TagNeutral
    CallCategory.PERSONAL.name -> TagImportant
    else -> TagNeutral
}

private fun callTypeLabel(type: Int): String = when (type) {
    1 -> "来电"
    2 -> "呼出"
    3 -> "未接"
    4 -> "语音信箱"
    5 -> "已拒接"
    6 -> "已拦截"
    else -> "通话"
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}秒"
    val minutes = seconds / 60
    val remain = seconds % 60
    return "${minutes}分${remain}秒"
}
