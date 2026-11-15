package com.xingjian.assistant.ui.settings

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.core.Permissions
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.ThemeMode
import com.xingjian.assistant.ui.components.ConfirmDialog
import com.xingjian.assistant.ui.components.KeyValueRow
import com.xingjian.assistant.ui.components.MessageBanner
import com.xingjian.assistant.ui.components.PermissionDialog
import com.xingjian.assistant.ui.components.SectionCard
import com.xingjian.assistant.ui.components.TagChip
import com.xingjian.assistant.ui.theme.TagImportant
import com.xingjian.assistant.ui.theme.TagSpam
import com.xingjian.assistant.ui.theme.decodeBackground
import java.io.File

@Composable
fun SettingsScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onOpenAi: () -> Unit = {}
) {
    val vm: SettingsViewModel = viewModel(factory = container.viewModelFactory)
    val context = LocalContext.current
    val counts by vm.counts.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val clearing by vm.clearing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val backgroundVersion by vm.backgroundVersion.collectAsStateWithLifecycle()
    val autoLedgerEnabled by vm.autoLedgerEnabled.collectAsStateWithLifecycle()
    val tapTapSession by vm.tapTapSession.collectAsStateWithLifecycle()

    var grantedTick by remember { mutableIntStateOf(0) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showTapTapLogin by remember { mutableStateOf(false) }
    var showTapTapWebLogin by remember { mutableStateOf(false) }

    val smsGranted = remember(grantedTick) { Permissions.hasSms(context) }
    val callGranted = remember(grantedTick) { Permissions.hasCallLog(context) }
    val notificationGranted = remember(grantedTick) { Permissions.hasNotificationListener(context) }
    val accessibilityGranted = remember(grantedTick) { Permissions.hasAccessibility(context) }

    // 从系统设置页返回时重新读取监听/无障碍授权状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                grantedTick++
                vm.refreshTapTapSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundFile = remember(backgroundVersion) { container.settings.backgroundFile() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { grantedTick++ }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.setBackground(uri) }

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
                title = "AI 助手",
                trailing = {
                    IconButton(onClick = onOpenAi) {
                        Icon(Icons.Filled.Star, contentDescription = "AI 配置")
                    }
                }
            ) {}
        }

        item {
            SectionCard(
                title = "TapTap 账号",
                trailing = {
                    Row {
                        TextButton(onClick = { showTapTapLogin = true }) {
                            Text(if (tapTapSession == null) "登录" else "重新登录")
                        }
                        if (tapTapSession != null) {
                            TextButton(onClick = { vm.clearTapTapAccount() }) {
                                Text("退出")
                            }
                        }
                    }
                }
            ) {
                KeyValueRow(
                    "登录态",
                    if (tapTapSession == null) "未登录（不影响游戏下载）" else "已登录"
                )
                Text(
                    text = "登录页由应用自绘，直接调用 TapTap 官方接口，验证成功后自动保存登录态；仅未注册手机号或验证异常时才需要网页登录兜底。Cookie 加密保存在本机，不会上传到任何服务器。游戏下载使用官方签名直链，不依赖此账号。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "权限管理") {
                PermissionRow(
                    name = "读取短信",
                    granted = smsGranted,
                    onRequest = { pendingPermission = "SMS" },
                    onOpenSettings = { Permissions.openAppSettings(context) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                PermissionRow(
                    name = "读取通话记录",
                    granted = callGranted,
                    onRequest = { pendingPermission = "CALL" },
                    onOpenSettings = { Permissions.openAppSettings(context) }
                )
            }
        }

        item {
            SectionCard(
                title = "自动记账",
                trailing = {
                    Switch(
                        checked = autoLedgerEnabled,
                        onCheckedChange = { vm.setAutoLedgerEnabled(it) }
                    )
                }
            ) {
                PermissionRow(
                    name = "通知监听",
                    granted = notificationGranted,
                    onRequest = { Permissions.openNotificationListenerSettings(context) },
                    onOpenSettings = { Permissions.openNotificationListenerSettings(context) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                PermissionRow(
                    name = "无障碍识别",
                    granted = accessibilityGranted,
                    onRequest = { Permissions.openAccessibilitySettings(context) },
                    onOpenSettings = { Permissions.openAccessibilitySettings(context) }
                )
            }
        }

        item {
            SectionCard(title = "主题模式") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries) { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "背景图片",
                trailing = {
                    Row {
                        IconButton(onClick = { imageLauncher.launch("image/*") }) {
                            Icon(Icons.Filled.Add, contentDescription = "选择背景图片")
                        }
                        IconButton(
                            onClick = { vm.clearBackground() },
                            enabled = backgroundFile != null
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "清除背景图片")
                        }
                    }
                }
            ) {
                BackgroundPreview(
                    file = backgroundFile,
                    version = backgroundVersion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }

        item {
            SectionCard(
                title = "本地数据",
                trailing = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = !clearing
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空全部本地数据")
                    }
                }
            ) {
                KeyValueRow("短信", "${counts.sms} 条")
                KeyValueRow("通话记录", "${counts.calls} 条")
                KeyValueRow("邮件缓存", "${counts.mails} 封")
                KeyValueRow("记账记录", "${counts.transactions} 笔")
            }
        }

        item {
            SectionCard(title = "关于") {
                KeyValueRow("应用名称", "星简助手")
                KeyValueRow("版本", "1.0.5")
                KeyValueRow("架构", "MVVM + Clean Architecture")
                KeyValueRow("最低系统版本", "Android 11 (API 30)")
            }
        }
    }

    pendingPermission?.let { kind ->
        val isSms = kind == "SMS"
        PermissionDialog(
            title = if (isSms) "读取短信权限" else "读取通话记录权限",
            description = if (isSms) {
                "用于读取本机短信并在本地完成垃圾/通知/重要分类。短信内容不会自动上传。"
            } else {
                "用于读取本机通话记录并在本地识别骚扰、快递、官方与私人来电。记录不会自动上传。"
            },
            onConfirm = {
                permissionLauncher.launch(
                    if (isSms) android.Manifest.permission.READ_SMS
                    else android.Manifest.permission.READ_CALL_LOG
                )
                pendingPermission = null
            },
            onDismiss = { pendingPermission = null }
        )
    }

    if (showTapTapLogin) {
        TapTapNativeLoginDialog(
            onDismiss = { showTapTapLogin = false },
            onLoggedIn = { cookie ->
                vm.saveTapTapCookie(cookie)
                showTapTapLogin = false
            },
            onUseWebLogin = {
                showTapTapLogin = false
                showTapTapWebLogin = true
            }
        )
    }

    if (showTapTapWebLogin) {
        TapTapWebLoginDialog(
            onDismiss = { showTapTapWebLogin = false },
            onSaveFromWebView = {
                vm.saveTapTapCookieFromWebView()
                showTapTapWebLogin = false
            },
            onSaveCookie = { cookie ->
                vm.saveTapTapCookie(cookie)
                showTapTapWebLogin = false
            }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空全部本地数据",
            text = "将删除本机保存的短信、通话、邮件缓存、记账记录、AI 画像、API Key 与代理配置。此操作不可恢复，确定继续？",
            confirmText = "确认清空",
            onConfirm = {
                vm.clearAllData()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun PermissionRow(
    name: String,
    granted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        TagChip(
            text = if (granted) "已授权" else "未授权",
            color = if (granted) TagImportant else TagSpam
        )
        IconButton(onClick = if (granted) onOpenSettings else onRequest) {
            Icon(
                imageVector = if (granted) Icons.Filled.Settings else Icons.Filled.Lock,
                contentDescription = if (granted) "系统设置" else "去授权"
            )
        }
    }
}

@Composable
private fun BackgroundPreview(file: File?, version: Int, modifier: Modifier = Modifier) {
    var bitmap by remember(version) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(version) {
        bitmap = file?.let { decodeBackground(it) }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
