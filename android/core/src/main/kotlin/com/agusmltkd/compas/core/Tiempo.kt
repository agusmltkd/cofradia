package com.agusmltkd.compas.core

/** "20:05". Acepta minutos > 1440 (madrugada): 1505 → "01:05". */
fun formatHora(minutos: Int): String {
    val m = ((minutos % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

/** "20:05:09" a partir de segundos desde la medianoche del día de salida. */
fun formatHoraSegundos(segundos: Long): String {
    val s = ((segundos % 86_400) + 86_400) % 86_400
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** "+3", "−2", "0" con el signo menos tipográfico. */
fun formatDelta(minutos: Int): String = when {
    minutos > 0 -> "+$minutos"
    minutos < 0 -> "−${-minutos}"
    else -> "0"
}

/** "12:34" → 754. Admite "1:05" y horas de madrugada "25:10". */
fun parseHora(texto: String): Int? {
    val m = Regex("""^\s*(\d{1,2})[:.h](\d{2})\s*$""").find(texto) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h > 47 || min > 59) return null
    return h * 60 + min
}

/** Cuenta atrás compacta para pantalla: "4:05", "0:34", "1:02:10". */
fun formatCuenta(segundos: Long): String {
    val s = kotlin.math.abs(segundos)
    val signo = if (segundos < 0) "−" else ""
    return if (s >= 3600) "%s%d:%02d:%02d".format(signo, s / 3600, (s % 3600) / 60, s % 60)
    else "%s%d:%02d".format(signo, s / 60, s % 60)
}
