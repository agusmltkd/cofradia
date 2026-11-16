package com.agusmltkd.compas.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agusmltkd.compas.datos.EstadoLicencia
import com.agusmltkd.compas.ui.Ajustes
import com.agusmltkd.compas.ui.UiEstado
import com.agusmltkd.compas.ui.componentes.BotonSecundario
import com.agusmltkd.compas.ui.componentes.Cabecera
import com.agusmltkd.compas.ui.componentes.Escudo
import com.agusmltkd.compas.ui.componentes.Etiqueta
import com.agusmltkd.compas.ui.componentes.FilaInterruptor
import com.agusmltkd.compas.ui.componentes.Segmentado
import com.agusmltkd.compas.ui.componentes.Separador
import com.agusmltkd.compas.ui.componentes.Tarjeta
import com.agusmltkd.compas.ui.tema.Compas
import java.text.DateFormat
import java.util.Date

@Composable
fun PantallaAjustes(
    e: UiEstado,
    version: String,
    alCambiar: (Ajustes) -> Unit,
    alSincronizar: () -> Unit,
    alSalir: () -> Unit,
    alAbrirPrivacidad: () -> Unit,
    alVolver: () -> Unit,
) {
    val p = Compas.p
    val a = e.ajustes
    val s = e.sesion

    Column(
        Modifier
            .fillMaxSize()
            .background(p.fondo)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Cabecera("Ajustes", atras = alVolver)

        Etiqueta("Voz")
        Spacer(Modifier.height(4.dp))
        Tarjeta(Modifier.fillMaxWidth(), relleno = 16.dp) {
            FilaInterruptor("Avisos automáticos", "Habla sola sin pulsar nada", a.avisosAutomaticos) { alCambiar(a.copy(avisosAutomaticos = it)) }
            if (a.avisosAutomaticos) {
                Separador()
                FilaInterruptor("Antes de cada punto", null, a.avisoPrevio) { alCambiar(a.copy(avisoPrevio = it)) }
                if (a.avisoPrevio) {
                    Segmentado(
                        listOf(60 to "1 min", 120 to "2 min", 180 to "3 min", 300 to "5 min"),
                        a.antelacion,
                        { alCambiar(a.copy(antelacion = it)) },
                        Modifier.padding(bottom = 12.dp),
                    )
                }
                Separador()
                FilaInterruptor("Al llegar a cada punto", "\"Deberías estar en…\"", a.avisoAlLlegar) { alCambiar(a.copy(avisoAlLlegar = it)) }
            }
            Separador()
            Spacer(Modifier.height(12.dp))
            Text("Velocidad de la voz", style = Compas.t.cuerpo, color = p.tinta)
            Spacer(Modifier.height(8.dp))
            Segmentado(
                listOf(0.85f to "Pausada", 1.0f to "Normal", 1.2f to "Rápida"),
                a.velocidadVoz,
                { alCambiar(a.copy(velocidadVoz = it)) },
            )
            Spacer(Modifier.height(4.dp))
            FilaInterruptor("Hora en formato 24 h", "\"Son las 20 y cuarto\" en vez de \"las ocho y cuarto\"", a.formato24h) { alCambiar(a.copy(formato24h = it)) }
        }

        Spacer(Modifier.height(24.dp))
        Etiqueta("Apariencia")
        Spacer(Modifier.height(8.dp))
        Segmentado(listOf(0 to "Sistema", 1 to "Claro", 2 to "Oscuro"), a.tema, { alCambiar(a.copy(tema = it)) })

        if (s != null) {
            Spacer(Modifier.height(24.dp))
            Etiqueta("Hermandad")
            Spacer(Modifier.height(8.dp))
            Tarjeta(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Escudo(s.hermandad, s.escudo, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(s.hermandad.nombre, style = Compas.t.cuerpoFuerte, color = p.tinta)
                }
                Spacer(Modifier.height(8.dp))
                val rol = when (s.rol) {
                    "diputado_mayor" -> "Diputado Mayor de Gobierno"
                    "consulta" -> "Solo consulta"
                    else -> "Fiscal"
                }
                Linea("Este móvil", listOfNotNull(s.etiqueta, rol).distinct().joinToString(" · "))
                when (val l = s.licencia) {
                    is EstadoLicencia.Activa -> Linea("Licencia", "hasta el ${l.finLicencia}")
                    EstadoLicencia.Demo -> Linea("Licencia", "Demostración")
                    else -> Linea("Licencia", "No vigente")
                }
                if (!s.esDemo) {
                    Linea(
                        "Última sincronización",
                        if (s.ultimaSincronizacion > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.ultimaSincronizacion * 1000)) else "nunca",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!s.esDemo) {
                BotonSecundario(if (e.sincronizando) "Sincronizando…" else "Sincronizar ahora", alSincronizar)
                Spacer(Modifier.height(10.dp))
            }
            BotonSecundario(if (s.esDemo) "Salir de la demostración" else "Desvincular este móvil", alSalir, peligro = !s.esDemo)
        }

        Spacer(Modifier.height(24.dp))
        Etiqueta("Acerca de")
        Spacer(Modifier.height(8.dp))
        Linea("Versión", version)
        Spacer(Modifier.height(8.dp))
        BotonSecundario("Política de privacidad", alAbrirPrivacidad)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Linea(rotulo: String, valor: String) {
    val p = Compas.p
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(rotulo, style = Compas.t.secundario, color = p.tinta2, modifier = Modifier.weight(1f))
        Text(valor, style = Compas.t.secundario, color = p.tinta)
    }
}
