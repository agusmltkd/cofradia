package com.agusmltkd.compas.vivo

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.guava.await

/** Lo que usa la pantalla para arrancar, parar y hacer hablar al servicio. */
class ControladorVivo(private val contexto: Context) {
    private var controlador: MediaController? = null

    private suspend fun conectar(): MediaController {
        controlador?.let { if (it.isConnected) return it }
        val token = SessionToken(contexto, ComponentName(contexto, ServicioEnVivo::class.java))
        return MediaController.Builder(contexto, token).buildAsync().await().also { controlador = it }
    }

    suspend fun empezar(config: SesionVivo.Config) {
        SesionVivo.config.value = config
        val c = conectar()
        c.prepare()
        c.play()
    }

    suspend fun hablar() = enviar(ServicioEnVivo.CMD_HABLAR)
    suspend fun proximos() = enviar(ServicioEnVivo.CMD_PROXIMOS)

    suspend fun detener() {
        enviar(ServicioEnVivo.CMD_DETENER)
        soltar()
    }

    private suspend fun enviar(accion: String) {
        conectar().sendCustomCommand(SessionCommand(accion, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun soltar() {
        controlador?.release()
        controlador = null
    }
}
