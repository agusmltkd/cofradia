/**
 * Convierte el itineraries.json del APK al modelo nuevo.
 *
 *   npm run importar -- ruta/al/itineraries.json [salida.json]
 *
 * Además de convertir, escupe un informe con todo lo que huele mal en los
 * datos de origen. Léelo: casi todo lo que sale ahí es un fallo real que
 * hoy está tapado porque el código va por índice.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { normalizarSecuencia } from '../src/core/tiempo.js';
import { compararPlanes, validarPlan } from '../src/core/validacion.js';
import type { Plan, Punto, Seccion, Tramo } from '../src/core/tipos.js';

/** Fracción del hueco entre puntos que se supone parado, a falta de dato real. */
const RATIO_PARON = 0.35;
/** Qué se considera el punto de horario fijo. Ajusta por hermandad. */
const PATRON_ANCLA = /palquillo|^campana$/i;

type JsonAntiguo = Record<string, Record<string, { time: string; place: string }[]>>;

const limpiar = (s: string) => s.replace(/\s+/g, ' ').trim();

function idDesde(nombre: string, i: number): string {
  const slug = limpiar(nombre)
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40);
  return `${String(i).padStart(2, '0')}-${slug || 'punto'}`;
}

/** De varias grafías del mismo sitio, se queda con la más frecuente. */
function nombreCanonico(variantes: string[]): string {
  const cuenta = new Map<string, number>();
  for (const v of variantes) {
    const k = limpiar(v);
    cuenta.set(k, (cuenta.get(k) ?? 0) + 1);
  }
  return [...cuenta.entries()].sort((a, b) => b[1] - a[1] || b[0].length - a[0].length)[0][0];
}

function partirTramo(hueco: number): Tramo {
  const paron = Math.max(0, Math.round(hueco * RATIO_PARON));
  return { andar: hueco - paron, paron, estimado: true };
}

export function importar(crudo: JsonAntiguo, hermandadId: string): { planes: Plan[]; informe: string[] } {
  const informe: string[] = [];
  const planes: Plan[] = [];

  for (const [nombrePlan, secciones] of Object.entries(crudo)) {
    const nombresSeccion = Object.keys(secciones);
    const longitudes = nombresSeccion.map((n) => secciones[n].length);
    if (new Set(longitudes).size > 1) {
      informe.push(
        `[${nombrePlan}] ERROR: las secciones no tienen el mismo número de puntos (${longitudes.join(', ')}). No se pueden alinear por índice.`,
      );
      continue;
    }
    const n = longitudes[0];

    // Un punto, N grafías. Nos quedamos con una y avisamos de las demás.
    const puntos: Punto[] = [];
    for (let i = 0; i < n; i++) {
      const variantes = nombresSeccion.map((s) => secciones[s][i].place);
      const canonico = nombreCanonico(variantes);
      const distintas = new Set(variantes.map(limpiar));
      if (distintas.size > 1) {
        informe.push(
          `[${nombrePlan}] punto ${i}: escrito de ${distintas.size} formas distintas -> se usa "${canonico}". Variantes: ${[...distintas].map((v) => JSON.stringify(v)).join(', ')}`,
        );
      }
      puntos.push({ id: idDesde(canonico, i), nombre: canonico, ancla: false });
    }

    // Ancla.
    const candidatos = puntos.map((p, i) => (PATRON_ANCLA.test(p.nombre) ? i : -1)).filter((i) => i >= 0);
    const conCampana = puntos.map((p, i) => (/campana/i.test(p.nombre) ? i : -1)).filter((i) => i >= 0);
    if (conCampana.length > 1) {
      informe.push(
        `[${nombrePlan}] AVISO: ${conCampana.length} puntos contienen "Campana" (${conCampana.map((i) => `${i}:${puntos[i].nombre}`).join(', ')}). Buscar por nombre es frágil; por eso ahora el ancla es explícita.`,
      );
    }
    if (candidatos.length === 1) {
      puntos[candidatos[0]].ancla = true;
    } else if (conCampana.length > 0) {
      puntos[conCampana[0]].ancla = true;
      informe.push(`[${nombrePlan}] ancla puesta en el punto ${conCampana[0]} ("${puntos[conCampana[0]].nombre}"). VERIFÍCALO.`);
    } else {
      informe.push(`[${nombrePlan}] ERROR: no se ha encontrado el punto ancla. Márcalo a mano.`);
    }

    const secc: Seccion[] = nombresSeccion.map((nombreSec) => {
      const horas = normalizarSecuencia(secciones[nombreSec].map((p) => p.time));
      const tramos: Tramo[] = [];
      for (let i = 0; i + 1 < horas.length; i++) {
        const hueco = horas[i + 1] - horas[i];
        if (hueco <= 0) {
          informe.push(
            `[${nombrePlan}/${nombreSec}] ERROR: tramo de ${hueco} min entre "${puntos[i].nombre}" y "${puntos[i + 1].nombre}".`,
          );
        }
        tramos.push(partirTramo(Math.max(0, hueco)));
      }
      return {
        id: nombreSec.toLowerCase(),
        nombre: nombreSec,
        salida: horas[0],
        tramos,
      };
    });

    planes.push({
      id: nombrePlan.toLowerCase().replace(/\s+/g, '-'),
      nombre: nombrePlan,
      hermandadId,
      puntos,
      secciones: secc,
    });
  }

  for (const p of planes) {
    for (const inc of validarPlan(p)) {
      informe.push(`[${p.nombre}] ${inc.nivel.toUpperCase()}: ${inc.mensaje}`);
    }
  }
  for (const inc of compararPlanes(planes)) {
    informe.push(`[global] ${inc.nivel.toUpperCase()}: ${inc.mensaje}`);
  }

  informe.push(
    `NOTA: el reparto andar/parón se ha estimado al ${Math.round(RATIO_PARON * 100)}% parado en todos los tramos. Es una suposición. Hasta que el capataz revise esos números, el recálculo de retrasos es orientativo.`,
  );

  return { planes, informe };
}

const [entrada, salida = 'pwa/planes.json'] = process.argv.slice(2);
if (!entrada) {
  console.error('Uso: npm run importar -- <itineraries.json> [salida.json]');
  process.exit(1);
}
const crudo = JSON.parse(readFileSync(entrada, 'utf8')) as JsonAntiguo;
const { planes, informe } = importar(crudo, 'san-esteban');
writeFileSync(salida, JSON.stringify({ hermandad: 'san-esteban', planes }, null, 2));

console.log(`\n=== INFORME DE IMPORTACIÓN (${informe.length} entradas) ===\n`);
for (const l of informe) console.log('  · ' + l);
console.log(`\n=> ${planes.length} planes escritos en ${salida}\n`);
