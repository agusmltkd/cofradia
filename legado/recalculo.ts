import { repartirConTope } from './reparto.js';
import { formatHora } from './tiempo.js';
import type {
  HorarioPunto,
  Minutos,
  OpcionesRecalculo,
  Plan,
  ResultadoPlan,
  ResultadoSeccion,
  Seccion,
} from './tipos.js';

const POR_DEFECTO: Required<OpcionesRecalculo> = {
  paronMinimo: 1,
  aceleracionAndarMaxima: 0.08,
  derivaCortejoMaxima: 3,
};

/** Índice del punto ancla. Falla si no hay exactamente uno. */
export function indiceAncla(plan: Plan): number {
  const idx = plan.puntos
    .map((p, i) => (p.ancla ? i : -1))
    .filter((i) => i >= 0);
  if (idx.length === 0) {
    throw new Error(
      `El plan "${plan.nombre}" no tiene punto ancla. Marca el punto de horario fijo (Campana, tribuna...) con ancla: true.`,
    );
  }
  if (idx.length > 1) {
    throw new Error(
      `El plan "${plan.nombre}" tiene ${idx.length} puntos ancla. Solo puede haber uno.`,
    );
  }
  if (idx[0] === 0) {
    throw new Error(`El ancla no puede ser el punto de salida.`);
  }
  return idx[0];
}

/** Horas de paso originales de una sección, punto a punto. */
export function horariosOriginales(seccion: Seccion): Minutos[] {
  const out: Minutos[] = [seccion.salida];
  let t = seccion.salida;
  for (const tramo of seccion.tramos) {
    t += tramo.andar + tramo.paron;
    out.push(t);
  }
  return out;
}

export function recalcularSeccion(
  plan: Plan,
  seccionId: string,
  retraso: Minutos,
  opciones: OpcionesRecalculo = {},
): ResultadoSeccion {
  const op = { ...POR_DEFECTO, ...opciones };
  const seccion = plan.secciones.find((s) => s.id === seccionId);
  if (!seccion) throw new Error(`Sección desconocida: ${seccionId}`);
  if (seccion.tramos.length !== plan.puntos.length - 1) {
    throw new Error(
      `La sección "${seccion.nombre}" tiene ${seccion.tramos.length} tramos y el plan ${plan.puntos.length} puntos. Deberían ser ${plan.puntos.length - 1}.`,
    );
  }

  const a = indiceAncla(plan);
  const original = horariosOriginales(seccion);
  const avisos: string[] = [];
  const D = Math.max(0, Math.round(retraso));

  // Solo se comprime el tramo que va desde la salida hasta el ancla.
  // Lo de después del ancla no se toca: ya va con su horario del Consejo.
  const region = seccion.tramos.slice(0, a);
  const paronDisponible = region.map((t) => Math.max(0, t.paron - op.paronMinimo));
  const capacidadParones = paronDisponible.reduce((x, y) => x + y, 0);
  const andarTotal = region.reduce((x, t) => x + t.andar, 0);

  const recorteDeParones = Math.min(D, capacidadParones);
  const cortesParon = repartirConTope(recorteDeParones, paronDisponible);
  const recorteDeAndar = D - recorteDeParones;

  let cortesAndar = new Array<number>(region.length).fill(0);
  let aceleracionAndar = 0;
  let viable = true;

  if (recorteDeAndar > 0) {
    if (andarTotal <= 0) {
      viable = false;
      avisos.push('No queda de dónde recortar: los parones están al mínimo y no hay tiempo de andar.');
    } else {
      aceleracionAndar = recorteDeAndar / andarTotal;
      // Nunca dejamos un tramo de andar por debajo de la mitad, pase lo que pase.
      const topes = region.map((t) => Math.max(0, Math.floor(t.andar / 2)));
      cortesAndar = repartirConTope(recorteDeAndar, topes);
      const conseguido = cortesAndar.reduce((x, y) => x + y, 0);
      if (conseguido < recorteDeAndar) {
        viable = false;
        avisos.push(
          `Ni recortando al máximo se llega: faltan ${recorteDeAndar - conseguido} min.`,
        );
      }
      if (aceleracionAndar > op.aceleracionAndarMaxima) {
        viable = false;
        avisos.push(
          `Haría falta andar un ${(aceleracionAndar * 100).toFixed(1)}% más rápido durante todo el recorrido hasta el ancla (máximo admitido: ${(op.aceleracionAndarMaxima * 100).toFixed(0)}%). Esto no se recupera andando: hay que avisar.`,
        );
      }
    }
  }

  // Reconstrucción de horarios.
  const nueva: Minutos[] = new Array(original.length);
  nueva[0] = original[0] + D;
  for (let i = 0; i < region.length; i++) {
    const andar = region[i].andar - cortesAndar[i];
    const paron = region[i].paron - cortesParon[i];
    nueva[i + 1] = nueva[i] + andar + paron;
  }
  // A partir del ancla, el horario original manda.
  for (let i = a; i < original.length; i++) nueva[i] = original[i];

  if (viable && nueva[a] !== original[a]) {
    avisos.push(
      `Descuadre interno en el ancla (${formatHora(nueva[a])} vs ${formatHora(original[a])}). Revisa los datos.`,
    );
  }

  const margenRestante = capacidadParones - recorteDeParones;
  if (viable && margenRestante < 10) {
    avisos.push(
      `Solo quedan ${margenRestante} min de colchón en los parones. Cualquier incidencia extra ya no se absorbe.`,
    );
  }
  const alMinimo = cortesParon.filter((c, i) => c > 0 && c === paronDisponible[i]).length;
  if (alMinimo > 0) {
    avisos.push(`${alMinimo} parones se quedan en el mínimo de ${op.paronMinimo} min.`);
  }
  if (region.some((t) => t.estimado)) {
    avisos.push(
      'Algunos tramos tienen el reparto andar/parón estimado automáticamente. Revísalos con el capataz antes de fiarte del recálculo.',
    );
  }

  const horarios: HorarioPunto[] = plan.puntos.map((p, i) => ({
    puntoId: p.id,
    nombre: p.nombre,
    ancla: p.ancla,
    original: original[i],
    nueva: nueva[i],
    delta: nueva[i] - original[i],
  }));

  return {
    seccionId: seccion.id,
    nombre: seccion.nombre,
    viable,
    nuevaSalida: nueva[0],
    horarios,
    recorteNecesario: D,
    recorteDeParones,
    recorteDeAndar,
    aceleracionAndar,
    margenRestante,
    avisos,
  };
}

