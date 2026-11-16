package com.agusmltkd.compas.vivo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Voz del sistema (Text-to-Speech). Pide foco de audio "transitorio con
 * atenuación" mientras habla, así si el diputado lleva algo sonando lo baja en
 * vez de cortarlo.
 */
class Locutor(contexto: Context) : TextToSpeech.OnInitListener {
    private val audio = contexto.getSystemService(AudioManager::class.java)
    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val foco = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(atributos)
        .build()

    private var listo = false
    private var pendiente: String? = null
    var velocidad: Float = 1f

    private val tts = TextToSpeech(contexto.applicationContext, this)

    override fun onInit(estado: Int) {
        if (estado != TextToSpeech.SUCCESS) return
        val es = Locale.forLanguageTag("es-ES")
        if (tts.isLanguageAvailable(es) >= TextToSpeech.LANG_AVAILABLE) tts.language = es
        tts.setAudioAttributes(atributos)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (!tts.isSpeaking) audio.abandonAudioFocusRequest(foco)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                audio.abandonAudioFocusRequest(foco)
            }
        })
        listo = true
        pendiente?.let { decir(it) }
        pendiente = null
    }

    /** Dice el texto cortando lo que estuviera diciendo. */
    fun decir(texto: String) {
        if (!listo) {
            pendiente = texto
            return
        }
        tts.setSpeechRate(velocidad)
        audio.requestAudioFocus(foco)
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    fun cerrar() {
        tts.stop()
        tts.shutdown()
        audio.abandonAudioFocusRequest(foco)
    }
}
