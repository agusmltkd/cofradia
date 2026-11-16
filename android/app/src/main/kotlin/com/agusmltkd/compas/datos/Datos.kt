package com.agusmltkd.compas.datos

import android.content.Context
import android.os.Build
import com.agusmltkd.compas.BuildConfig
import com.agusmltkd.compas.core.Demo
import com.agusmltkd.compas.core.ErrorServidor
import com.agusmltkd.compas.core.Hermandad
import com.agusmltkd.compas.core.Itinerario
import com.agusmltkd.compas.core.Paquete
import com.agusmltkd.compas.core.ResultadoLicencia
import com.agusmltkd.compas.core.VerificadorLicencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

val JsonCompas = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Estado de la licencia tal como lo ve la interfaz. */
sealed interface EstadoLicencia {
    data class Activa(val diasRestantes: Long, val finLicencia: String) : EstadoLicencia
    /** Han pasado 30 días sin red. Los datos siguen ahí, pero no se puede salir en vivo. */
    data object SinRenovar : EstadoLicencia
    data object RelojManipulado : EstadoLicencia
    data object Invalida : EstadoLicencia
    data object Demo : EstadoLicencia
}

data class Sesion(
    val hermandad: Hermandad,
    val itinerarios: List<Itinerario>,
    val rol: String,
    val seccionPreferida: String?,
    val etiqueta: String?,
    val licencia: EstadoLicencia,
    val esDemo: Boolean,
    val ultimaSincronizacion: Long,
) {
    val puedeSalirEnVivo: Boolean get() = licencia is EstadoLicencia.Activa || licencia is EstadoLicencia.Demo
}

/** Persistencia local: el último paquete recibido y las credenciales del móvil. */
class Almacen(private val contexto: Context) {
    private val prefs = contexto.getSharedPreferences("compas", Context.MODE_PRIVATE)
    private val fichero = File(contexto.filesDir, "paquete.json")

    var dispositivoId: String?
        get() = prefs.getString("dispositivo_id", null)
        set(v) = prefs.edit().putString("dispositivo_id", v).apply()
    var secreto: String?
        get() = prefs.getString("secreto", null)
        set(v) = prefs.edit().putString("secreto", v).apply()
    var esDemo: Boolean
        get() = prefs.getBoolean("demo", false)
        set(v) = prefs.edit().putBoolean("demo", v).apply()
    var ultimaSincronizacion: Long
        get() = prefs.getLong("ultima_sync", 0)
        set(v) = prefs.edit().putLong("ultima_sync", v).apply()

    /** Mayor instante visto (servidor o reloj): detecta relojes atrasados a mano. */
    var maximoVisto: Long
        get() = prefs.getLong("maximo_visto", 0)
        set(v) = prefs.edit().putLong("maximo_visto", maxOf(v, prefs.getLong("maximo_visto", 0))).apply()

    fun guardarPaquete(p: Paquete) {
        val sinSecreto = p.copy(dispositivo = p.dispositivo.copy(secreto = null))
        val tmp = File(fichero.parentFile, "paquete.json.tmp")
        tmp.writeText(JsonCompas.encodeToString(Paquete.serializer(), sinSecreto))
        tmp.renameTo(fichero)
    }

    fun leerPaquete(): Paquete? = try {
        if (fichero.exists()) JsonCompas.decodeFromString(Paquete.serializer(), fichero.readText()) else null
    } catch (_: Exception) {
        null
    }

    fun borrarTodo() {
        prefs.edit().clear().apply()
        fichero.delete()
    }

    // ---- ajustes
    var avisosAutomaticos: Boolean
        get() = prefs.getBoolean("avisos_auto", true)
        set(v) = prefs.edit().putBoolean("avisos_auto", v).apply()
    var avisoPrevio: Boolean
        get() = prefs.getBoolean("aviso_previo", true)
        set(v) = prefs.edit().putBoolean("aviso_previo", v).apply()
    var antelacionSegundos: Int
        get() = prefs.getInt("antelacion", 120)
        set(v) = prefs.edit().putInt("antelacion", v).apply()
    var avisoAlLlegar: Boolean
        get() = prefs.getBoolean("aviso_llegar", true)
        set(v) = prefs.edit().putBoolean("aviso_llegar", v).apply()
    var velocidadVoz: Float
        get() = prefs.getFloat("velocidad_voz", 1.0f)
        set(v) = prefs.edit().putFloat("velocidad_voz", v).apply()
    var formato24h: Boolean
        get() = prefs.getBoolean("formato_24h", false)
        set(v) = prefs.edit().putBoolean("formato_24h", v).apply()
    var temaOscuro: Int // 0 sistema, 1 claro, 2 oscuro
        get() = prefs.getInt("tema", 0)
        set(v) = prefs.edit().putInt("tema", v).apply()
}

