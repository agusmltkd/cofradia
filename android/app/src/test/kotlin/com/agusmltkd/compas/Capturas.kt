package com.agusmltkd.compas

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.agusmltkd.compas.ui.PrevActivacion
import com.agusmltkd.compas.ui.PrevActivacionOscuro
import com.agusmltkd.compas.ui.PrevAjustes
import com.agusmltkd.compas.ui.PrevEnVivo
import com.agusmltkd.compas.ui.PrevEnVivoClaro
import com.agusmltkd.compas.ui.PrevHorario
import com.agusmltkd.compas.ui.PrevInicio
import com.agusmltkd.compas.ui.PrevInicioOscuro
import org.junit.Rule
import org.junit.Test

/**
 * Capturas de las pantallas reales, renderizadas sin emulador.
 *   ./gradlew :app:recordPaparazziDebug
 * Las imágenes salen en app/src/test/snapshots/images/.
 */
class Capturas {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private fun alto(dp: Int) = paparazzi.unsafeUpdateConfig(
        deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = (dp * 2.625).toInt()),
    )

    @Test fun activacion() = paparazzi.snapshot { PrevActivacion() }
    @Test fun activacionOscuro() = paparazzi.snapshot { PrevActivacionOscuro() }
    @Test fun inicio() { alto(1180); paparazzi.snapshot { PrevInicio() } }
    @Test fun inicioOscuroNoViable() { alto(1180); paparazzi.snapshot { PrevInicioOscuro() } }
    @Test fun horario() = paparazzi.snapshot { PrevHorario() }
    @Test fun enVivo() { alto(960); paparazzi.snapshot { PrevEnVivo() } }
    @Test fun enVivoClaro() { alto(960); paparazzi.snapshot { PrevEnVivoClaro() } }
    @Test fun ajustes() { alto(1300); paparazzi.snapshot { PrevAjustes() } }
}
