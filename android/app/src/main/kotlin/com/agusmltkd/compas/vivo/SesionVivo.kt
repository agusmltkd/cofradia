package com.agusmltkd.compas.vivo

import com.agusmltkd.compas.core.AvisoProgramado
import com.agusmltkd.compas.core.EstadoVivo
import com.agusmltkd.compas.core.HorarioPunto
import com.agusmltkd.compas.core.programarAvisos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Lo que comparten la pantalla y el servicio en vivo (mismo proceso).
 * La pantalla fija la configuración; el servicio publica el estado.
 */
object SesionVivo {
    data class Config(
        val horarios: List<HorarioPunto>,
        val hermandad: String,
        val seccion: String,
        val itinerario: String,
        val retraso: Int,
        /** Epoch (s) de la medianoche del día de salida. */
        val baseDia: Long,
        /** Segundos que se suman al reloj real. ≠ 0 solo en ensayos. */
        val desfase: Long,
        val avisosAutomaticos: Boolean,
        val antelaciones: List<Long>,
        val velocidadVoz: Float,
        val formato24h: Boolean,
    ) {
        val esEnsayo: Boolean get() = desfase != 0L
        val avisos: List<AvisoProgramado> by lazy { programarAvisos(horarios, antelaciones) }

        fun ahora(): Long = System.currentTimeMillis() / 1000 + desfase - baseDia
    }

    val config = MutableStateFlow<Config?>(null)
    private val _estado = MutableStateFlow<EstadoVivo?>(null)
    val estado: StateFlow<EstadoVivo?> = _estado
    val activo = MutableStateFlow(false)

    internal fun publicar(e: EstadoVivo?) {
        _estado.value = e
    }

    /**
     * Medianoche del día de salida. Si son menos de las 6:00 y el itinerario
     * empieza por la tarde, la salida fue AYER (estamos en la madrugada).
     */
    fun baseDia(salidaMinutos: Int, zona: ZoneId = ZoneId.systemDefault()): Long {
        val hoy = LocalDate.now(zona)
        val dia = if (LocalTime.now(zona).hour < 6 && salidaMinutos >= 12 * 60) hoy.minusDays(1) else hoy
        return dia.atStartOfDay(zona).toEpochSecond()
    }
}
