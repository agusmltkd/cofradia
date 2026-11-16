package com.agusmltkd.compas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agusmltkd.compas.CompasApp
import com.agusmltkd.compas.core.DatosItinerario
import com.agusmltkd.compas.core.Incidencia
import com.agusmltkd.compas.core.Itinerario
import com.agusmltkd.compas.core.ResultadoPlan
import com.agusmltkd.compas.core.ResultadoSeccion
import com.agusmltkd.compas.core.Seccion
import com.agusmltkd.compas.core.recalcularPlan
import com.agusmltkd.compas.core.retrasoMaximoSeccion
import com.agusmltkd.compas.core.validar
import com.agusmltkd.compas.datos.ErrorApi
import com.agusmltkd.compas.datos.EstadoLicencia
import com.agusmltkd.compas.datos.Sesion
import com.agusmltkd.compas.vivo.ControladorVivo
import com.agusmltkd.compas.vivo.SesionVivo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Pantalla { INICIO, HORARIO, VIVO, AJUSTES }

data class Ajustes(
    val avisosAutomaticos: Boolean = true,
    val avisoPrevio: Boolean = true,
    val antelacion: Int = 120,
    val avisoAlLlegar: Boolean = true,
    val velocidadVoz: Float = 1f,
    val formato24h: Boolean = false,
    val tema: Int = 0,
)

/** Cálculo derivado de la selección actual. */
data class Calculo(
    val itinerario: Itinerario,
    val seccion: Seccion,
    val plan: ResultadoPlan,
    val resultado: ResultadoSeccion,
    val maximo: Int,
    val incidencias: List<Incidencia>,
) {
    val datos: DatosItinerario get() = itinerario.datos
    val bloqueante: Boolean get() = incidencias.any { it.grave }
}

