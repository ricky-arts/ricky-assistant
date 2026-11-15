package com.xingjian.assistant.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 统一时间格式化与区间计算（全部基于设备本地时区） */
object DateTimeUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun formatDate(epochMillis: Long): String = toLocalDate(epochMillis).format(dateFormatter)

    fun formatMonth(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(monthFormatter)

    fun formatDateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFormatter)

    fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    fun startOfDay(epochMillis: Long): Long =
        toLocalDate(epochMillis).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(epochMillis: Long): Long =
        toLocalDate(epochMillis).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun startOfMonth(epochMillis: Long): Long =
        toLocalDate(epochMillis).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfYear(epochMillis: Long): Long =
        toLocalDate(epochMillis).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun todayStart(): Long = startOfDay(System.currentTimeMillis())

    fun daysAgoStart(days: Int): Long =
        LocalDate.now(zone).minusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 金额（分）转显示文本 */
    fun formatMoney(cents: Long, withSymbol: Boolean = true): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val text = "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        val prefix = if (withSymbol) "¥" else ""
        return if (negative) "-$prefix$text" else "$prefix$text"
    }
}
