package com.galeria.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val monthYearFormat = SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR"))
    private val fullDateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR"))

    fun monthYearKey(millis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}"
    }

    fun monthYearLabel(millis: Long): String {
        val label = monthYearFormat.format(Date(millis))
        return label.replaceFirstChar { it.uppercase() }
    }

    fun fullDate(millis: Long): String = fullDateFormat.format(Date(millis))
}
