package com.agusmltkd.compas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.agusmltkd.compas.core.Demo
import com.agusmltkd.compas.core.estadoVivo
import com.agusmltkd.compas.core.recalcularPlan
import com.agusmltkd.compas.core.retrasoMaximoSeccion
import com.agusmltkd.compas.core.validar
import com.agusmltkd.compas.datos.EstadoLicencia
import com.agusmltkd.compas.datos.Sesion
import com.agusmltkd.compas.ui.pantallas.PantallaActivacion
import com.agusmltkd.compas.ui.pantallas.PantallaAjustes
import com.agusmltkd.compas.ui.pantallas.PantallaEnVivo
import com.agusmltkd.compas.ui.pantallas.PantallaHorario
import com.agusmltkd.compas.ui.pantallas.PantallaInicio
import com.agusmltkd.compas.ui.tema.TemaCompas

// ---------------------------------------------------------------------------
// Previsualizaciones para Android Studio: abre este archivo y pulsa "Split"
// o "Design" arriba a la derecha. Cada función es una pantalla con datos de
// la hermandad de ejemplo, en claro y en oscuro.
// ---------------------------------------------------------------------------

private fun estadoEjemplo(retraso: Int = 12, seccion: Int = 0): UiEstado {
    val c = Demo.contenido
    val it0 = c.itinerarios.first()
    val sec = it0.datos.secciones[seccion]
    val plan = recalcularPlan(it0.datos, retraso)
    val sesion = Sesion(
        hermandad = c.hermandad,
        itinerarios = c.itinerarios,
        rol = "fiscal",
        seccionPreferida = sec.id,
        etiqueta = "Fiscal de cruz de guía",
        licencia = EstadoLicencia.Activa(27, "2027-06-30"),
        esDemo = false,
        ultimaSincronizacion = 1_795_000_000,
    )
    return UiEstado(
        cargando = false,
        sesion = sesion,
        itinerarioId = it0.id,
        seccionId = sec.id,
        retraso = retraso,
        calculo = Calculo(it0, sec, plan, plan.secciones.first { it.seccion.id == sec.id }, retrasoMaximoSeccion(it0.datos, sec), validar(it0.datos)),
    )
}

@Composable
private fun EnVivoEjemplo() {
    val e = estadoEjemplo(0)
    val h = e.calculo!!.resultado.horarios
    PantallaEnVivo(estadoVivo(h, h[4].nueva * 60L - 204), "Cruz de guía", "Hermandad de Ejemplo", ensayo = false, {}, {}, {}, datosHermandad = e.sesion!!.hermandad)
}

@Preview(name = "1 · Activación", widthDp = 390, heightDp = 844, showBackground = true)
@Composable fun PrevActivacion() = TemaCompas(false) { PantallaActivacion(false, null, {}, {}) }

@Preview(name = "1 · Activación (oscuro)", widthDp = 390, heightDp = 844, showBackground = true)
@Composable fun PrevActivacionOscuro() = TemaCompas(true) { PantallaActivacion(false, "Ese código no existe o está desactivado", {}, {}) }

@Preview(name = "2 · Inicio", widthDp = 390, heightDp = 1100, showBackground = true)
@Composable fun PrevInicio() = TemaCompas(false) { PantallaInicio(estadoEjemplo(), {}, {}, {}, {}, {}) }

@Preview(name = "2 · Inicio (oscuro, no viable)", widthDp = 390, heightDp = 1100, showBackground = true)
@Composable fun PrevInicioOscuro() = TemaCompas(true) { PantallaInicio(estadoEjemplo(retraso = 80), {}, {}, {}, {}, {}) }

@Preview(name = "3 · Horario", widthDp = 390, heightDp = 844, showBackground = true)
@Composable fun PrevHorario() = TemaCompas(false) { PantallaHorario(estadoEjemplo()) {} }

@Preview(name = "4 · En vivo", widthDp = 390, heightDp = 900, showBackground = true)
@Composable fun PrevEnVivo() = TemaCompas(true) { EnVivoEjemplo() }

@Preview(name = "4 · En vivo (claro)", widthDp = 390, heightDp = 900, showBackground = true)
@Composable fun PrevEnVivoClaro() = TemaCompas(false) { EnVivoEjemplo() }

@Preview(name = "5 · Ajustes", widthDp = 390, heightDp = 1200, showBackground = true)
@Composable fun PrevAjustes() = TemaCompas(true) { PantallaAjustes(estadoEjemplo(), "1.0.0", {}, {}, {}, {}, {}) }
