package com.agusmltkd.compas.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Datos de ejemplo de una hermandad ficticia. Sirven para probar la app sin
 * código de activación y para que el equipo de revisión de Google Play pueda
 * recorrerla entera.
 */
object Demo {
    @Serializable
    data class Contenido(val hermandad: Hermandad, val itinerarios: List<Itinerario>)

    val json = Json { ignoreUnknownKeys = true }

    val contenido: Contenido by lazy {
        val texto = Demo::class.java.getResourceAsStream("/demo.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString<Contenido>(texto)
    }
}
