package com.agusmltkd.compas.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.agusmltkd.compas.core.EstadoVivo
import com.agusmltkd.compas.core.Fase
import com.agusmltkd.compas.core.formatCuenta
import com.agusmltkd.compas.core.formatHora
import com.agusmltkd.compas.core.formatHoraSegundos
import com.agusmltkd.compas.ui.componentes.BotonPrincipal
import com.agusmltkd.compas.ui.componentes.BotonSecundario
import com.agusmltkd.compas.ui.componentes.Etiqueta
import com.agusmltkd.compas.ui.componentes.Separador
import com.agusmltkd.compas.ui.componentes.Tarjeta
import com.agusmltkd.compas.ui.tema.Compas

/**
 * Pantalla del modo en vivo. Pensada para mirarla medio segundo entre dos
 * levantás: la hora enorme, el siguiente punto y cuánto falta.
 */
@Composable
fun PantallaEnVivo(
    estado: EstadoVivo?,
    seccion: String,
    hermandad: String,
    ensayo: Boolean,
    alHablar: () -> Unit,
    alProximos: () -> Unit,
    alTerminar: () -> Unit,
) {
    val p = Compas.p
    Column(
        Modifier
            .fillMaxSize()
            .background(p.fondo)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta("En vivo · $seccion", color = p.acento)
            Spacer(Modifier.weight(1f))
            if (ensayo) {
                Text(
                    "ENSAYO",
                    style = Compas.t.etiqueta,
                    color = p.sobreAcento,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.acento).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(hermandad, style = Compas.t.secundario, color = p.tinta3)

        if (estado == null) {
            Spacer(Modifier.height(80.dp))
            Text("Arrancando…", style = Compas.t.subtitulo, color = p.tinta2)
            return@Column
        }

        Spacer(Modifier.height(24.dp))
        Text(formatHoraSegundos(estado.ahora), style = Compas.t.reloj, color = p.tinta)
        Spacer(Modifier.height(24.dp))

        when (estado.fase) {
            Fase.TERMINADO -> Tarjeta(Modifier.fillMaxWidth()) {
                Etiqueta("Itinerario")
                Spacer(Modifier.height(6.dp))
                Text("Terminado", style = Compas.t.titulo, color = p.tinta)
            }
            else -> {
                val enPunto = estado.enPunto
                val objetivo = enPunto ?: estado.siguiente
                Tarjeta(Modifier.fillMaxWidth(), relleno = 20.dp) {
                    Etiqueta(
                        when {
                            estado.fase == Fase.ANTES_DE_SALIR -> "Salida"
                            enPunto != null -> "Deberías estar en"
                            else -> "Siguiente"
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(objetivo?.punto?.nombre ?: "—", style = Compas.t.titulo, color = p.tinta)
                    if (objetivo?.punto?.nota != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(objetivo.punto.nota!!, style = Compas.t.secundario, color = p.tinta2)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Etiqueta("A las")
                            Text(objetivo?.let { formatHora(it.nueva) } ?: "—", style = Compas.t.cifraGrande, color = p.tinta)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Etiqueta(if (enPunto != null) "Ahora" else "Faltan")
                            Text(
                                if (enPunto != null) "0:00" else formatCuenta(estado.segundosHastaSiguiente),
                                style = Compas.t.cifraGrande,
                                color = if (objetivo?.punto?.ancla == true) p.acento else p.tinta,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Progreso(estado.progresoTramo)
                }
            }
        }

        // Los siguientes puntos, en pequeño.
        val despues = estado.restantes.filter { it != estado.enPunto && it != estado.siguiente }.take(3)
        if (despues.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Etiqueta("Después")
            Spacer(Modifier.height(4.dp))
            despues.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(formatHora(h.nueva), style = Compas.t.hora, color = p.tinta2, modifier = Modifier.padding(end = 16.dp))
                    Text(h.punto.nombre, style = Compas.t.cuerpo, color = if (h.punto.ancla) p.acento else p.tinta, modifier = Modifier.weight(1f))
                }
                Separador()
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonPrincipal("Escuchar", alHablar, Modifier.weight(1f))
            BotonSecundario("Próximos", alProximos, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Con la pantalla apagada: una pulsación en el botón de los auriculares dice la hora y el siguiente punto. Dos pulsaciones, los dos próximos.",
            style = Compas.t.secundario,
            color = p.tinta3,
        )
        Spacer(Modifier.height(24.dp))
        BotonSecundario("Terminar", alTerminar, peligro = true)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Progreso(fraccion: Float) {
    val p = Compas.p
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.superficie2)) {
        Box(Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).height(4.dp).background(p.tinta))
    }
}
