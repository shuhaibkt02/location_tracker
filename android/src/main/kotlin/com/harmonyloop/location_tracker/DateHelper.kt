package com.harmonyloop.location_tracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateHelper {
    private const val DATE_PATTERN = "yyyy-MM-dd"

    fun getFormattedDate(date: Date = Date()): String {
        val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US)
        return sdf.format(date)
    }

    fun getFormattedDate(calendar: Calendar): String {
        return getFormattedDate(calendar.time)
    }

    fun getFormattedDate(timestampMillis: Long): String {
        return getFormattedDate(Date(timestampMillis))
    }

    fun getYesterdayDate(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return getFormattedDate(cal)
    }
}
