package com.agusmltkd.compas.core

/**
 * Modo en vivo: dónde debería estar la sección AHORA y qué tiene que decir la
 * voz. Todo en segundos desde la medianoche del día de salida (puede pasar de
 * 86 400 en la madrugada).
 */
enum class Fase { ANTES_DE_SALIR, EN_CAMINO, TERMINADO }

data class EstadoVivo(
    val fase: Fase,
    val ahora: Long,
    /** Punto en el que se debería estar justo ahora (± [VENTANA_EN_PUNTO] s), si lo hay. */
    val enPunto: HorarioPunto?,
    /** Próximo punto por delante. null si ya se pasó el último. */
    val siguiente: HorarioPunto?,
    val segundosHastaSiguiente: Long,
    /** Último punto cuya hora ya pasó. */
    val anterior: HorarioPunto?,
    /** 0..1 dentro del tramo actual. */
    val progresoTramo: Float,
    val restantes: List<HorarioPunto>,
)

const val VENTANA_EN_PUNTO = 20L

fun estadoVivo(horarios: List<HorarioPunto>, ahora: Long): EstadoVivo {
    require(horarios.isNotEmpty())
    val t = horarios.map { it.nueva * 60L }
    val iSig = t.indexOfFirst { it > ahora }
    val siguiente = if (iSig >= 0) horarios[iSig] else null
    val anterior = when {
        iSig == -1 -> horarios.last()
        iSig == 0 -> null
        else -> horarios[iSig - 1]
    }
    val enPunto = horarios.indices
        .filter { kotlin.math.abs(t[it] - ahora) <= VENTANA_EN_PUNTO }
        .minByOrNull { kotlin.math.abs(t[it] - ahora) }
        ?.let { horarios[it] }
    val fase = when {
        ahora < t.first() - VENTANA_EN_PUNTO -> Fase.ANTES_DE_SALIR
        iSig == -1 && ahora > t.last() + VENTANA_EN_PUNTO -> Fase.TERMINADO
        else -> Fase.EN_CAMINO
    }
    val progreso = if (anterior != null && siguiente != null) {
        val a = anterior.nueva * 60L
        val b = siguiente.nueva * 60L
        if (b > a) ((ahora - a).toFloat() / (b - a)).coerceIn(0f, 1f) else 1f
    } else if (siguiente == null) 1f else 0f

    return EstadoVivo(
        fase = fase,
        ahora = ahora,
        enPunto = enPunto,
        siguiente = siguiente,
        segundosHastaSiguiente = if (siguiente != null) siguiente.nueva * 60L - ahora else 0,
        anterior = anterior,
        progresoTramo = progreso,
        restantes = if (iSig >= 0) horarios.subList(iSig, horarios.size) else emptyList(),
    )
}

/** Textos que dice la voz. */
object Locucion {
    private val horas12 = listOf(
        "doce", "una", "dos", "tres", "cuatro", "cinco", "seis",
        "siete", "ocho", "nueve", "diez", "once",
    )

    /** "las ocho y 14", "la una y media", "las nueve en punto". */
    fun horaCorta(segundos: Long, formato24h: Boolean = false): String {
        val s = ((segundos % 86_400) + 86_400) % 86_400
        val h = (s / 3600).toInt()
        val m = ((s % 3600) / 60).toInt()
        val nombre = if (formato24h) {
            if (h == 1) "la una" else "las $h"
        } else {
            val h12 = h % 12
            if (h12 == 1) "la una" else "las ${horas12[h12]}"
        }
        val minutos = when (m) {
            0 -> "en punto"
            15 -> "y cuarto"
            30 -> "y media"
            else -> "y $m"
        }
        return "$nombre $minutos"
    }

    /** "Son las ocho y 14", "Es la una y media". */
    fun hora(segundos: Long, formato24h: Boolean = false): String {
        val c = horaCorta(segundos, formato24h)
        return if (c.startsWith("la ")) "Es $c" else "Son $c"
    }

