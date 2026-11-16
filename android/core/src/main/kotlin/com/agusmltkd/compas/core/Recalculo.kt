package com.agusmltkd.compas.core

/**
 * Recálculo por retraso en la salida.
 *
 * Solo se comprime el recorrido entre la salida y la PRIMERA ancla (el
 * palquillo de la Campana en Sevilla). Desde ahí manda el horario oficial.
 * El retraso se absorbe primero en los parones (dejando un mínimo en cada
 * uno) y solo después acelerando el andar, con un tope. Si hay que acelerar
 * más del tope, el resultado es NO VIABLE: hay que avisar, no correr.
 */
data class OpcionesRecalculo(
    val paronMinimo: Int = 1,
    val aceleracionAndarMaxima: Double = 0.08,
    val derivaCortejoMaxima: Int = 3,
)

data class HorarioPunto(
    val indice: Int,
    val punto: Punto,
    val original: Int,
    val nueva: Int,
) {
    val delta: Int get() = nueva - original
}

data class ResultadoSeccion(
    val seccion: Seccion,
    val viable: Boolean,
    val horarios: List<HorarioPunto>,
    val retraso: Int,
    val recorteDeParones: Int,
    val recorteDeAndar: Int,
    val aceleracionAndar: Double,
    /** Minutos de parón que quedan libres después del recorte. */
    val margenRestante: Int,
    val avisos: List<String>,
) {
    val nuevaSalida: Int get() = horarios.first().nueva
}

data class ResultadoPlan(
    val retraso: Int,
    val viable: Boolean,
    val secciones: List<ResultadoSeccion>,
    val avisosCortejo: List<String>,
)

class DatosInvalidos(mensaje: String) : IllegalArgumentException(mensaje)

/** Índice de la primera ancla. Si no hay ninguna, se usa el último punto. */
fun indiceAncla(datos: DatosItinerario): Int {
    val i = datos.puntos.indexOfFirst { it.ancla }
    if (i == 0) throw DatosInvalidos("El punto de salida no puede ser un punto de horario fijo.")
    return if (i > 0) i else datos.puntos.lastIndex
}

fun horariosOriginales(seccion: Seccion): List<Int> {
    val out = ArrayList<Int>(seccion.tramos.size + 1)
    var t = seccion.salida
    out += t
    for (tr in seccion.tramos) {
        t += tr.andar + tr.paron
        out += t
    }
    return out
}

fun recalcularSeccion(
    datos: DatosItinerario,
    seccion: Seccion,
    retraso: Int,
    op: OpcionesRecalculo = OpcionesRecalculo(),
): ResultadoSeccion {
    if (seccion.tramos.size != datos.puntos.size - 1) {
        throw DatosInvalidos(
            "«${seccion.nombre}» tiene ${seccion.tramos.size} tramos y el itinerario ${datos.puntos.size} puntos.",
        )
    }
    val a = indiceAncla(datos)
    val original = horariosOriginales(seccion)
    val d = maxOf(0, retraso)
    val avisos = mutableListOf<String>()

    val region = seccion.tramos.subList(0, a)
    val paronDisponible = region.map { maxOf(0, it.paron - op.paronMinimo) }
    val capacidad = paronDisponible.sum()
    val andarTotal = region.sumOf { it.andar }

    val deParones = minOf(d, capacidad)
    val cortesParon = repartirConTope(deParones, paronDisponible)
    val deAndar = d - deParones

    var cortesAndar = List(region.size) { 0 }
    var aceleracion = 0.0
    var viable = true

    if (deAndar > 0) {
        if (andarTotal <= 0) {
            viable = false
            avisos += "No queda de dónde recortar: los parones están al mínimo."
        } else {
            aceleracion = deAndar.toDouble() / andarTotal
            // Ningún tramo de andar baja de la mitad, pase lo que pase.
            cortesAndar = repartirConTope(deAndar, region.map { it.andar / 2 })
            val conseguido = cortesAndar.sum()
            if (conseguido < deAndar) {
                viable = false
                avisos += "Ni recortando al máximo se llega: faltan ${deAndar - conseguido} min."
            }
            if (aceleracion > op.aceleracionAndarMaxima) {
                viable = false
                avisos += "Habría que andar un %.1f%% más rápido hasta el punto fijo (máximo %d%%). Esto no se recupera andando: hay que avisar."
                    .format(aceleracion * 100, (op.aceleracionAndarMaxima * 100).toInt())
            }
        }
    }

    val nueva = IntArray(original.size)
    nueva[0] = original[0] + d
    for (i in region.indices) {
        nueva[i + 1] = nueva[i] + (region[i].andar - cortesAndar[i]) + (region[i].paron - cortesParon[i])
    }
    for (i in a until original.size) nueva[i] = original[i]

    val margen = capacidad - deParones
    if (viable && margen < 10) {
        avisos += "Solo quedan $margen min de colchón en los parones."
    }
    if (region.any { it.estimado }) {
        avisos += "Hay tramos con el reparto andar/parón estimado. Revísalos con el capataz."
    }

    return ResultadoSeccion(
        seccion = seccion,
        viable = viable,
        horarios = datos.puntos.mapIndexed { i, p -> HorarioPunto(i, p, original[i], nueva[i]) },
        retraso = d,
        recorteDeParones = deParones,
        recorteDeAndar = deAndar,
        aceleracionAndar = aceleracion,
        margenRestante = margen,
        avisos = avisos,
    )
}

fun recalcularPlan(
    datos: DatosItinerario,
    retraso: Int,
    op: OpcionesRecalculo = OpcionesRecalculo(),
): ResultadoPlan {
    val secciones = datos.secciones.map { recalcularSeccion(datos, it, retraso, op) }
    val a = indiceAncla(datos)
    val avisosCortejo = mutableListOf<String>()

    // El cortejo tiene longitud física: si la separación entre dos secciones
    // cambia mucho al recalcular, el horario es incoherente sobre el terreno.
    for (s in 0 until secciones.size - 1) {
        val x = secciones[s]
        val y = secciones[s + 1]
        var peor = 0
        var donde = ""
        for (i in 0..a) {
            val antes = y.horarios[i].original - x.horarios[i].original
            val ahora = y.horarios[i].nueva - x.horarios[i].nueva
            val deriva = kotlin.math.abs(ahora - antes)
            if (deriva > peor) {
                peor = deriva
                donde = datos.puntos[i].nombre
            }
        }
        if (peor > op.derivaCortejoMaxima) {
            avisosCortejo += "La separación entre ${x.seccion.nombre} y ${y.seccion.nombre} varía $peor min (en $donde)."
        }
    }
    return ResultadoPlan(maxOf(0, retraso), secciones.all { it.viable }, secciones, avisosCortejo)
}

/** "¿Cuánto puedo perder antes de no llegar?" — búsqueda binaria. */
fun retrasoMaximoAbsorbible(datos: DatosItinerario, op: OpcionesRecalculo = OpcionesRecalculo()): Int {
    var lo = 0
    var hi = 240
    if (recalcularPlan(datos, hi, op).viable) return hi
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (recalcularPlan(datos, mid, op).viable) lo = mid else hi = mid - 1
    }
    return lo
}

fun retrasoMaximoSeccion(datos: DatosItinerario, seccion: Seccion, op: OpcionesRecalculo = OpcionesRecalculo()): Int {
    var lo = 0
    var hi = 240
    if (recalcularSeccion(datos, seccion, hi, op).viable) return hi
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (recalcularSeccion(datos, seccion, mid, op).viable) lo = mid else hi = mid - 1
    }
    return lo
}
