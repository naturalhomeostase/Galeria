package com.galeria.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    // SimpleDateFormat NÃO é seguro para uso simultâneo em várias threads. Como o agrupamento
    // por mês agora roda fora da thread principal (e telas na thread principal também formatam
    // datas), cada formatador é protegido com synchronized.
    private val monthYearFormat = SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR"))
    private val fullDateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR"))

    // Um Calendar por thread, reaproveitado: antes era criado um novo POR FOTO a cada
    // agrupamento (dezenas de milhares de objetos a cada recarga da galeria).
    private val calendars = ThreadLocal.withInitial { Calendar.getInstance() }

    fun monthYearKey(millis: Long): String {
        val cal = calendars.get()!!
        cal.timeZone = TimeZone.getDefault() // acompanha mudança de fuso durante o uso do app
        cal.timeInMillis = millis
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }

    fun monthYearLabel(millis: Long): String {
        val label = synchronized(monthYearFormat) { monthYearFormat.format(Date(millis)) }
        return label.replaceFirstChar { it.uppercase() }
    }

    fun fullDate(millis: Long): String = synchronized(fullDateFormat) { fullDateFormat.format(Date(millis)) }

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
