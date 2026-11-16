package com.agusmltkd.compas.ui.pantallas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agusmltkd.compas.ui.componentes.BotonPrincipal
import com.agusmltkd.compas.ui.componentes.BotonTexto
import com.agusmltkd.compas.ui.componentes.Etiqueta
import com.agusmltkd.compas.ui.componentes.RadioBoton
import com.agusmltkd.compas.ui.componentes.Separador
import com.agusmltkd.compas.ui.tema.Compas

/** Puntos del símbolo de Statio en una caja de 108 (los mismos que el icono; ver marca/puntos.py). */
private val PuntosMarca = listOf(70.0f to 36.0f, 62.89f to 29.1f, 52.32f to 28.33f, 42.3f to 31.75f, 38.1f to 40.86f, 44.91f to 48.59f, 54.84f to 52.48f, 64.54f to 56.84f, 69.99f to 65.41f, 65.53f to 74.69f, 55.68f to 78.44f, 45.11f to 77.85f, 38f to 71f)

/**
 * Marca de Statio: una S hecha de paradas que termina en el punto de horario
 * fijo (el rombo de color acento).
 */
@Composable
fun Marca(tamano: Dp = 44.dp, tinta: Color = Compas.p.tinta, acento: Color = Compas.p.acento) {
    Canvas(Modifier.size(tamano)) {
        // El símbolo ocupa x 30..80, y 28..86 de la caja de 108: lo encuadramos.
        val k = size.minDimension / 58f
        fun pos(x: Float, y: Float) = Offset((x - 26f) * k, (y - 28f) * k)
        PuntosMarca.dropLast(1).forEachIndexed { i, (x, y) ->
            drawCircle(tinta, radius = (if (i == 0) 4.8f else 3.4f) * k, center = pos(x, y))
        }
        val (fx, fy) = PuntosMarca.last()
        val c = pos(fx, fy)
        val s = 7.6f * k
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x, c.y - s); lineTo(c.x + s, c.y); lineTo(c.x, c.y + s); lineTo(c.x - s, c.y); close()
            },
            acento,
        )
    }
}

@Composable
fun PantallaActivacion(
    activando: Boolean,
    error: String?,
    alActivar: (String) -> Unit,
    alProbarDemo: () -> Unit,
) {
    val p = Compas.p
    var codigo by rememberSaveable { mutableStateOf("") }
    val limpio = codigo.uppercase().filter { it.isLetterOrDigit() }.take(8)
    val completo = limpio.length == 8

    Column(
        Modifier
            .fillMaxSize()
            .background(p.fondo)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Marca(36.dp)
            Spacer(Modifier.size(12.dp))
            Text("Statio", style = Compas.t.subtitulo, color = p.tinta)
        }
        Spacer(Modifier.height(56.dp))
        Text("El horario de tu cofradía,\nal oído.", style = Compas.t.titulo.copy(fontSize = 32.sp, lineHeight = 38.sp), color = p.tinta)
        Spacer(Modifier.height(12.dp))
        Text(
            "Cada fiscal sabe en todo momento dónde tiene que estar. Pulsa el botón de los auriculares y la app te lo dice.",
            style = Compas.t.cuerpo,
            color = p.tinta2,
        )
        Spacer(Modifier.height(40.dp))

        Etiqueta("Código de tu hermandad")
        Spacer(Modifier.height(10.dp))
        CampoCodigo(limpio, { codigo = it }, enviar = { if (completo) alActivar(limpio) })
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, style = Compas.t.secundario, color = p.mal)
        }
        Spacer(Modifier.height(16.dp))
        BotonPrincipal(if (activando) "Activando…" else "Activar", { alActivar(limpio) }, activo = completo && !activando)
        Spacer(Modifier.height(12.dp))
        Text(
            "Te lo da el Diputado Mayor de Gobierno de tu hermandad. Solo hace falta conexión esta vez: después la app funciona 30 días sin cobertura.",
            style = Compas.t.secundario,
            color = p.tinta3,
        )

        Spacer(Modifier.height(32.dp))
        Separador()
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(8.dp))
        Text("¿Solo quieres verla?", style = Compas.t.secundario, color = p.tinta2)
        BotonTexto("Probar con una hermandad de ejemplo →", alProbarDemo, Modifier.padding(start = 0.dp), color = p.acento)
        Spacer(Modifier.height(32.dp))
    }
}

/** Campo de 8 caracteres en dos bloques: ABCD-1234. */
@Composable
private fun CampoCodigo(valor: String, alCambiar: (String) -> Unit, enviar: () -> Unit) {
    val p = Compas.p
    BasicTextField(
        value = valor,
        onValueChange = { alCambiar(it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { enviar() }),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        decorationBox = { campo ->
            Box {
                Box(Modifier.size(1.dp)) { campo() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until 8) {
                        if (i == 4) Text("–", style = Compas.t.subtitulo, color = p.tinta3)
                        val c = valor.getOrNull(i)?.toString() ?: ""
                        val actual = i == valor.length
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RadioBoton)
                                .background(p.superficie)
                                .border(if (actual) 1.5.dp else 1.dp, if (actual) p.tinta2 else p.linea, RadioBoton),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(c, style = Compas.t.cifra.copy(letterSpacing = 0.em), color = p.tinta, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
    )
}
