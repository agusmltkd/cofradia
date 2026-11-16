package com.agusmltkd.compas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepartoTest {
    @Test fun sumaExacta() {
        val topes = listOf(3, 7, 2, 9, 4, 1)
        for (total in 0..26) {
            val r = repartirConTope(total, topes)
            assertEquals("total=$total", minOf(total, topes.sum()), r.sum())
            r.forEachIndexed { i, v -> assertTrue(v in 0..topes[i]) }
        }
    }

    @Test fun sinCapacidad() {
        assertEquals(listOf(0, 0), repartirConTope(5, listOf(0, 0)))
        assertEquals(emptyList<Int>(), repartirConTope(5, emptyList()))
    }
}

class RecalculoTest {
    private val datos = Demo.contenido.itinerarios.first().datos
    private val ancla = indiceAncla(datos)

    @Test fun sinRetrasoNoCambiaNada() {
        val r = recalcularPlan(datos, 0)
        assertTrue(r.viable)
        r.secciones.forEach { s -> s.horarios.forEach { assertEquals(0, it.delta) } }
    }

    @Test fun elAnclaSeRespeta() {
        for (retraso in listOf(5, 10, 15)) {
            val r = recalcularPlan(datos, retraso)
            r.secciones.forEach { s ->
                assertEquals(retraso, s.horarios.first().delta)
                assertEquals("ancla con retraso $retraso", 0, s.horarios[ancla].delta)
                assertEquals(retraso, s.recorteDeParones + s.recorteDeAndar)
            }
        }
    }

    @Test fun losHorariosNoRetroceden() {
        val r = recalcularPlan(datos, 20)
        r.secciones.forEach { s ->
            s.horarios.zipWithNext().forEach { (a, b) -> assertTrue(b.nueva >= a.nueva) }
        }
    }

    @Test fun retrasoEnormeNoEsViable() {
        assertFalse(recalcularPlan(datos, 200).viable)
    }

    @Test fun maximoAbsorbibleEsFrontera() {
        val max = retrasoMaximoAbsorbible(datos)
        assertTrue(max in 1..239)
        assertTrue(recalcularPlan(datos, max).viable)
        assertFalse(recalcularPlan(datos, max + 1).viable)
    }

    @Test fun datosDemoSonValidos() {
        assertTrue(validar(datos).none { it.grave })
    }
}

class VivoTest {
    private val datos = Demo.contenido.itinerarios.first().datos
    private val horarios = recalcularSeccion(datos, datos.secciones.first(), 0).horarios
    private val salida = horarios.first().nueva * 60L

    @Test fun antesDeSalir() {
        val e = estadoVivo(horarios, salida - 600)
        assertEquals(Fase.ANTES_DE_SALIR, e.fase)
        assertEquals(600, e.segundosHastaSiguiente)
        val f = Locucion.frase(e)
        assertTrue(f, f.startsWith("Son las cinco y 50."))
        assertTrue(f, f.contains("dentro de 10 minutos"))
    }

    @Test fun enUnPunto() {
        val p = horarios[3]
        val e = estadoVivo(horarios, p.nueva * 60L + 5)
        assertEquals(p, e.enPunto)
        assertTrue(Locucion.frase(e).contains("Deberías estar en ${Locucion.nombreHablado(p.punto)}"))
    }

    @Test fun camino() {
        val p = horarios[3]
        val e = estadoVivo(horarios, p.nueva * 60L - 34)
        assertEquals(null, e.enPunto)
        assertEquals(p, e.siguiente)
        val f = Locucion.frase(e)
        assertTrue(f, f.contains(" y en 34 segundos deberías estar en "))
    }

    @Test fun terminado() {
        val e = estadoVivo(horarios, horarios.last().nueva * 60L + 120)
        assertEquals(Fase.TERMINADO, e.fase)
    }

