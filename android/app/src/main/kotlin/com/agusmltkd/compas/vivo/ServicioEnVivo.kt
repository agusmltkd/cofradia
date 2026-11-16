package com.agusmltkd.compas.vivo

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.agusmltkd.compas.R
import com.agusmltkd.compas.core.Fase
import com.agusmltkd.compas.core.Locucion
import com.agusmltkd.compas.core.avisosPendientes
import com.agusmltkd.compas.core.estadoVivo
import com.agusmltkd.compas.core.formatHora
import com.agusmltkd.compas.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio del modo en vivo.
 *
 * Cómo llega el botón de los auriculares con la pantalla apagada: Android
 * entrega los botones multimedia a la sesión que está reproduciendo. Así que
 * reproducimos un audio en silencio en bucle y nos quedamos con la sesión.
 * El reproductor está envuelto en [Botonera], que convierte "pausa" y
 * "siguiente" en "habla".
 *
 *   1 pulsación  → hora y siguiente punto
 *   2 pulsaciones → los dos próximos puntos con su hora
 *
 * Además, si están activados, da avisos automáticos antes de cada punto.
 */
@OptIn(UnstableApi::class)
class ServicioEnVivo : MediaSessionService() {

    companion object {
        const val CMD_HABLAR = "compas.HABLAR"
        const val CMD_PROXIMOS = "compas.PROXIMOS"
        const val CMD_DETENER = "compas.DETENER"
    }

    private var sesion: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private lateinit var locutor: Locutor
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ultimoTitulo = ""

    override fun onCreate() {
        super.onCreate()
        locutor = Locutor(this)

        exo = ExoPlayer.Builder(this)
            // handleAudioFocus = false: la voz no debe pausar el silencio, ni al revés.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                false,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.setMediaItem(elemento("Modo en vivo", "Preparando…"))
        exo.prepare()

        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        sesion = MediaSession.Builder(this, Botonera(exo, ::hablar, ::hablarProximos))
            .setSessionActivity(abrirApp)
            .setCallback(Llamadas())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.canal_vivo)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notificacion) },
        )

        SesionVivo.activo.value = true
        ambito.launch { bucle() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    override fun onDestroy() {
        ambito.cancel()
        SesionVivo.activo.value = false
        SesionVivo.publicar(null)
        sesion?.run {
            player.release()
            release()
        }
        sesion = null
        locutor.cerrar()
        super.onDestroy()
    }

    private suspend fun bucle() {
        var anterior: Long? = null
        while (ambito.isActive) {
            val cfg = SesionVivo.config.value
            if (cfg != null) {
                locutor.velocidad = cfg.velocidadVoz
                val ahora = cfg.ahora()
                val e = estadoVivo(cfg.horarios, ahora)
                SesionVivo.publicar(e)

                val desde = anterior
                if (cfg.avisosAutomaticos && desde != null && ahora > desde) {
                    // Si coinciden varios avisos en el mismo tick, basta con decirlo una vez.
                    if (avisosPendientes(cfg.avisos, desde, ahora).isNotEmpty()) {
                        locutor.decir(Locucion.frase(e, cfg.formato24h))
                    }
                }
                anterior = ahora
                actualizarNotificacion(e.fase, e.siguiente?.punto?.nombre, e.siguiente?.nueva, e.segundosHastaSiguiente, cfg)
            }
            delay(250)
        }
    }

    private fun actualizarNotificacion(fase: Fase, siguiente: String?, hora: Int?, segundos: Long, cfg: SesionVivo.Config) {
        val titulo = when (fase) {
            Fase.TERMINADO -> "Itinerario terminado"
            else -> siguiente ?: "—"
        }
        // Granularidad de un minuto para no reconstruir la notificación cada segundo.
        val sub = when (fase) {
            Fase.ANTES_DE_SALIR -> "Salida a las ${formatHora(hora ?: 0)} · ${cfg.seccion}"
            Fase.EN_CAMINO -> "A las ${formatHora(hora ?: 0)} · faltan ${(segundos + 59) / 60} min · ${cfg.seccion}"
            Fase.TERMINADO -> cfg.seccion
        }
        val clave = "$titulo|$sub"
        if (clave == ultimoTitulo) return
        ultimoTitulo = clave
        exo.replaceMediaItem(0, elemento(titulo, sub))
    }

    private fun elemento(titulo: String, subtitulo: String) = MediaItem.Builder()
        .setMediaId("vivo")
        .setUri(RawResourceDataSource.buildRawResourceUri(R.raw.silencio))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(titulo)
                .setArtist(subtitulo)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()

    private fun hablar() {
        val cfg = SesionVivo.config.value ?: return
        locutor.decir(Locucion.frase(estadoVivo(cfg.horarios, cfg.ahora()), cfg.formato24h))
    }

    private fun hablarProximos() {
        val cfg = SesionVivo.config.value ?: return
        locutor.decir(Locucion.proximos(estadoVivo(cfg.horarios, cfg.ahora()), 2, cfg.formato24h))
    }

    private fun detener() {
        SesionVivo.config.value = null
        exo.stop()
        exo.clearMediaItems()
        stopSelf()
    }

    private inner class Llamadas : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val comandos = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_HABLAR, Bundle.EMPTY))
                .add(SessionCommand(CMD_PROXIMOS, Bundle.EMPTY))
                .add(SessionCommand(CMD_DETENER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(comandos)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_HABLAR -> hablar()
                CMD_PROXIMOS -> hablarProximos()
                CMD_DETENER -> detener()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}

/**
 * Reproductor que nunca se pausa: cada "pausa" o "play" que llega del botón de
 * los auriculares (o de la notificación) se convierte en una locución.
 */
@OptIn(UnstableApi::class)
private class Botonera(
    private val real: ExoPlayer,
    private val pulsacion: () -> Unit,
    private val doble: () -> Unit,
) : ForwardingPlayer(real) {

    override fun play() {
        if (real.playWhenReady) pulsacion() else super.play()
    }

    override fun pause() = pulsacion()

    override fun seekToNext() = doble()
    override fun seekToNextMediaItem() = doble()
    override fun seekToPrevious() = pulsacion()
    override fun seekToPreviousMediaItem() = pulsacion()

    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
        .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
}
