package com.xingjian.assistant.ui.ledger

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.data.local.entity.CategoryEntity
import com.xingjian.assistant.data.local.entity.TransactionEntity
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.TransactionSource
import com.xingjian.assistant.domain.model.TransactionType
import com.xingjian.assistant.domain.model.enumOrDefault
import com.xingjian.assistant.ui.components.BarChart
import com.xingjian.assistant.ui.components.ChartBar
import com.xingjian.assistant.ui.components.ConfirmDialog
import com.xingjian.assistant.ui.components.EmptyState
import com.xingjian.assistant.ui.components.MessageBanner
import com.xingjian.assistant.ui.components.RatioBarList
import com.xingjian.assistant.ui.components.RatioItem
import com.xingjian.assistant.ui.components.SearchField
import com.xingjian.assistant.ui.components.SectionCard
import com.xingjian.assistant.ui.components.StatCard
import com.xingjian.assistant.ui.components.TagChip
import com.xingjian.assistant.ui.theme.TagImportant
import com.xingjian.assistant.ui.theme.TagNeutral
import com.xingjian.assistant.ui.theme.TagNotice
import com.xingjian.assistant.ui.theme.TagSpam

private val RatioPalette = listOf(TagImportant, TagNotice, TagNeutral, TagSpam)

@Composable
fun LedgerScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: LedgerViewModel = viewModel(factory = container.viewModelFactory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val chartType = filter.type ?: TransactionType.EXPENSE
    val chartItems = remember(state.transactions, chartType) {
        state.transactions.filter { it.type == chartType.name }
    }

    val bars = remember(state.rangeStart, chartItems, filter.range) {
        when (filter.range) {
            LedgerRange.DAY -> emptyList()
            LedgerRange.MONTH -> {
                val days = DateTimeUtils.toLocalDate(state.rangeStart).lengthOfMonth()
                val sums = LongArray(days + 2)
                chartItems.forEach { item ->
                    val day = DateTimeUtils.toLocalDate(item.occurredAt).dayOfMonth
                    sums[day] += item.amountCents
                }
                (1..days).map { day -> ChartBar("$day", sums[day].toFloat()) }
            }
            LedgerRange.YEAR -> {
                val sums = LongArray(14)
                chartItems.forEach { item ->
                    sums[DateTimeUtils.toLocalDate(item.occurredAt).monthValue] += item.amountCents
                }
                (1..12).map { month -> ChartBar("$month 月", sums[month].toFloat()) }
            }
        }
    }

    val ratios = remember(chartItems) {
        val total = chartItems.sumOf { it.amountCents }
        if (total <= 0) {
            emptyList()
        } else {
            chartItems.groupBy { it.categoryName }
                .map { (name, group) ->
                    val amount = group.sumOf { it.amountCents }
                    RatioItem(
                        label = name,
                        amountText = DateTimeUtils.formatMoney(amount),
                        ratio = amount.toFloat() / total.toFloat(),
                        color = Color.Unspecified
                    )
                }
                .sortedByDescending { it.ratio }
                .mapIndexed { index, item ->
                    item.copy(color = RatioPalette[index % RatioPalette.size])
                }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = buildCsv(state.transactions)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                }
                vm.postMessage("已导出 ${state.transactions.size} 条记录")
            }.onFailure { vm.postMessage("导出失败：${it.message ?: "未知错误"}") }
        }
    }

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
                title = "账目概览 · ${state.rangeLabel}",
                trailing = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "记一笔")
                    }
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "本期支出",
                        value = DateTimeUtils.formatMoney(state.expenseCents),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                    StatCard(
                        label = "本期收入",
                        value = DateTimeUtils.formatMoney(state.incomeCents),
                        modifier = Modifier.weight(1f),
                        accent = TagImportant
                    )
                    StatCard(
                        label = "结余",
                        value = DateTimeUtils.formatMoney(state.incomeCents - state.expenseCents),
                        modifier = Modifier.weight(1f),
                        accent = TagNeutral
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerRange.entries.forEach { range ->
                        FilterChip(
                            selected = filter.range == range,
                            onClick = { vm.setRange(range) },
                            label = { Text(range.label) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.type == null,
                        onClick = { vm.setType(null) },
                        label = { Text("全部类型") }
                    )
                    TransactionType.entries.forEach { type ->
                        FilterChip(
                            selected = filter.type == type,
                            onClick = { vm.setType(type) },
                            label = { Text(type.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SearchField(
                    value = filter.query,
                    onValueChange = { vm.setQuery(it) },
                    placeholder = "搜索备注或分类"
                )
            }
        }

        item {
            SectionCard(
                title = when (filter.range) {
                    LedgerRange.DAY -> "今日${chartType.label}分类"
                    LedgerRange.MONTH -> "本月每日${chartType.label}"
                    LedgerRange.YEAR -> "本年每月${chartType.label}"
                },
                trailing = {
                    IconButton(onClick = { showCategories = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "分类管理")
                    }
                }
            ) {
                if (filter.range != LedgerRange.DAY && bars.isNotEmpty()) {
                    BarChart(
                        bars = bars,
                        barColor = if (chartType == TransactionType.EXPENSE) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            TagImportant
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (ratios.isEmpty()) {
                    EmptyState("暂无${chartType.label}记录")
                } else {
                    RatioBarList(items = ratios)
                }
            }
        }

        if (filter.type == null) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter.categoryId == null,
                            onClick = { vm.setCategory(null) },
                            label = { Text("全部分类") }
                        )
                    }
                    val visibleCategories = state.categories.filter { !it.builtIn || it.sortOrder <= 8 }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = filter.categoryId == category.id,
                            onClick = { vm.setCategory(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { exportLauncher.launch("xingjian_ledger.csv") },
                    enabled = state.transactions.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "导出 CSV")
                }
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空记账记录")
                }
            }
        }

        if (state.transactions.isEmpty()) {
            item { SectionCard { EmptyState("暂无记录") } }
        }

        items(state.transactions, key = { it.id }) { item ->
            TransactionCard(item = item, onDelete = { vm.deleteTransaction(item.id) })
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            categories = state.categories,
            onDismiss = { showAdd = false },
            onSave = { amount, type, categoryId, note, dateMillis ->
                vm.addTransaction(amount, type, categoryId, note, dateMillis)
                showAdd = false
            }
        )
    }

    if (showCategories) {
        CategoriesDialog(
            categories = state.categories,
            onAdd = { name, type -> vm.addCategory(name, type) },
            onDelete = { vm.deleteCategory(it) },
            onDismiss = { showCategories = false }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空记账记录",
            text = "将删除本机保存的全部收支记录，分类保持不变。该操作不可恢复。",
            confirmText = "清空",
            onConfirm = {
                vm.clearTransactions()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun TransactionCard(item: TransactionEntity, onDelete: () -> Unit) {
    val isExpense = item.type == TransactionType.EXPENSE.name
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagChip(
                        text = item.categoryName,
                        color = if (isExpense) MaterialTheme.colorScheme.tertiary else TagImportant
                    )
                    if (item.source != TransactionSource.MANUAL.name) {
                        Spacer(Modifier.width(6.dp))
                        TagChip(
                            text = enumOrDefault(item.source, TransactionSource.MANUAL).label,
                            color = TagNotice
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = DateTimeUtils.formatDate(item.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = if (isExpense) "-${DateTimeUtils.formatMoney(item.amountCents)}"
                else "+${DateTimeUtils.formatMoney(item.amountCents)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isExpense) MaterialTheme.colorScheme.tertiary else TagImportant
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (amount: String, type: TransactionType, categoryId: Long, note: String, occurredAt: Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var note by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val visibleCategories = categories.filter { it.type == type.name }
    var categoryId by remember { mutableStateOf(0L) }
    if (categoryId == 0L && visibleCategories.isNotEmpty()) {
        categoryId = visibleCategories.first().id
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { item ->
                        FilterChip(
                            selected = type == item,
                            onClick = {
                                type = item
                                categoryId = 0L
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额（元）") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleCategories, key = { it.id }) { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "日期：${DateTimeUtils.formatDate(dateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "选择日期")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount.isNotBlank() && categoryId != 0L,
                onClick = {
                    onSave(amount, type, categoryId, note, dateMillis)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            // DatePicker 返回 UTC 零点，这里按本地日历日对齐
                            val localDate = java.time.Instant.ofEpochMilli(selected)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                            dateMillis = localDate.atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CategoriesDialog(
    categories: List<CategoryEntity>,
    onAdd: (String, TransactionType) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类管理") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { item ->
                        FilterChip(
                            selected = type == item,
                            onClick = { type = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("新分类名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onAdd(name, type)
                            name = ""
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加分类")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TransactionType.entries.forEach { sectionType ->
                    Text(
                        text = sectionType.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    categories.filter { it.type == sectionType.name }.forEach { category ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (category.builtIn) "内置" else "自定义",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!category.builtIn) {
                                IconButton(onClick = { onDelete(category) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除分类",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun buildCsv(items: List<TransactionEntity>): String {
    val builder = StringBuilder("\uFEFF日期,类型,分类,金额(元),来源,备注\n")
    items.forEach { item ->
        val typeLabel = runCatching { TransactionType.valueOf(item.type).label }.getOrDefault(item.type)
        val sourceLabel = enumOrDefault(item.source, TransactionSource.MANUAL).label
        val note = item.note.replace(",", "，").replace("\n", " ")
        builder.append(
            "${DateTimeUtils.formatDate(item.occurredAt)},$typeLabel,${item.categoryName}," +
                "${DateTimeUtils.formatMoney(item.amountCents, withSymbol = false)},$sourceLabel,$note\n"
        )
    }
    return builder.toString()
}
