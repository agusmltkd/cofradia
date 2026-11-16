package com.agusmltkd.compas.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agusmltkd.compas.BuildConfig
import com.agusmltkd.compas.ui.pantallas.PantallaActivacion
import com.agusmltkd.compas.ui.pantallas.PantallaAjustes
import com.agusmltkd.compas.ui.pantallas.PantallaEnVivo
import com.agusmltkd.compas.ui.pantallas.PantallaHorario
import com.agusmltkd.compas.ui.pantallas.PantallaInicio
import com.agusmltkd.compas.ui.tema.Compas
import com.agusmltkd.compas.ui.tema.TemaCompas
import com.agusmltkd.compas.vivo.SesionVivo

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var ensayoPendiente: Boolean? = null

    private val pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Aunque lo deniegue, el modo en vivo funciona: la notificación de
        // reproducción multimedia está exenta en Android 13+.
        ensayoPendiente?.let { vm.empezarVivo(it) }
        ensayoPendiente = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { vm.estado.value.cargando }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val e by vm.estado.collectAsStateWithLifecycle()
            val oscuro = when (e.ajustes.tema) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            TemaCompas(oscuro) {
                Raiz(e)
            }
        }
    }

    @Composable
    private fun Raiz(e: UiEstado) {
        LaunchedEffect(e.aviso) {
            e.aviso?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                vm.avisar(null)
            }
        }
        // En vivo, la pantalla no se apaga sola mientras esté en primer plano.
        LaunchedEffect(e.pantalla) {
            if (e.pantalla == Pantalla.VIVO) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        BackHandler(enabled = e.pantalla == Pantalla.HORARIO || e.pantalla == Pantalla.AJUSTES) {
            vm.ir(Pantalla.INICIO)
        }

        Box(Modifier.fillMaxSize().background(Compas.p.fondo)) {
            when {
                e.cargando -> Unit
                e.sesion == null -> PantallaActivacion(e.activando, e.errorActivacion, vm::activar, vm::entrarDemo)
                e.pantalla == Pantalla.HORARIO -> PantallaHorario(e) { vm.ir(Pantalla.INICIO) }
                e.pantalla == Pantalla.AJUSTES -> PantallaAjustes(
                    e = e,
                    version = BuildConfig.VERSION_NAME,
                    alCambiar = vm::cambiarAjustes,
                    alSincronizar = { vm.sincronizar() },
                    alSalir = vm::salir,
                    alAbrirPrivacidad = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.URL_PRIVACIDAD))) },
                    alVolver = { vm.ir(Pantalla.INICIO) },
                )
                e.pantalla == Pantalla.VIVO -> {
                    val vivo by SesionVivo.estado.collectAsStateWithLifecycle()
                    val cfg by SesionVivo.config.collectAsStateWithLifecycle()
                    PantallaEnVivo(
                        estado = vivo,
                        seccion = cfg?.seccion ?: "",
                        hermandad = cfg?.hermandad ?: "",
                        ensayo = cfg?.esEnsayo == true,
                        alHablar = { vm.hablar() },
                        alProximos = { vm.proximos() },
                        alTerminar = vm::detenerVivo,
                        datosHermandad = e.sesion?.hermandad,
                        escudo = e.sesion?.escudo,
                    )
                }
                else -> PantallaInicio(
                    e = e,
                    alElegirItinerario = vm::elegirItinerario,
                    alElegirSeccion = vm::elegirSeccion,
                    alCambiarRetraso = vm::cambiarRetraso,
                    alEmpezar = ::empezar,
                    alIr = vm::ir,
                )
            }
        }
    }

    private fun empezar(ensayo: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ensayoPendiente = ensayo
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.empezarVivo(ensayo)
        }
    }
}
