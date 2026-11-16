package com.agusmltkd.compas.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Dirección visual: un instrumento, no una web.
 *
 *  - Referencias: paneles de salidas de estación, el cuadro de un coche y apps
 *    de seguimiento de vuelos. Mucha información a golpe de vista, cifras
 *    grandes y alineadas, cero adornos.
 *  - Fondo negro cálido y texto marfil (cera), no blanco puro: de noche,
 *    en la calle, deslumbra menos.
 *  - Un único acento, "cera" (oro viejo). Verde y terracota solo para decir
 *    "vas bien / vas tarde". Nada de degradados, sombras ni emojis.
 *  - Cifras tabulares en todas las horas para que no "bailen" al cambiar.
 */
@Immutable
data class Paleta(
    val fondo: Color,
    val superficie: Color,
    val superficie2: Color,
    val linea: Color,
    val tinta: Color,
    val tinta2: Color,
    val tinta3: Color,
    val acento: Color,
    val sobreAcento: Color,
    val bien: Color,
    val mal: Color,
    val oscura: Boolean,
)

val PaletaOscura = Paleta(
    fondo = Color(0xFF0E0E0D),
    superficie = Color(0xFF171614),
    superficie2 = Color(0xFF211F1C),
    linea = Color(0xFF2D2A26),
    tinta = Color(0xFFF1ECE2),
    tinta2 = Color(0xFFA69F93),
    tinta3 = Color(0xFF6E685F),
    acento = Color(0xFFD8A24A),
    sobreAcento = Color(0xFF16120A),
    bien = Color(0xFF7DB894),
    mal = Color(0xFFE5836A),
    oscura = true,
)

val PaletaClara = Paleta(
    fondo = Color(0xFFF5F2EC),
    superficie = Color(0xFFFFFFFF),
    superficie2 = Color(0xFFEDE8DF),
    linea = Color(0xFFE0D9CD),
    tinta = Color(0xFF1B1A17),
    tinta2 = Color(0xFF625C53),
    tinta3 = Color(0xFF958E83),
    acento = Color(0xFF9C6A17),
    sobreAcento = Color(0xFFFFFFFF),
    bien = Color(0xFF2E7A4F),
    mal = Color(0xFFB4462C),
    oscura = false,
)

private val tabular = "tnum"
private val familia = FontFamily.Default

@Immutable
data class Tipos(
    val reloj: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.Medium, fontSize = 76.sp, letterSpacing = (-0.03).em, fontFeatureSettings = tabular, lineHeight = 80.sp),
    val cifraGrande: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-0.02).em, fontFeatureSettings = tabular, lineHeight = 44.sp),
    val cifra: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = tabular, lineHeight = 26.sp),
    val titulo: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.01).em, lineHeight = 31.sp),
    val subtitulo: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp),
    val cuerpo: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    val cuerpoFuerte: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    val secundario: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    val hora: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFeatureSettings = tabular),
    val etiqueta: TextStyle = TextStyle(fontFamily = familia, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.09.em),
)

val LocalPaleta = staticCompositionLocalOf { PaletaOscura }
val LocalTipos = staticCompositionLocalOf { Tipos() }

object Compas {
    val p: Paleta @Composable get() = LocalPaleta.current
    val t: Tipos @Composable get() = LocalTipos.current
}

@Composable
fun TemaCompas(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    val p = if (oscuro) PaletaOscura else PaletaClara
    val esquema = if (oscuro) {
        darkColorScheme(
            primary = p.acento, onPrimary = p.sobreAcento, background = p.fondo, onBackground = p.tinta,
            surface = p.superficie, onSurface = p.tinta, surfaceVariant = p.superficie2, onSurfaceVariant = p.tinta2,
            outline = p.linea, outlineVariant = p.linea, error = p.mal,
        )
    } else {
        lightColorScheme(
            primary = p.acento, onPrimary = p.sobreAcento, background = p.fondo, onBackground = p.tinta,
            surface = p.superficie, onSurface = p.tinta, surfaceVariant = p.superficie2, onSurfaceVariant = p.tinta2,
            outline = p.linea, outlineVariant = p.linea, error = p.mal,
        )
    }
    CompositionLocalProvider(LocalPaleta provides p, LocalTipos provides Tipos()) {
        MaterialTheme(colorScheme = esquema, content = contenido)
    }
}
