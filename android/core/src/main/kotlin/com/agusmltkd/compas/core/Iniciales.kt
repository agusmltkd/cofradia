package com.agusmltkd.compas.core

private val prefijo = Regex(
    """^(real\s+|ilustre\s+|pontificia\s+|antigua\s+|muy\s+|y\s+)*(hermandad|hdad\.?|cofrad[ií]a)\s+(sacramental\s+)?(de\s+|del\s+)?(la\s+|el\s+|los\s+|las\s+)?""",
    RegexOption.IGNORE_CASE,
)
private val vacias = setOf("de", "del", "la", "el", "los", "las", "y", "e", "nuestra", "nuestro", "señora", "padre")

/**
 * Iniciales para cuando la hermandad no tiene escudo subido:
 * "Hermandad de San Esteban" → "SE", "La Estrella" → "E".
 */
fun iniciales(h: Hermandad): String {
    val base = (h.nombreCorto?.takeIf { it.isNotBlank() } ?: h.nombre).replace(prefijo, "").trim()
    val palabras = base.split(Regex("\\s+")).filter { it.isNotBlank() && it.lowercase() !in vacias }
    val r = palabras.take(2).joinToString("") { it.first().uppercase() }
    return r.ifEmpty { base.take(1).uppercase().ifEmpty { "?" } }
}