data class UiEstado(
    val cargando: Boolean = true,
    val sesion: Sesion? = null,
    val pantalla: Pantalla = Pantalla.INICIO,
    val itinerarioId: String? = null,
    val seccionId: String? = null,
    val retraso: Int = 0,
    val calculo: Calculo? = null,
    val activando: Boolean = false,
    val errorActivacion: String? = null,
    val sincronizando: Boolean = false,
    val aviso: String? = null,
    val ajustes: Ajustes = Ajustes(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CompasApp).repositorio
    private val vivo = ControladorVivo(app)

    private val _estado = MutableStateFlow(UiEstado())
    val estado: StateFlow<UiEstado> = _estado

    init {
        val a = repo.ajustes
        _estado.update {
            it.copy(
                ajustes = Ajustes(a.avisosAutomaticos, a.avisoPrevio, a.antelacionSegundos, a.avisoAlLlegar, a.velocidadVoz, a.formato24h, a.temaOscuro),
            )
        }
        val s = repo.sesionGuardada()
        ponerSesion(s)
        _estado.update { it.copy(cargando = false, pantalla = if (SesionVivo.activo.value) Pantalla.VIVO else Pantalla.INICIO) }
        if (s != null && !s.esDemo) sincronizar(silencioso = true)
    }

    private fun ponerSesion(s: Sesion?) {
        _estado.update { e ->
            if (s == null) return@update e.copy(sesion = null, calculo = null)
            val it0 = s.itinerarios.firstOrNull { it.id == e.itinerarioId } ?: s.itinerarios.firstOrNull()
            val secs = it0?.datos?.secciones.orEmpty()
            val sec = secs.firstOrNull { it.id == e.seccionId }
                ?: secs.firstOrNull { it.id == s.seccionPreferida }
                ?: secs.firstOrNull()
            e.copy(sesion = s, itinerarioId = it0?.id, seccionId = sec?.id)
        }
        recalcular()
    }

    private fun recalcular() {
        _estado.update { e ->
            val s = e.sesion ?: return@update e.copy(calculo = null)
            val it0 = s.itinerarios.firstOrNull { it.id == e.itinerarioId } ?: return@update e.copy(calculo = null)
            val sec = it0.datos.secciones.firstOrNull { it.id == e.seccionId } ?: return@update e.copy(calculo = null)
            val inc = validar(it0.datos)
            val calc = if (inc.any { it.grave }) {
                null
            } else {
                val plan = recalcularPlan(it0.datos, e.retraso)
                Calculo(
                    itinerario = it0,
                    seccion = sec,
                    plan = plan,
                    resultado = plan.secciones.first { it.seccion.id == sec.id },
                    maximo = retrasoMaximoSeccion(it0.datos, sec),
                    incidencias = inc,
                )
            }
            e.copy(calculo = calc)
        }
    }

    // ---------------------------------------------------------------- acciones

    fun ir(p: Pantalla) = _estado.update { it.copy(pantalla = p) }

    fun elegirItinerario(id: String) {
        _estado.update { it.copy(itinerarioId = id) }
        ponerSesion(_estado.value.sesion)
    }

    fun elegirSeccion(id: String) {
        _estado.update { it.copy(seccionId = id) }
        recalcular()
    }

    fun cambiarRetraso(min: Int) {
        _estado.update { it.copy(retraso = min) }
        recalcular()
    }

    fun activar(codigo: String) {
        _estado.update { it.copy(activando = true, errorActivacion = null) }
        viewModelScope.launch {
            try {
                ponerSesion(repo.activar(codigo))
                _estado.update { it.copy(pantalla = Pantalla.INICIO) }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(errorActivacion = e.message) }
            } catch (e: Exception) {
                _estado.update { it.copy(errorActivacion = "No se pudo activar: ${e.message}") }
            } finally {
                _estado.update { it.copy(activando = false) }
            }
        }
    }

    fun entrarDemo() = ponerSesion(repo.entrarDemo())

    fun sincronizar(silencioso: Boolean = false) {
        if (_estado.value.sincronizando) return
        _estado.update { it.copy(sincronizando = true) }
        viewModelScope.launch {
            try {
                ponerSesion(repo.sincronizar())
                if (!silencioso) avisar("Itinerarios y licencia al día.")
            } catch (e: ErrorApi) {
                when (e.codigo) {
                    // La hermandad dio de baja este móvil o ya no tiene licencia.
                    "revocado", "credenciales" -> {
                        repo.salir()
                        ponerSesion(null)
                        _estado.update { it.copy(errorActivacion = e.message) }
                    }
                    "sin_licencia" -> {
                        _estado.value.sesion?.let { ponerSesion(it.copy(licencia = EstadoLicencia.Invalida)) }
                        avisar(e.message ?: "")
                    }
                    else -> if (!silencioso) avisar(e.message ?: "")
                }
            } finally {
                _estado.update { it.copy(sincronizando = false) }
            }
        }
    }

    fun salir() {
        viewModelScope.launch { if (SesionVivo.activo.value) vivo.detener() }
        repo.salir()
        _estado.update { UiEstado(cargando = false, ajustes = it.ajustes) }
    }

    fun avisar(texto: String?) = _estado.update { it.copy(aviso = texto) }

    /** @param ensayo si true, el reloj se adelanta/atrasa para que la salida sea dentro de 2 min. */
    fun empezarVivo(ensayo: Boolean) {
        val e = _estado.value
        val c = e.calculo ?: return
        val s = e.sesion ?: return
        if (!s.puedeSalirEnVivo) {
            avisar("La licencia no está vigente. Conéctate a internet y sincroniza.")
            return
        }
        val horarios = c.resultado.horarios
        val base = SesionVivo.baseDia(horarios.first().nueva)
        val desfase = if (ensayo) {
            val objetivo = base + horarios.first().nueva * 60L - 120
            objetivo - System.currentTimeMillis() / 1000
        } else 0L
        val a = e.ajustes
        val antelaciones = buildList {
            if (a.avisoPrevio) add(a.antelacion.toLong())
            if (a.avisoAlLlegar) add(0L)
        }
        val cfg = SesionVivo.Config(
            horarios = horarios,
            hermandad = s.hermandad.nombreCorto ?: s.hermandad.nombre,
            seccion = c.seccion.nombre,
            itinerario = c.itinerario.nombre,
            retraso = e.retraso,
            baseDia = base,
            desfase = desfase,
            avisosAutomaticos = a.avisosAutomaticos,
            antelaciones = antelaciones,
            velocidadVoz = a.velocidadVoz,
            formato24h = a.formato24h,
        )
        viewModelScope.launch {
            try {
                vivo.empezar(cfg)
                ir(Pantalla.VIVO)
            } catch (ex: Exception) {
                avisar("No se pudo iniciar el modo en vivo: ${ex.message}")
            }
        }
    }

    fun hablar() = viewModelScope.launch { runCatching { vivo.hablar() } }
    fun proximos() = viewModelScope.launch { runCatching { vivo.proximos() } }

    fun detenerVivo() {
        viewModelScope.launch {
            runCatching { vivo.detener() }
            ir(Pantalla.INICIO)
        }
    }

    fun cambiarAjustes(nuevo: Ajustes) {
        val a = repo.ajustes
        a.avisosAutomaticos = nuevo.avisosAutomaticos
        a.avisoPrevio = nuevo.avisoPrevio
        a.antelacionSegundos = nuevo.antelacion
        a.avisoAlLlegar = nuevo.avisoAlLlegar
        a.velocidadVoz = nuevo.velocidadVoz
        a.formato24h = nuevo.formato24h
        a.temaOscuro = nuevo.tema
        _estado.update { it.copy(ajustes = nuevo) }
        // Si está en vivo, los cambios de voz y avisos se aplican al momento.
        SesionVivo.config.value?.let { cfg ->
            SesionVivo.config.value = cfg.copy(
                avisosAutomaticos = nuevo.avisosAutomaticos,
                velocidadVoz = nuevo.velocidadVoz,
                formato24h = nuevo.formato24h,
                antelaciones = buildList {
                    if (nuevo.avisoPrevio) add(nuevo.antelacion.toLong())
                    if (nuevo.avisoAlLlegar) add(0L)
                },
            )
        }
    }

    override fun onCleared() {
        vivo.soltar()
        super.onCleared()
    }
}
