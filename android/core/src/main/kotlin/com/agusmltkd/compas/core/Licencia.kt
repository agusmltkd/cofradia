package com.agusmltkd.compas.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Token de licencia firmado por el servidor (ECDSA P-256):
 *
 *     base64url(JSON carga) "." base64url(firma r||s)
 *
 * El móvil lo verifica con la clave pública que lleva dentro, sin red. Así
 * funciona el día de la salida aunque la cobertura esté saturada.
 */
@Serializable
data class CargaLicencia(
    val v: Int,
    val kid: String,
    val did: String,
    val hid: String,
    val hs: String,
    val hn: String,
    val rol: String,
    val sec: String? = null,
    val iat: Long,
    val exp: Long,
    val lic: String,
)

sealed interface ResultadoLicencia {
    data class Valida(val carga: CargaLicencia, val diasRestantes: Long) : ResultadoLicencia
    data class Caducada(val carga: CargaLicencia) : ResultadoLicencia
    /** El reloj del móvil está por detrás de lo que ya vimos: alguien lo ha atrasado. */
    data class RelojManipulado(val carga: CargaLicencia) : ResultadoLicencia
    data object FirmaInvalida : ResultadoLicencia
    data object Malformada : ResultadoLicencia
}

class VerificadorLicencia(clavePublicaSpkiBase64: String) {
    private val clave: PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(clavePublicaSpkiBase64)))

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param ahora segundos epoch del reloj del móvil.
     * @param maximoVisto el instante más alto que la app ha visto nunca (del
     *   servidor o del propio reloj). Si `ahora` está muy por detrás, el reloj
     *   se ha atrasado a mano para estirar la licencia.
     */
    fun verificar(token: String, ahora: Long, maximoVisto: Long = 0): ResultadoLicencia {
        val partes = token.split('.')
        if (partes.size != 2) return ResultadoLicencia.Malformada
        val firma = try {
            Base64.getUrlDecoder().decode(partes[1])
        } catch (_: IllegalArgumentException) {
            return ResultadoLicencia.Malformada
        }
        if (firma.size != 64) return ResultadoLicencia.Malformada

        val ok = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(clave)
                update(partes[0].toByteArray(Charsets.US_ASCII))
                verify(rawADer(firma))
            }
        } catch (_: Exception) {
            false
        }
        if (!ok) return ResultadoLicencia.FirmaInvalida

        val carga = try {
            json.decodeFromString<CargaLicencia>(String(Base64.getUrlDecoder().decode(partes[0]), Charsets.UTF_8))
        } catch (_: Exception) {
            return ResultadoLicencia.Malformada
        }
        // Una hora de tolerancia para cambios de hora y relojes algo desajustados.
        if (maximoVisto - ahora > 3600) return ResultadoLicencia.RelojManipulado(carga)
        val referencia = maxOf(ahora, maximoVisto)
        if (referencia >= carga.exp) return ResultadoLicencia.Caducada(carga)
        return ResultadoLicencia.Valida(carga, (carga.exp - referencia) / 86_400)
    }

    companion object {
        /** WebCrypto firma en formato r||s (64 bytes); Java espera DER. */
        fun rawADer(raw: ByteArray): ByteArray {
            fun entero(b: ByteArray): ByteArray {
                val v = BigInteger(1, b).toByteArray() // añade 0x00 si el bit alto está a 1
                return byteArrayOf(0x02, v.size.toByte()) + v
            }
            val r = entero(raw.copyOfRange(0, 32))
            val s = entero(raw.copyOfRange(32, 64))
            val cuerpo = r + s
            return byteArrayOf(0x30, cuerpo.size.toByte()) + cuerpo
        }
    }
}
