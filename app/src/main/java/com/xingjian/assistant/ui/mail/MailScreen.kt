package com.xingjian.assistant.ui.mail

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.data.local.entity.MailAccountEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.MailCategory
import com.xingjian.assistant.domain.model.ProxyType
import com.xingjian.assistant.ui.components.CategoryPickerDialog
import com.xingjian.assistant.ui.components.EmptyState
import com.xingjian.assistant.ui.components.MessageBanner
import com.xingjian.assistant.ui.components.ProxyConfigDialog
import com.xingjian.assistant.ui.components.SectionCard
import com.xingjian.assistant.ui.components.TagChip
import com.xingjian.assistant.ui.theme.TagImportant
import com.xingjian.assistant.ui.theme.TagNeutral
import com.xingjian.assistant.ui.theme.TagNotice
import com.xingjian.assistant.ui.theme.TagSpam

/**
 * 邮件面板：已并入「通讯」模块，作为通讯页的「邮件」标签内容展示，不再单独占用底部导航。
 */
@Composable
fun MailPane(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: MailViewModel = viewModel(factory = container.viewModelFactory)
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val mails by vm.mails.collectAsStateWithLifecycle()
    val proxyConfig by vm.proxyConfig.collectAsStateWithLifecycle()
    val selectedAccountId by vm.selectedAccountId.collectAsStateWithLifecycle()
    val fetching by vm.fetching.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var showAddAccount by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<MailEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (message != null) {
            item { MessageBanner(message = message!!, onDismiss = { vm.clearMessage() }) }
        }

        item {
            SectionCard(
                title = "邮箱代理",
                trailing = {
                    IconButton(onClick = { showProxyDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "配置代理")
                    }
                }
            ) {
                Text(
                    text = if (proxyConfig.enabled && proxyConfig.type != ProxyType.NONE) {
                        "${proxyConfig.type.label} · ${proxyConfig.host}:${proxyConfig.port}"
                    } else {
                        "直连"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "邮箱账号",
                trailing = {
                    IconButton(onClick = { showAddAccount = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加邮箱账号")
                    }
                }
            ) {
                if (accounts.isEmpty()) {
                    EmptyState("暂无邮箱账号")
                } else {
                    accounts.forEachIndexed { index, account ->
                        AccountRow(
                            account = account,
                            fetching = fetching == account.id,
                            onFetch = { vm.fetch(account.id) },
                            onEnabledChange = { vm.setAccountEnabled(account.id, it) },
                            onDelete = { vm.deleteAccount(account.id) }
                        )
                        if (index != accounts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedAccountId == null,
                            onClick = { vm.selectAccount(null) },
                            label = { Text("全部邮箱") }
                        )
                    }
                    items(accounts, key = { it.id }) { account ->
                        FilterChip(
                            selected = selectedAccountId == account.id,
                            onClick = { vm.selectAccount(account.id) },
                            label = {
                                Text(
                                    account.displayName.ifBlank { account.email },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.clearMails() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空邮件缓存")
                    }
                }
            }

            if (mails.isEmpty()) {
                item { SectionCard { EmptyState("暂无邮件") } }
            }

            items(mails, key = { it.id }) { mail ->
                MailCard(
                    mail = mail,
                    onCategory = { pickerTarget = mail },
                    onStar = { vm.toggleStar(mail) },
                    onDelete = { vm.deleteMail(mail.id) }
                )
            }
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { email, displayName, provider, host, port, ssl, username, password ->
                vm.addAccount(email, displayName, provider, host, port, ssl, username, password)
                showAddAccount = false
            }
        )
    }

    if (showProxyDialog) {
        ProxyConfigDialog(
            title = "邮箱代理配置",
            config = proxyConfig,
            onDismiss = { showProxyDialog = false },
            onSave = { enabled, type, host, port, username, password ->
                vm.saveProxy(enabled, type, host, port, username, password)
                showProxyDialog = false
            }
        )
    }

    pickerTarget?.let { mail ->
        CategoryPickerDialog(
            title = "修改邮件分类",
            options = MailCategory.entries.map { it.name to it.label },
            selected = mail.category,
            onSelect = { name ->
                runCatching { MailCategory.valueOf(name) }.getOrNull()?.let { category ->
                    vm.updateCategory(mail.id, category)
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun AccountRow(
    account: MailAccountEntity,
    fetching: Boolean,
    onFetch: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.displayName.ifBlank { account.email },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = account.enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${account.imapHost}:${account.imapPort}${if (account.useSsl) " · SSL" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onFetch, enabled = !fetching) {
                Icon(Icons.Filled.Refresh, contentDescription = "拉取邮件")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除账号",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MailCard(
    mail: MailEntity,
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
                    text = mail.subject.ifBlank { "(无主题)" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TagChip(
                    text = runCatching { MailCategory.valueOf(mail.category).label }.getOrDefault("普通邮件"),
                    color = mailColor(mail.category)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = mail.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = mail.snippet.ifBlank { "(无正文摘要)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateTimeUtils.formatDateTime(mail.receivedAt),
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
                        contentDescription = "标记邮件",
                        tint = if (mail.starred) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除邮件",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (email: String, displayName: String, provider: String, host: String, port: Int, ssl: Boolean, username: String, password: String) -> Unit
) {
    var presetIndex by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(MailPresets[0].host) }
    var port by remember { mutableStateOf(MailPresets[0].port.toString()) }
    var useSsl by remember { mutableStateOf(MailPresets[0].ssl) }

    val portValue = port.toIntOrNull() ?: 0
    val valid = email.contains("@") && host.isNotBlank() && portValue in 1..65535 && password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加邮箱账号") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MailPresets) { preset ->
                        FilterChip(
                            selected = presetIndex == MailPresets.indexOf(preset),
                            onClick = {
                                presetIndex = MailPresets.indexOf(preset)
                                if (preset.host.isNotBlank()) {
                                    host = preset.host
                                    port = preset.port.toString()
                                    useSsl = preset.ssl
                                }
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱地址") },
                    placeholder = { Text("name@gmail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("登录账号（留空则使用邮箱地址）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码 / 应用专用密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IMAP 服务器") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { input -> port = input.filter { it.isDigit() }.take(5) },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("使用 SSL/TLS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = useSsl, onCheckedChange = { useSsl = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        email.trim(),
                        displayName.trim(),
                        MailPresets[presetIndex].provider,
                        host.trim(),
                        portValue,
                        useSsl,
                        username.trim(),
                        password
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private data class MailPreset(
    val provider: String,
    val label: String,
    val host: String,
    val port: Int,
    val ssl: Boolean
)

private val MailPresets = listOf(
    MailPreset("GMAIL", "Gmail", "imap.gmail.com", 993, true),
    MailPreset("OUTLOOK", "Outlook", "outlook.office365.com", 993, true),
    MailPreset("QQ", "QQ 邮箱", "imap.qq.com", 993, true),
    MailPreset("163", "163 邮箱", "imap.163.com", 993, true),
    MailPreset("OTHER", "自定义", "", 993, true)
)

private fun mailColor(category: String) = when (category) {
    MailCategory.SPAM.name -> TagSpam
    MailCategory.NOTIFICATION.name -> TagNotice
    MailCategory.BILL.name -> TagImportant
    else -> TagNeutral
}
