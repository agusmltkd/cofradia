package com.agusmltkd.compas.core

/**
 * Reparte `total` minutos enteros entre varias ranuras, en proporción a su
 * capacidad y sin pasarse del tope de ninguna.
 *
 * Garantía: la suma del resultado es EXACTAMENTE `total` (o la capacidad
 * total si `total` la supera). Redondear cada tramo por separado es lo que en
 * la app antigua convertía un retraso de 30 minutos en un recorte de 32.
 */
fun repartirConTope(total: Int, topes: List<Int>): List<Int> {
    val n = topes.size
    val out = IntArray(n)
    val objetivo = maxOf(0, total)
    if (objetivo == 0 || n == 0) return out.toList()

    val limpios = topes.map { maxOf(0, it) }
    val capacidad = limpios.sum()
    if (capacidad == 0) return out.toList()
    if (objetivo >= capacidad) return limpios

    // Como objetivo < capacidad, el ratio es < 1 y ninguna ranura satura.
    val ratio = objetivo.toDouble() / capacidad
    val real = limpios.map { it * ratio }
    val base = real.map { kotlin.math.floor(it).toInt() }
    for (i in 0 until n) out[i] = base[i]
    var falta = objetivo - base.sum()

    // Método del resto mayor; desempate por orden.
    val orden = real.indices.sortedWith(
        compareByDescending<Int> { real[it] - kotlin.math.floor(real[it]) }.thenBy { it },
    )
    var pasada = 0
    while (falta > 0 && pasada < 2) {
        for (i in orden) {
            if (falta == 0) break
            if (out[i] < limpios[i]) {
                out[i]++
                falta--
            }
        }
        pasada++
    }
    return out.toList()
}
