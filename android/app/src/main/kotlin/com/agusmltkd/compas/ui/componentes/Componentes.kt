package com.agusmltkd.compas.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agusmltkd.compas.ui.tema.Compas

val RadioTarjeta = RoundedCornerShape(16.dp)
val RadioBoton = RoundedCornerShape(14.dp)

/** Rótulo pequeño en versalitas: "RETRASO EN LA SALIDA". */
@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier, color: Color = Compas.p.tinta3) {
    Text(texto.uppercase(), style = Compas.t.etiqueta, color = color, modifier = modifier)
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    relleno: Dp = 16.dp,
    alPulsar: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val p = Compas.p
    Column(
        modifier
            .clip(RadioTarjeta)
            .background(p.superficie)
            .border(1.dp, p.linea, RadioTarjeta)
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier)
            .padding(relleno),
        content = contenido,
    )
}

@Composable
fun BotonPrincipal(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, activo: Boolean = true) {
    val p = Compas.p
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RadioBoton)
            .background(if (activo) p.tinta else p.superficie2)
            .clickable(enabled = activo, role = Role.Button, onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = Compas.t.cuerpoFuerte, color = if (activo) p.fondo else p.tinta3)
    }
}

@Composable
fun BotonSecundario(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, peligro: Boolean = false) {
    val p = Compas.p
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RadioBoton)
            .border(1.dp, p.linea, RadioBoton)
            .clickable(role = Role.Button, onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = Compas.t.cuerpoFuerte, color = if (peligro) p.mal else p.tinta)
    }
}

@Composable
fun BotonTexto(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, color: Color = Compas.p.tinta2) {
    Text(
        texto,
        style = Compas.t.cuerpoFuerte,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = alPulsar)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** Selector de opciones en una sola fila, estilo control segmentado. */
@Composable
fun <T> Segmentado(opciones: List<Pair<T, String>>, seleccion: T?, alCambiar: (T) -> Unit, modifier: Modifier = Modifier) {
    val p = Compas.p
    Row(
        modifier
            .fillMaxWidth()
            .clip(RadioBoton)
            .background(p.superficie2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opciones.forEach { (valor, texto) ->
            val activo = valor == seleccion
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activo) p.superficie else Color.Transparent)
                    .then(if (activo) Modifier.border(1.dp, p.linea, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable(role = Role.Tab) { alCambiar(valor) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    texto,
                    style = Compas.t.secundario,
                    color = if (activo) p.tinta else p.tinta2,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** − 12 min + */
@Composable
fun Paso(valor: Int, alCambiar: (Int) -> Unit, sufijo: String, modifier: Modifier = Modifier, minimo: Int = 0, maximo: Int = 240, paso: Int = 1) {
    val p = Compas.p
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BotonRedondo("−", activo = valor > minimo) { alCambiar((valor - paso).coerceAtLeast(minimo)) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(valor.toString(), style = Compas.t.cifraGrande, color = p.tinta)
            Spacer(Modifier.width(6.dp))
            Text(sufijo, style = Compas.t.secundario, color = p.tinta2, modifier = Modifier.padding(bottom = 6.dp))
        }
        BotonRedondo("+", activo = valor < maximo) { alCambiar((valor + paso).coerceAtMost(maximo)) }
    }
}

@Composable
fun BotonRedondo(simbolo: String, activo: Boolean = true, alPulsar: () -> Unit) {
    val p = Compas.p
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(1.dp, p.linea, CircleShape)
            .clickable(enabled = activo, role = Role.Button, onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Text(simbolo, style = Compas.t.titulo, color = if (activo) p.tinta else p.tinta3)
    }
}

/** Punto de color + texto: "Licencia activa · 28 días". */
@Composable
fun Estado(texto: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(texto, style = Compas.t.secundario, color = Compas.p.tinta2)
    }
}

/** Dato con rótulo, para filas de métricas. */
@Composable
fun RowScope.Dato(rotulo: String, valor: String, color: Color = Compas.p.tinta, modifier: Modifier = Modifier) {
    Column(modifier.weight(1f)) {
        Etiqueta(rotulo)
        Spacer(Modifier.height(4.dp))
        Text(valor, style = Compas.t.cifra, color = color, maxLines = 1)
    }
}

@Composable
fun Separador(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Compas.p.linea))
}

/** Fila de ajuste con interruptor. */
@Composable
fun FilaInterruptor(titulo: String, detalle: String?, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    val p = Compas.p
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) { alCambiar(!activo) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = Compas.t.cuerpo, color = p.tinta)
            if (detalle != null) Text(detalle, style = Compas.t.secundario, color = p.tinta2)
        }
        Spacer(Modifier.width(12.dp))
        Interruptor(activo)
    }
}

/** Interruptor propio: más sobrio que el de Material. */
@Composable
fun Interruptor(activo: Boolean) {
    val p = Compas.p
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (activo) p.acento else p.superficie2)
            .border(1.dp, if (activo) p.acento else p.linea, CircleShape)
            .padding(3.dp),
        contentAlignment = if (activo) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (activo) p.sobreAcento else p.tinta3))
    }
}

/** Cabecera de pantalla: título grande y, opcionalmente, una acción a la derecha. */
@Composable
fun Cabecera(titulo: String, sobretitulo: String? = null, accion: (@Composable () -> Unit)? = null, atras: (() -> Unit)? = null) {
    val p = Compas.p
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            if (atras != null) {
                BotonTexto("‹ Volver", atras, Modifier.padding(start = 0.dp), color = p.tinta2)
            }
            Spacer(Modifier.weight(1f))
            accion?.invoke()
        }
        if (sobretitulo != null) {
            Etiqueta(sobretitulo)
            Spacer(Modifier.height(6.dp))
        }
        Text(titulo, style = Compas.t.titulo, color = p.tinta)
    }
}

/**
 * Escudo de la hermandad. Si no hay imagen guardada, sus iniciales sobre su
 * color (nunca un hueco vacío).
 */
@Composable
fun Escudo(hermandad: com.agusmltkd.compas.core.Hermandad, fichero: java.io.File?, tamano: Dp = 48.dp, modifier: Modifier = Modifier) {
    val imagen = androidx.compose.runtime.remember(fichero?.path, fichero?.lastModified()) {
        fichero?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
    if (imagen != null) {
        androidx.compose.foundation.Image(
            bitmap = imagen,
            contentDescription = "Escudo de ${hermandad.nombre}",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier.size(tamano),
        )
    } else {
        val fondo = runCatching { Color(android.graphics.Color.parseColor(hermandad.color)) }.getOrDefault(Compas.p.acento)
        Box(modifier.size(tamano).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
            Text(
                com.agusmltkd.compas.core.iniciales(hermandad),
                style = Compas.t.cuerpoFuerte.copy(fontSize = (tamano.value * 0.36f).sp),
                color = Color.White,
            )
        }
    }
}
