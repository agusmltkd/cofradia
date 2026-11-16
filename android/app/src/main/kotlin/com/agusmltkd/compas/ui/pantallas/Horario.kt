package com.agusmltkd.compas.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.agusmltkd.compas.core.HorarioPunto
import com.agusmltkd.compas.core.formatDelta
import com.agusmltkd.compas.core.formatHora
import com.agusmltkd.compas.ui.UiEstado
import com.agusmltkd.compas.ui.componentes.Cabecera
import com.agusmltkd.compas.ui.tema.Compas

@Composable
fun PantallaHorario(e: UiEstado, alVolver: () -> Unit) {
    val p = Compas.p
    val c = e.calculo ?: return
    val horarios = c.resultado.horarios

    Column(Modifier.fillMaxSize().background(p.fondo).systemBarsPadding().padding(horizontal = 20.dp)) {
        Cabecera(
            titulo = c.seccion.nombre,
            sobretitulo = c.itinerario.nombre + if (e.retraso > 0) " · recalculado con ${e.retraso} min de retraso" else "",
            atras = alVolver,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(horarios, key = { _, h -> h.punto.id }) { i, h ->
                FilaPunto(h, primero = i == 0, ultimo = i == horarios.lastIndex, actual = false, pasado = false)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Una parada del recorrido con su raíl a la izquierda.
 * Los puntos de horario fijo llevan un rombo de color acento.
 */
@Composable
fun FilaPunto(h: HorarioPunto, primero: Boolean, ultimo: Boolean, actual: Boolean, pasado: Boolean) {
    val p = Compas.p
    val tinta = if (pasado) p.tinta3 else p.tinta
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 60.dp)) {
        // Hora
        Column(Modifier.width(64.dp).padding(top = 14.dp)) {
            Text(formatHora(h.nueva), style = Compas.t.hora, color = if (actual) p.acento else tinta)
            if (h.delta != 0) {
                Text(
                    formatHora(h.original),
                    style = Compas.t.secundario,
                    color = p.tinta3,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
        // Raíl
        Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).height(18.dp).background(if (primero) Color.Transparent else p.linea))
                Marcador(h.punto.ancla, actual, pasado)
                Box(Modifier.width(2.dp).weight(1f).background(if (ultimo) Color.Transparent else p.linea))
            }
        }
        // Nombre
        Column(Modifier.weight(1f).padding(top = 13.dp, bottom = 12.dp, start = 4.dp)) {
            Text(h.punto.nombre, style = if (actual) Compas.t.cuerpoFuerte else Compas.t.cuerpo, color = tinta)
            val detalle = listOfNotNull(
                if (h.punto.ancla) "Horario fijo" else null,
                h.punto.nota,
            ).joinToString(" · ")
            if (detalle.isNotEmpty()) Text(detalle, style = Compas.t.secundario, color = if (h.punto.ancla) p.acento else p.tinta2)
        }
        if (h.delta != 0) {
            Text(
                formatDelta(h.delta),
                style = Compas.t.hora,
                color = if (h.delta > 0) p.mal else p.bien,
                modifier = Modifier.padding(top = 14.dp, start = 8.dp),
            )
        }
    }
}

@Composable
private fun Marcador(ancla: Boolean, actual: Boolean, pasado: Boolean) {
    val p = Compas.p
    val forma = if (ancla) RoundedCornerShape(2.dp) else CircleShape
    val color = when {
        actual -> p.acento
        ancla -> p.acento
        pasado -> p.tinta3
        else -> p.fondo
    }
    Box(
        Modifier
            .size(if (ancla || actual) 12.dp else 10.dp)
            .clip(forma)
            .background(color)
            .border(2.dp, if (actual || ancla) p.acento else if (pasado) p.tinta3 else p.tinta2, forma),
    )
}