export function recalcularPlan(
  plan: Plan,
  retraso: Minutos,
  opciones: OpcionesRecalculo = {},
): ResultadoPlan {
  const op = { ...POR_DEFECTO, ...opciones };
  const secciones = plan.secciones.map((s) =>
    recalcularSeccion(plan, s.id, retraso, opciones),
  );
  const a = indiceAncla(plan);
  const avisosCortejo: string[] = [];

  // El cortejo tiene una longitud física: los nazarenos que van entre la cruz
  // de guía y el paso de Cristo no se comprimen. Si al recalcular la separación
  // entre secciones cambia mucho, el horario es incoherente sobre el terreno.
  for (let s = 0; s + 1 < secciones.length; s++) {
    const A = secciones[s];
    const B = secciones[s + 1];
    let peor = 0;
    let dondePeor = '';
    for (let i = 0; i <= a; i++) {
      const antes = B.horarios[i].original - A.horarios[i].original;
      const ahora = B.horarios[i].nueva - A.horarios[i].nueva;
      const deriva = Math.abs(ahora - antes);
      if (deriva > peor) {
        peor = deriva;
        dondePeor = plan.puntos[i].nombre;
      }
    }
    if (peor > op.derivaCortejoMaxima) {
      avisosCortejo.push(
        `La separación entre ${A.nombre} y ${B.nombre} se desvía hasta ${peor} min (en ${dondePeor}). El cortejo no se estira: revisa si hace falta ajustar a mano.`,
      );
    }
  }

  return {
    planId: plan.id,
    retraso: Math.max(0, Math.round(retraso)),
    viable: secciones.every((s) => s.viable),
    secciones,
    avisosCortejo,
  };
}

/**
 * El retraso máximo que todavía se puede absorber. Búsqueda binaria sobre
 * `recalcularPlan`. Es LA pregunta del diputado mayor de gobierno:
 * "¿cuánto puedo perder antes de que ya no llegue?".
 */
export function retrasoMaximoAbsorbible(
  plan: Plan,
  opciones: OpcionesRecalculo = {},
): Minutos {
  let lo = 0;
  let hi = 240;
  if (recalcularPlan(plan, hi, opciones).viable) return hi;
  while (lo < hi) {
    const mid = Math.ceil((lo + hi) / 2);
    if (recalcularPlan(plan, mid, opciones).viable) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}
