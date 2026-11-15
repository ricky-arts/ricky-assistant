package com.xingjian.assistant.ui.ai

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.data.local.entity.AiProfileEntity
import com.xingjian.assistant.data.local.entity.AiProviderEntity
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.AiApiStyle
import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.ProxyType
import com.xingjian.assistant.ui.components.ConfirmDialog
import com.xingjian.assistant.ui.components.EmptyState
import com.xingjian.assistant.ui.components.MessageBanner
import com.xingjian.assistant.ui.components.ProxyConfigDialog
import com.xingjian.assistant.ui.components.SectionCard

@Composable
fun AiScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: AiViewModel = viewModel(factory = container.viewModelFactory)
    val providers by vm.providers.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val proxyConfig by vm.proxyConfig.collectAsStateWithLifecycle()
    val counts by vm.dataCounts.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var showProxyDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiProviderEntity?>(null) }
    var pendingDeleteProfile by remember { mutableStateOf<AiProfileEntity?>(null) }

    // Key 是否已保存：随服务商列表与提示消息刷新
    val keyFlags = remember(providers, message) {
        val map = mutableMapOf<Long, Boolean>()
        providers.forEach { provider -> map[provider.id] = vm.hasKey(provider.id) }
        map
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (message != null) {
            item { MessageBanner(message = message!!, onDismiss = { vm.clearMessage() }) }
        }

        item { ProxyCard(config = proxyConfig, onConfigure = { showProxyDialog = true }) }

        item {
            ProvidersCard(
                providers = providers,
                keyFlags = keyFlags,
                onToggle = { provider, enabled -> vm.toggle(provider, enabled) },
                onEdit = { provider -> editing = provider }
            )
        }

        item {
            DataSummaryCard(
                counts = counts,
                generating = generating,
                onGenerate = { vm.generate() }
            )
        }

        item {
            ProfilesCard(
                profiles = profiles,
                onDelete = { profile -> pendingDeleteProfile = profile }
            )
        }
    }

    if (showProxyDialog) {
        ProxyConfigDialog(
            title = "AI 代理配置",
            config = proxyConfig,
            onDismiss = { showProxyDialog = false },
            onSave = { enabled, type, host, port, username, password ->
                vm.saveProxy(enabled, type, host, port, username, password)
                showProxyDialog = false
            }
        )
    }

    editing?.let { provider ->
        ProviderEditDialog(
            provider = provider,
            hasKey = keyFlags[provider.id] == true,
            onDismiss = { editing = null },
            onSave = { updated, apiKey ->
                vm.saveProvider(updated, apiKey)
                editing = null
            },
            onClearKey = {
                vm.clearKey(provider.id)
                editing = null
            }
        )
    }

    pendingDeleteProfile?.let { profile ->
        ConfirmDialog(
            title = "删除画像",
            text = "确定删除「${profile.title}」？此操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                vm.deleteProfile(profile.id)
                pendingDeleteProfile = null
            },
            onDismiss = { pendingDeleteProfile = null }
        )
    }
}

@Composable
private fun ProxyCard(config: ProxyConfig, onConfigure: () -> Unit) {
    val desc = if (config.enabled && config.type != ProxyType.NONE) {
        config.type.label + " · " + config.host + ":" + config.port
    } else {
        "未启用代理（直连服务商接口）"
    }
    SectionCard(
        title = "AI 网络代理",
        trailing = {
            IconButton(onClick = onConfigure) {
                Icon(Icons.Filled.Settings, contentDescription = "配置代理")
            }
        }
    ) {
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProvidersCard(
    providers: List<AiProviderEntity>,
    keyFlags: Map<Long, Boolean>,
    onToggle: (AiProviderEntity, Boolean) -> Unit,
    onEdit: (AiProviderEntity) -> Unit
) {
    val enabledCount = providers.count { it.enabled }
    SectionCard(
        title = "服务商与 API Key",
        trailing = {
            Text(
                text = "已启用 " + enabledCount + " 个",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        if (providers.isEmpty()) {
            EmptyState("暂无服务商模板。")
        }
        providers.forEachIndexed { index, provider ->
            ProviderRow(
                provider = provider,
                hasKey = keyFlags[provider.id] == true,
                onToggle = { enabled -> onToggle(provider, enabled) },
                onEdit = { onEdit(provider) }
            )
            if (index != providers.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun DataSummaryCard(
    counts: AiDataCounts,
    generating: Boolean,
    onGenerate: () -> Unit
) {
    SectionCard(title = "本地数据摘要") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("短信", counts.sms.toString(), Modifier.weight(1f))
            SummaryTile("通话", counts.calls.toString(), Modifier.weight(1f))
            SummaryTile("邮件", counts.mails.toString(), Modifier.weight(1f))
            SummaryTile("账目", counts.transactions.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onGenerate,
            enabled = !generating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Star, contentDescription = "生成个人画像")
        }
    }
}

@Composable
private fun ProfilesCard(profiles: List<AiProfileEntity>, onDelete: (AiProfileEntity) -> Unit) {
    SectionCard(title = "个人画像") {
        if (profiles.isEmpty()) {
            EmptyState("暂无画像")
        }
        profiles.forEachIndexed { index, profile ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = profile.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.providerLabel + " · " + profile.model + " · " +
                        DateTimeUtils.formatDateTime(profile.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = profile.content,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { onDelete(profile) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除画像")
                    }
                }
            }
            if (index != profiles.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: AiProviderEntity,
    hasKey: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!hasKey) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "未配置 Key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = provider.model + " · " + provider.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = provider.enabled, onCheckedChange = onToggle)
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "配置 " + provider.label)
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderEditDialog(
    provider: AiProviderEntity,
    hasKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (AiProviderEntity, String) -> Unit,
    onClearKey: () -> Unit
) {
    var label by remember { mutableStateOf(provider.label) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var model by remember { mutableStateOf(provider.model) }
    var apiStyle by remember { mutableStateOf(provider.apiStyle) }
    var apiKey by remember { mutableStateOf("") }

    val keyPlaceholder = if (hasKey) "已保存（留空则不修改）" else "sk-..."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 " + provider.label) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text(keyPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "接口风格",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiApiStyle.entries) { style ->
                        FilterChip(
                            selected = apiStyle == style.name,
                            onClick = { apiStyle = style.name },
                            label = { Text(style.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = provider.copy(
                        label = label.trim().ifBlank { provider.label },
                        baseUrl = baseUrl.trim(),
                        model = model.trim(),
                        apiStyle = apiStyle
                    )
                    onSave(updated, apiKey)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (hasKey) {
                    TextButton(onClick = onClearKey) { Text("移除 Key") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
