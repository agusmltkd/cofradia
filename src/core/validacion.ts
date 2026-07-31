import { horariosOriginales } from './recalculo.js';
import type { Plan } from './tipos.js';

export interface Incidencia {
  nivel: 'error' | 'aviso';
  mensaje: string;
}

/**
 * Repasa un plan buscando lo que rompe en producción.
 * Todo lo que hay aquí salió de mirar el JSON real de San Esteban.
 */
export function validarPlan(plan: Plan): Incidencia[] {
  const out: Incidencia[] = [];
  const err = (m: string) => out.push({ nivel: 'error', mensaje: m });
  const avi = (m: string) => out.push({ nivel: 'aviso', mensaje: m });

  if (plan.puntos.length < 2) err('El plan necesita al menos dos puntos.');

  const anclas = plan.puntos.filter((p) => p.ancla);
  if (anclas.length === 0) err('No hay punto ancla marcado.');
  if (anclas.length > 1)
    err(`Hay ${anclas.length} puntos ancla: ${anclas.map((p) => p.nombre).join(', ')}.`);
  if (plan.puntos[0]?.ancla) err('El punto de salida no puede ser el ancla.');

  const ids = new Set<string>();
  for (const p of plan.puntos) {
    if (ids.has(p.id)) err(`Id de punto duplicado: ${p.id}`);
    ids.add(p.id);
    if (p.nombre !== p.nombre.trim() || /\s{2,}/.test(p.nombre)) {
      avi(`Nombre con espacios sobrantes: ${JSON.stringify(p.nombre)}`);
    }
    if (!p.nombre.trim()) err(`El punto ${p.id} no tiene nombre.`);
  }

  const porNombre = new Map<string, string[]>();
  for (const p of plan.puntos) {
    const k = p.nombre.toLowerCase().replace(/\s+/g, ' ').trim();
    porNombre.set(k, [...(porNombre.get(k) ?? []), p.id]);
  }
  for (const [k, v] of porNombre) {
    if (v.length > 1) avi(`Dos puntos se llaman igual ("${k}"): ${v.join(', ')}.`);
  }

  if (plan.secciones.length === 0) err('El plan no tiene secciones.');

  for (const s of plan.secciones) {
    if (s.tramos.length !== plan.puntos.length - 1) {
      err(
        `La sección "${s.nombre}" tiene ${s.tramos.length} tramos; deberían ser ${plan.puntos.length - 1}.`,
      );
      continue;
    }
    s.tramos.forEach((t, i) => {
      const destino = plan.puntos[i + 1]?.nombre ?? `#${i + 1}`;
      if (t.andar < 0 || t.paron < 0)
        err(`Tramo negativo en "${s.nombre}" antes de ${destino}.`);
      if (t.andar + t.paron === 0)
        avi(`Tramo de 0 min en "${s.nombre}" antes de ${destino}.`);
      if (t.andar === 0 && t.paron > 0)
        avi(`Tramo sin tiempo de andar en "${s.nombre}" antes de ${destino}.`);
    });
    const estimados = s.tramos.filter((t) => t.estimado).length;
    if (estimados > 0)
      avi(`"${s.nombre}": ${estimados} de ${s.tramos.length} tramos tienen el reparto andar/parón estimado.`);
  }

  // El cortejo no puede adelantarse a sí mismo.
  for (let i = 0; i + 1 < plan.secciones.length; i++) {
    const A = horariosOriginales(plan.secciones[i]);
    const B = horariosOriginales(plan.secciones[i + 1]);
    for (let k = 0; k < A.length; k++) {
      if (B[k] <= A[k]) {
        err(
          `En ${plan.puntos[k].nombre}, "${plan.secciones[i + 1].nombre}" pasa antes o a la vez que "${plan.secciones[i].nombre}".`,
        );
        break;
      }
    }
  }

  return out;
}

/**
 * Compara planes entre sí. Un plan de agua que dura exactamente lo mismo
 * que el de sol casi siempre significa que se copió y no se ajustó.
 */
export function compararPlanes(planes: Plan[]): Incidencia[] {
  const out: Incidencia[] = [];
  for (let i = 0; i < planes.length; i++) {
    for (let j = i + 1; j < planes.length; j++) {
      const a = planes[i];
      const b = planes[j];
      if (a.secciones.length !== b.secciones.length) continue;
      const iguales = a.secciones.every((sa, k) => {
        const sb = b.secciones[k];
        const ha = horariosOriginales(sa);
        const hb = horariosOriginales(sb);
        return ha.length === hb.length && ha.every((t, n) => t === hb[n]);
      });
      if (iguales) {
        out.push({
          nivel: 'aviso',
          mensaje: `"${a.nombre}" y "${b.nombre}" tienen exactamente los mismos horarios. ¿Seguro que son planes distintos?`,
        });
      }
    }
  }
  return out;
}
