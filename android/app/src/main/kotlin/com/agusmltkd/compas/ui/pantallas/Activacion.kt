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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Marca: el arco del reloj y la línea del recorrido que acaba en el punto fijo. */
@Composable
fun Marca(tamano: Dp = 44.dp, tinta: Color = Compas.p.tinta, acento: Color = Compas.p.acento) {
    Canvas(Modifier.size(tamano)) {
        val s = size.minDimension
        val grosor = s * 0.09f
        val r = s * 0.40f
        val c = Offset(s / 2, s / 2)
        drawArc(
            color = tinta,
            startAngle = -90f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = grosor, cap = StrokeCap.Round),
        )
        drawLine(tinta, c, Offset(c.x, c.y - r * 0.82f), strokeWidth = grosor, cap = StrokeCap.Round)
        drawCircle(acento, radius = s * 0.11f, center = c)
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
            Text("Compás", style = Compas.t.subtitulo, color = p.tinta)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Solo quieres verla?", style = Compas.t.secundario, color = p.tinta2, modifier = Modifier.weight(1f))
            BotonTexto("Probar con una hermandad de ejemplo", alProbarDemo, color = p.acento)
        }
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