class ErrorApi(val codigo: String, mensaje: String) : Exception(mensaje)

/** Cliente de las Edge Functions `activar` y `sincronizar`. */
class Api {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val tipo = "application/json; charset=utf-8".toMediaType()

    private suspend fun post(funcion: String, cuerpo: String): Paquete = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/functions/v1/$funcion")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .post(cuerpo.toRequestBody(tipo))
            .build()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw ErrorApi("red", "Sin conexión. Inténtalo cuando tengas cobertura.")
        }
        resp.use {
            val texto = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val err = runCatching { JsonCompas.decodeFromString(ErrorServidor.serializer(), texto) }.getOrNull()
                throw ErrorApi(err?.error ?: "http_${it.code}", err?.mensaje ?: "El servidor respondió con un error (${it.code}).")
            }
            JsonCompas.decodeFromString(Paquete.serializer(), texto)
        }
    }

    suspend fun activar(codigo: String): Paquete = post(
        "activar",
        buildJsonObject {
            put("codigo", codigo)
            put("nombre", "${Build.MANUFACTURER} ${Build.MODEL}".take(60))
            put("modelo", Build.MODEL)
            put("version_app", BuildConfig.VERSION_NAME)
        }.toString(),
    )

    suspend fun sincronizar(id: String, secreto: String): Paquete = post(
        "sincronizar",
        buildJsonObject {
            put("dispositivo_id", id)
            put("secreto", secreto)
            put("version_app", BuildConfig.VERSION_NAME)
        }.toString(),
    )
}

/** Une almacén, API y licencia. La interfaz solo habla con esto. */
class Repositorio(private val almacen: Almacen, private val api: Api) {
    private val verificador = VerificadorLicencia(BuildConfig.CLAVE_PUBLICA_LICENCIA)

    val ajustes: Almacen get() = almacen

    private fun ahora() = System.currentTimeMillis() / 1000

    fun sesionGuardada(): Sesion? {
        if (almacen.esDemo) return sesionDemo()
        val p = almacen.leerPaquete() ?: return null
        if (almacen.dispositivoId == null) return null
        return sesionDe(p)
    }

    private fun sesionDe(p: Paquete): Sesion {
        val ahora = ahora()
        val estado = when (val r = verificador.verificar(p.token, ahora, almacen.maximoVisto)) {
            is ResultadoLicencia.Valida -> {
                almacen.maximoVisto = ahora
                EstadoLicencia.Activa(r.diasRestantes, r.carga.lic)
            }
            is ResultadoLicencia.Caducada -> EstadoLicencia.SinRenovar
            is ResultadoLicencia.RelojManipulado -> EstadoLicencia.RelojManipulado
            else -> EstadoLicencia.Invalida
        }
        return Sesion(
            hermandad = p.hermandad,
            itinerarios = p.itinerarios.sortedBy { it.orden },
            rol = p.dispositivo.rol,
            seccionPreferida = p.dispositivo.seccion,
            etiqueta = p.dispositivo.etiqueta,
            licencia = estado,
            esDemo = false,
            ultimaSincronizacion = almacen.ultimaSincronizacion,
        )
    }

    fun sesionDemo(): Sesion {
        val c = Demo.contenido
        return Sesion(c.hermandad, c.itinerarios, "diputado_mayor", null, "Modo demostración", EstadoLicencia.Demo, true, 0)
    }

    fun entrarDemo(): Sesion {
        almacen.esDemo = true
        return sesionDemo()
    }

    suspend fun activar(codigo: String): Sesion {
        val p = api.activar(codigo)
        almacen.esDemo = false
        almacen.dispositivoId = p.dispositivo.id
        almacen.secreto = p.dispositivo.secreto ?: throw ErrorApi("interno", "El servidor no devolvió credenciales.")
        almacen.maximoVisto = p.servidorAhora
        almacen.ultimaSincronizacion = ahora()
        almacen.guardarPaquete(p)
        return sesionDe(p)
    }

    /**
     * Renueva la licencia y descarga los itinerarios. Si no hay red, devuelve
     * la sesión guardada sin tocar nada: la app sigue funcionando.
     */
    suspend fun sincronizar(): Sesion? {
        if (almacen.esDemo) return sesionDemo()
        val id = almacen.dispositivoId ?: return null
        val secreto = almacen.secreto ?: return null
        val p = api.sincronizar(id, secreto)
        almacen.maximoVisto = p.servidorAhora
        almacen.ultimaSincronizacion = ahora()
        almacen.guardarPaquete(p)
        return sesionDe(p)
    }

    fun salir() = almacen.borrarTodo()
}
