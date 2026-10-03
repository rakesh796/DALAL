package com.dalal.scalp

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Session times, expiries and events. Nothing hard-coded as "3:30" or "expiry = Tuesday only":
 * expiries are computed from exchange rules + the holiday list, and shift back on holidays.
 */
object MarketCalendar {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    // Update each year. Remaining 2026 trading holidays (NSE circular FAOP71777).
    val HOLIDAYS: Set<LocalDate> = setOf(
        LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 10),
        LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 25)
    )

    val PRE_OPEN: LocalTime = LocalTime.of(9, 0)
    val OPEN: LocalTime = LocalTime.of(9, 15)
    val CLOSE: LocalTime = LocalTime.of(15, 40) // F&O close since 3 Aug 2026

    private val EVENTS: List<Pair<LocalDate, String>> = listOf(
        LocalDate.of(2026, 10, 7) to "RBI policy decision · in market hours",
        LocalDate.of(2026, 10, 12) to "India CPI · after close",
        LocalDate.of(2026, 10, 14) to "US CPI · after close",
        LocalDate.of(2026, 10, 28) to "Fed decision · night before SENSEX monthly",
        LocalDate.of(2026, 11, 10) to "US CPI · after close",
        LocalDate.of(2026, 11, 12) to "India CPI · after close",
        LocalDate.of(2026, 12, 4) to "RBI policy decision · in market hours",
        LocalDate.of(2026, 12, 9) to "Fed decision · after close",
        LocalDate.of(2026, 12, 10) to "US CPI · after close"
    )

    private val EN = Locale.ENGLISH

    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)
    fun today(): LocalDate = now().toLocalDate()

    fun isTradingDay(d: LocalDate): Boolean =
        d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && d !in HOLIDAYS

    /** Trading mode: trading days, 9:00 AM to 3:40 PM */
    fun tradingModeActive(prefs: Prefs): Boolean {
        if (!prefs.tradingMode) return false
        val n = now()
        val t = n.toLocalTime()
        return isTradingDay(n.toLocalDate()) && !t.isBefore(PRE_OPEN) && t.isBefore(CLOSE)
    }

    fun statusLine(): String {
        val n = now()
        val t = n.toLocalTime()
        if (isTradingDay(n.toLocalDate())) {
            if (!t.isBefore(OPEN) && t.isBefore(CLOSE)) {
                return "OPEN · closes 3:40 PM · ${dur(Duration.between(t, CLOSE))} left"
            }
            if (!t.isBefore(PRE_OPEN) && t.isBefore(OPEN)) {
                return "PRE-OPEN · options open 9:15 AM · in ${dur(Duration.between(t, OPEN))}"
            }
        }
        val next = nextOpen(n)
        return "CLOSED · opens ${next.format(DateTimeFormatter.ofPattern("EEE h:mm a", EN))} · in ${dur(Duration.between(n, next))}"
    }

    private fun nextOpen(n: ZonedDateTime): ZonedDateTime {
        var d = n.toLocalDate()
        if (!(isTradingDay(d) && n.toLocalTime().isBefore(OPEN))) d = d.plusDays(1)
        var guard = 0
        while (!isTradingDay(d) && guard < 30) { d = d.plusDays(1); guard++ }
        return ZonedDateTime.of(d, OPEN, ZONE)
    }

    private fun dur(x: Duration): String {
        val m = x.toMinutes().coerceAtLeast(0)
        val days = m / 1440
        val h = (m % 1440) / 60
        val mm = m % 60
        return when {
            days > 0 -> "${days}d ${h}h"
            h > 0 -> "${h}h ${mm}m"
            else -> "${mm}m"
        }
    }

    private fun shiftBack(d: LocalDate): LocalDate {
        var x = d
        var guard = 0
        while (!isTradingDay(x) && guard < 10) { x = x.minusDays(1); guard++ }
        return x
    }

    fun nextWeekly(from: LocalDate, dow: DayOfWeek): LocalDate {
        var nominal = from.with(TemporalAdjusters.nextOrSame(dow))
        for (i in 0 until 10) {
            val actual = shiftBack(nominal)
            if (!actual.isBefore(from)) return actual
            nominal = nominal.plusWeeks(1)
        }
        return nominal
    }

    fun nextMonthly(from: LocalDate, dow: DayOfWeek): LocalDate {
        var month = YearMonth.from(from)
        for (i in 0 until 6) {
            val nominal = month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(dow))
            val actual = shiftBack(nominal)
            if (!actual.isBefore(from)) return actual
            month = month.plusMonths(1)
        }
        return month.atEndOfMonth()
    }

    data class Badge(val text: String, val hot: Boolean)

    fun expiryBadges(): List<Badge> {
        val today = today()
        val f = DateTimeFormatter.ofPattern("EEE d MMM", EN)
        fun badge(name: String, d: LocalDate, monthly: Boolean): Badge {
            val days = Duration.between(today.atStartOfDay(), d.atStartOfDay()).toDays()
            val dateText = if (d == today) "TODAY" else d.format(f)
            return Badge("$name $dateText${if (monthly) " · monthly" else ""}", days <= 1)
        }
        val niftyW = nextWeekly(today, DayOfWeek.TUESDAY)
        val niftyM = nextMonthly(today, DayOfWeek.TUESDAY)
        val sensexW = nextWeekly(today, DayOfWeek.THURSDAY)
        val sensexM = nextMonthly(today, DayOfWeek.THURSDAY)
        return listOf(
            badge("NIFTY", niftyW, niftyW == niftyM),
            badge("SENSEX", sensexW, sensexW == sensexM),
            badge("BANKNIFTY", niftyM, true) // BANKNIFTY: monthly only, last Tuesday
        )
    }

    fun upcomingEvents(n: Int = 4): List<String> {
        val today = today()
        val f = DateTimeFormatter.ofPattern("EEE d MMM", EN)
        return EVENTS.filter { !it.first.isBefore(today) }
            .sortedBy { it.first }
            .take(n)
            .map { "${it.first.format(f)} · ${it.second}" }
    }
}