    /** "34 segundos", "3 minutos y 20 segundos", "12 minutos", "1 hora y 5 minutos". */
    fun duracion(segundos: Long): String {
        val s = maxOf(0, segundos)
        fun plural(n: Long, uno: String, varios: String) = if (n == 1L) "1 $uno" else "$n $varios"
        return when {
            s < 60 -> plural(s, "segundo", "segundos")
            s < 600 -> {
                val m = s / 60
                val r = s % 60
                if (r == 0L) plural(m, "minuto", "minutos")
                else "${plural(m, "minuto", "minutos")} y ${plural(r, "segundo", "segundos")}"
            }
            s < 3600 -> plural(s / 60, "minuto", "minutos")
            else -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0L) plural(h, "hora", "horas")
                else "${plural(h, "hora", "horas")} y ${plural(m, "minuto", "minutos")}"
            }
        }
    }

    fun nombreHablado(p: Punto): String {
        p.voz?.takeIf { it.isNotBlank() }?.let { return it }
        // Los nombres vienen muchas veces en mayúsculas desde Excel: la voz
        // lee mejor "Plaza de San Leandro" que "PLAZA DE SAN LEANDRO".
        val letras = p.nombre.filter { it.isLetter() }
        val enMayus = letras.isNotEmpty() && letras.count { it.isUpperCase() } > letras.length * 0.7
        val base = if (enMayus) p.nombre.lowercase().replaceFirstChar { it.uppercase() } else p.nombre
        return base.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * La frase principal. Ejemplos:
     *  - "Son las ocho y 14. Deberías estar en Laraña."
     *  - "Son las ocho y 14 y en 34 segundos deberías estar en Laraña."
     *  - "Son las siete y 42. La salida es dentro de 18 minutos."
     */
    fun frase(e: EstadoVivo, formato24h: Boolean = false): String {
        val hora = hora(e.ahora, formato24h)
        return when (e.fase) {
            Fase.ANTES_DE_SALIR -> {
                val sig = e.siguiente!!
                "$hora. La salida es a ${horaCorta(sig.nueva * 60L, formato24h)}, dentro de ${duracion(e.segundosHastaSiguiente)}."
            }
            Fase.TERMINADO -> "$hora. Itinerario terminado."
            Fase.EN_CAMINO -> {
                val p = e.enPunto
                if (p != null) {
                    val fijo = if (p.punto.ancla) " Punto de horario fijo." else ""
                    "$hora. Deberías estar en ${nombreHablado(p.punto)}.$fijo"
                } else {
                    val sig = e.siguiente!!
                    val fijo = if (sig.punto.ancla) " Es punto de horario fijo." else ""
                    "$hora y en ${duracion(e.segundosHastaSiguiente)} deberías estar en ${nombreHablado(sig.punto)}.$fijo"
                }
            }
        }
    }

    /** Doble pulsación: los dos siguientes puntos con su hora. */
    fun proximos(e: EstadoVivo, n: Int = 2, formato24h: Boolean = false): String {
        val lista = e.restantes.filter { it != e.enPunto }.take(n)
        if (lista.isEmpty()) return "No quedan más puntos."
        return "Próximos: " + lista.joinToString(". ") {
            "${nombreHablado(it.punto)}, a ${horaCorta(it.nueva * 60L, formato24h)}"
        } + "."
    }
}

/** Un aviso automático: a esta hora, la voz habla sola. */
data class AvisoProgramado(val instante: Long, val indicePunto: Int, val antelacion: Long)

/**
 * Programa los avisos automáticos para cada punto: por ejemplo con
 * antelaciones [120, 0] se avisa dos minutos antes y al llegar.
 */
fun programarAvisos(horarios: List<HorarioPunto>, antelaciones: List<Long>): List<AvisoProgramado> =
    horarios.flatMapIndexed { i, h ->
        antelaciones.distinct().map { AvisoProgramado(h.nueva * 60L - it, i, it) }
    }.sortedBy { it.instante }

/**
 * Avisos que tocan entre el tick anterior y este. Si el móvil estuvo dormido
 * y el aviso tiene más de [caducidad] segundos, se descarta: decir tarde
 * "en 2 minutos" es peor que no decir nada.
 */
fun avisosPendientes(
    avisos: List<AvisoProgramado>,
    desde: Long,
    hasta: Long,
    caducidad: Long = 15,
): List<AvisoProgramado> = avisos.filter { it.instante in (desde + 1)..hasta && hasta - it.instante <= caducidad }
