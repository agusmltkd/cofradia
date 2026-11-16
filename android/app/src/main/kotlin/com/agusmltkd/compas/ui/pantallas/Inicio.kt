package com.agusmltkd.compas.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agusmltkd.compas.core.formatDelta
import com.agusmltkd.compas.core.formatHora
import com.agusmltkd.compas.core.indiceAncla
import com.agusmltkd.compas.datos.EstadoLicencia
import com.agusmltkd.compas.ui.Pantalla
import com.agusmltkd.compas.ui.UiEstado
import com.agusmltkd.compas.ui.componentes.BotonPrincipal
import com.agusmltkd.compas.ui.componentes.BotonSecundario
import com.agusmltkd.compas.ui.componentes.BotonTexto
import com.agusmltkd.compas.ui.componentes.Dato
import com.agusmltkd.compas.ui.componentes.Estado
import com.agusmltkd.compas.ui.componentes.Etiqueta
import com.agusmltkd.compas.ui.componentes.Paso
import com.agusmltkd.compas.ui.componentes.Segmentado
import com.agusmltkd.compas.ui.componentes.Separador
import com.agusmltkd.compas.ui.componentes.Tarjeta
import com.agusmltkd.compas.ui.tema.Compas

@Composable
fun PantallaInicio(
    e: UiEstado,
    alElegirItinerario: (String) -> Unit,
    alElegirSeccion: (String) -> Unit,
    alCambiarRetraso: (Int) -> Unit,
    alEmpezar: (ensayo: Boolean) -> Unit,
    alIr: (Pantalla) -> Unit,
) {
    val p = Compas.p
    val s = e.sesion ?: return
    val c = e.calculo

    Column(
        Modifier
            .fillMaxSize()
            .background(p.fondo)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // Cabecera: hermandad y estado de la licencia.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Marca(28.dp)
            Spacer(Modifier.weight(1f))
            BotonTexto("Ajustes", { alIr(Pantalla.AJUSTES) })
        }
        Spacer(Modifier.height(20.dp))
        Etiqueta(listOfNotNull(s.hermandad.diaSalida, s.hermandad.ciudad).joinToString(" · "))
        Spacer(Modifier.height(6.dp))
        Text(s.hermandad.nombre, style = Compas.t.titulo, color = p.tinta)
        Spacer(Modifier.height(8.dp))
        when (val l = s.licencia) {
            is EstadoLicencia.Activa -> Estado("Licencia activa · funciona sin red ${l.diasRestantes} días más", p.bien)
            EstadoLicencia.Demo -> Estado("Hermandad de ejemplo · datos ficticios", p.acento)
            EstadoLicencia.SinRenovar -> Estado("Hace más de 30 días que no sincroniza. Conéctate a internet.", p.mal)
            EstadoLicencia.RelojManipulado -> Estado("La hora del móvil no es correcta. Ponla en automático.", p.mal)
            EstadoLicencia.Invalida -> Estado("Licencia no válida. Contacta con tu hermandad.", p.mal)
        }

        Spacer(Modifier.height(28.dp))

        if (s.itinerarios.isEmpty()) {
            Tarjeta(Modifier.fillMaxWidth()) {
                Text("Todavía no hay itinerarios publicados", style = Compas.t.cuerpoFuerte, color = p.tinta)
                Spacer(Modifier.height(4.dp))
                Text("Cuando la hermandad publique uno desde el panel, aparecerá aquí al sincronizar.", style = Compas.t.secundario, color = p.tinta2)
            }
            return@Column
        }

        if (s.itinerarios.size > 1) {
            Etiqueta("Itinerario")
            Spacer(Modifier.height(8.dp))
            Segmentado(s.itinerarios.map { it.id to it.nombre }, e.itinerarioId, alElegirItinerario)
            Spacer(Modifier.height(20.dp))
        }

        val secciones = s.itinerarios.firstOrNull { it.id == e.itinerarioId }?.datos?.secciones.orEmpty()
        Etiqueta("Tu sección")
        Spacer(Modifier.height(8.dp))
        Segmentado(secciones.map { it.id to it.nombre }, e.seccionId, alElegirSeccion)
        Spacer(Modifier.height(20.dp))

        if (c == null) {
            Tarjeta(Modifier.fillMaxWidth()) {
                Text("Este itinerario tiene errores", style = Compas.t.cuerpoFuerte, color = p.mal)
                Spacer(Modifier.height(4.dp))
                Text("Revísalo en el panel web antes de usarlo.", style = Compas.t.secundario, color = p.tinta2)
            }
            return@Column
        }

        Tarjeta(Modifier.fillMaxWidth()) {
            Etiqueta("Retraso en la salida")
            Spacer(Modifier.height(12.dp))
            Paso(e.retraso, alCambiarRetraso, "min")
        }
        Spacer(Modifier.height(12.dp))

        // Resumen del recálculo.
        val r = c.resultado
        val iAncla = indiceAncla(c.datos)
        val ancla = r.horarios[iAncla]
        Tarjeta(Modifier.fillMaxWidth(), relleno = 0.dp) {
            FilaHito("Salida", formatHora(r.nuevaSalida), if (e.retraso > 0) "${formatDelta(e.retraso)} min" else "en hora", e.retraso > 0)
            Separador()
            FilaHito(
                if (ancla.punto.ancla) "Punto fijo · ${ancla.punto.nombre}" else "Llegada · ${ancla.punto.nombre}",
                formatHora(ancla.nueva),
                if (r.viable) "se mantiene" else "no se llega",
                !r.viable,
            )
            Separador()
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dato("Colchón", "${r.margenRestante} min", if (r.margenRestante < 10) p.mal else p.tinta)
                Dato("Máximo", "${c.maximo} min")
                Dato("Andar", if (r.aceleracionAndar > 0) "+%.0f%%".format(r.aceleracionAndar * 100) else "normal", if (r.aceleracionAndar > 0) p.mal else p.tinta)
            }
        }

        val avisos = r.avisos + c.plan.avisosCortejo
        if (avisos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            avisos.forEach {
                Text("· $it", style = Compas.t.secundario, color = if (!r.viable) p.mal else p.tinta2, modifier = Modifier.padding(vertical = 3.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        BotonPrincipal("Empezar modo en vivo", { alEmpezar(false) }, activo = s.puedeSalirEnVivo)
        Spacer(Modifier.height(10.dp))
        BotonSecundario("Ver horario completo", { alIr(Pantalla.HORARIO) })
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            BotonTexto("Ensayar ahora con la hora simulada", { alEmpezar(true) }, color = p.tinta2)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilaHito(rotulo: String, hora: String, detalle: String, alerta: Boolean) {
    val p = Compas.p
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Etiqueta(rotulo)
            Spacer(Modifier.height(4.dp))
            Text(detalle, style = Compas.t.secundario, color = if (alerta) p.mal else p.tinta2)
        }
        Text(hora, style = Compas.t.cifraGrande, color = p.tinta)
    }
}
