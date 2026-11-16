package com.agusmltkd.compas.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelo de datos. Es el mismo JSON que guarda el panel web en
 * `itinerarios.datos` y que llega al móvil al sincronizar.
 *
 * Idea central: un itinerario es UNA lista de puntos y cada sección (cruz de
 * guía, paso de Cristo, paso de palio...) recorre esos mismos puntos con sus
 * propios tiempos. Cada tramo entre dos puntos es `andar` + `paron`: los
 * retrasos se recuperan comiéndose parones, no corriendo.
 *
 * Las horas se guardan en minutos desde la medianoche del día de salida.
 * Pueden pasar de 1440 (madrugada del día siguiente).
 */
@Serializable
data class DatosItinerario(
    val puntos: List<Punto>,
    val secciones: List<Seccion>,
)

@Serializable
data class Punto(
    val id: String,
    val nombre: String,
    /** Hora innegociable (palquillo de la Campana, Catedral...). */
    val ancla: Boolean = false,
    /** Referencia visual para el diputado: "azulejo del Cautivo". */
    val nota: String? = null,
    /** Cómo debe pronunciarlo la voz, si el nombre escrito no sirve. */
    val voz: String? = null,
)

@Serializable
data class Seccion(
    val id: String,
    val nombre: String,
    /** Hora de paso por el primer punto, en minutos. */
    val salida: Int,
    /** Siempre puntos.size - 1. */
    val tramos: List<Tramo>,
)

@Serializable
data class Tramo(
    val andar: Int,
    val paron: Int,
    /** true si el reparto andar/parón se estimó al importar y nadie lo ha revisado. */
    val estimado: Boolean = false,
)

/** Un itinerario tal como llega del servidor. */
@Serializable
data class Itinerario(
    val id: String,
    val nombre: String,
    val orden: Int = 0,
    val version: Int = 1,
    val datos: DatosItinerario,
)

@Serializable
data class Hermandad(
    val id: String,
    val slug: String,
    val nombre: String,
    @SerialName("nombre_corto") val nombreCorto: String? = null,
    val ciudad: String = "Sevilla",
    @SerialName("dia_salida") val diaSalida: String? = null,
    val color: String = "#B8862F",
    /** Imagen pública del escudo (Supabase Storage). La app la guarda para usarla sin red. */
    @SerialName("escudo_url") val escudoUrl: String? = null,
)

/** Lo que devuelven `activar` y `sincronizar`, y lo que se guarda en el móvil. */
@Serializable
data class Paquete(
    val token: String,
    val dispositivo: InfoDispositivo,
    val hermandad: Hermandad,
    val itinerarios: List<Itinerario>,
    @SerialName("servidor_ahora") val servidorAhora: Long,
)

@Serializable
data class InfoDispositivo(
    val id: String,
    /** Solo viene en la activación. Después se guarda localmente. */
    val secreto: String? = null,
    val rol: String,
    val seccion: String? = null,
    val etiqueta: String? = null,
)

@Serializable
data class ErrorServidor(val error: String, val mensaje: String)