    @Test fun horasHabladas() {
        assertEquals("Es la una y media", Locucion.hora(13 * 3600L + 30 * 60))
        assertEquals("Son las ocho en punto", Locucion.hora(20 * 3600L))
        assertEquals("Son las doce y 5", Locucion.hora(24 * 3600L + 5 * 60)) // madrugada
        assertEquals("Son las 20 y cuarto", Locucion.hora(20 * 3600L + 15 * 60, formato24h = true))
    }

    @Test fun duraciones() {
        assertEquals("1 segundo", Locucion.duracion(1))
        assertEquals("34 segundos", Locucion.duracion(34))
        assertEquals("1 minuto y 5 segundos", Locucion.duracion(65))
        assertEquals("3 minutos", Locucion.duracion(180))
        assertEquals("12 minutos", Locucion.duracion(12 * 60 + 40))
        assertEquals("1 hora y 5 minutos", Locucion.duracion(3900))
    }

    @Test fun nombresEnMayusculas() {
        assertEquals("Plaza san leandro final", Locucion.nombreHablado(Punto("x", "PLAZA SAN LEANDRO  FINAL")))
        assertEquals("Laraña Orfila", Locucion.nombreHablado(Punto("x", "Laraña Orfila")))
        assertEquals("Campana", Locucion.nombreHablado(Punto("x", "CAMPANA PALQUILLO", voz = "Campana")))
    }

    @Test fun avisos() {
        val av = programarAvisos(horarios, listOf(120, 0))
        assertEquals(horarios.size * 2, av.size)
        val t = horarios[2].nueva * 60L
        assertEquals(1, avisosPendientes(av, t - 1, t).size)
        // Un aviso de hace un minuto ya no se dice.
        assertEquals(0, avisosPendientes(av, t - 1, t + 60).size)
    }
}

class LicenciaTest {
    // Par de claves solo para tests (generado aparte). La clave real NO está aquí.
    private val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEElcNAFnPV2itPyK5ZWQ991jcBHoLMe4iOMw28Vipw0pnRZN1DVeiXXCjWQtIqPGa1xRSC3r3+K5YhACAAHYAZA=="
    private val token = "eyJ2IjoxLCJraWQiOiJ0ZXN0IiwiZGlkIjoiZDEiLCJoaWQiOiJoMSIsImhzIjoicHJ1ZWJhIiwiaG4iOiJIZXJtYW5kYWQgZGUgUHJ1ZWJhIiwicm9sIjoiZmlzY2FsIiwic2VjIjoiY3J1eiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxODAwMDAwMDAwLCJsaWMiOiIyMDI3LTEyLTMxIn0.Prl3_WRUos3TALGBQmElYkPMSQyKew7RNWNxLGk4Qe5O4Dy0tPKi8RRCnrBIv5b42U1nf5oeK-7OURO9S1E7IQ"
    private val v = VerificadorLicencia(pub)

    @Test fun valida() {
        val r = v.verificar(token, ahora = 1_750_000_000)
        assertTrue(r is ResultadoLicencia.Valida)
        assertEquals("Hermandad de Prueba", (r as ResultadoLicencia.Valida).carga.hn)
    }

    @Test fun caducada() {
        assertTrue(v.verificar(token, ahora = 1_800_000_001) is ResultadoLicencia.Caducada)
    }

    @Test fun relojAtrasado() {
        assertTrue(v.verificar(token, ahora = 1_750_000_000, maximoVisto = 1_760_000_000) is ResultadoLicencia.RelojManipulado)
    }

    @Test fun manipulada() {
        val cuerpo = token.substringBefore('.')
        val otra = cuerpo.dropLast(2) + (if (cuerpo.endsWith("x0")) "y0" else "x0")
        assertTrue(v.verificar("$otra.${token.substringAfter('.')}", 1_750_000_000) !is ResultadoLicencia.Valida)
        assertEquals(ResultadoLicencia.Malformada, v.verificar("basura", 1))
    }
}
