package com.agusmltkd.compas.core

data class Incidencia(val grave: Boolean, val texto: String)

/**
 * Comprueba un itinerario antes de usarlo. Las incidencias graves impiden
 * salir en vivo; las leves solo se muestran.
 */
fun validar(datos: DatosItinerario): List<Incidencia> {
    val out = mutableListOf<Incidencia>()
    fun grave(t: String) = out.add(Incidencia(true, t))
    fun leve(t: String) = out.add(Incidencia(false, t))

    if (datos.puntos.size < 2) grave("El itinerario necesita al menos dos puntos.")
    if (datos.secciones.isEmpty()) grave("No hay ninguna sección (cruz de guía, pasos...).")
    if (datos.puntos.firstOrNull()?.ancla == true) grave("La salida no puede ser un punto de horario fijo.")
    if (datos.puntos.none { it.ancla }) leve("No hay ningún punto de horario fijo: el recálculo apuntará al último punto.")

    datos.puntos.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach {
        grave("Identificador de punto repetido: $it")
    }
    datos.puntos.groupBy { it.nombre.trim().lowercase() }.filter { it.value.size > 1 }.keys.forEach {
        leve("Hay varios puntos llamados «$it». Añade algo que los distinga (ida/vuelta).")
    }
    for (s in datos.secciones) {
        if (s.tramos.size != datos.puntos.size - 1) {
            grave("«${s.nombre}»: ${s.tramos.size} tramos para ${datos.puntos.size} puntos (deberían ser ${datos.puntos.size - 1}).")
            continue
        }
        s.tramos.forEachIndexed { i, t ->
            if (t.andar < 0 || t.paron < 0) grave("«${s.nombre}»: tiempos negativos entre ${datos.puntos[i].nombre} y ${datos.puntos[i + 1].nombre}.")
            if (t.andar + t.paron == 0) leve("«${s.nombre}»: llega a ${datos.puntos[i + 1].nombre} a la misma hora que al punto anterior.")
            if (t.andar + t.paron > 90) leve("«${s.nombre}»: más de hora y media entre ${datos.puntos[i].nombre} y ${datos.puntos[i + 1].nombre}.")
        }
        if (s.tramos.any { it.estimado }) leve("«${s.nombre}»: el reparto andar/parón está estimado en algunos tramos.")
    }
    // Las secciones van en orden: la siguiente no puede pasar antes que la anterior.
    for (k in 0 until datos.secciones.size - 1) {
        val a = datos.secciones[k]
        val b = datos.secciones[k + 1]
        if (a.tramos.size != b.tramos.size) continue
        val ha = horariosOriginales(a)
        val hb = horariosOriginales(b)
        val i = ha.indices.firstOrNull { hb[it] < ha[it] }
        if (i != null) leve("«${b.nombre}» pasa por ${datos.puntos[i].nombre} antes que «${a.nombre}».")
    }
    return out
}
