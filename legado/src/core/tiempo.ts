import type { Minutos } from './tipos.js';

/** "17:45" -> 1065. No admite nada raro: si no casa, revienta. */
export function parseHora(s: string): Minutos {
  const m = /^\s*(\d{1,2}):(\d{2})\s*$/.exec(s);
  if (!m) throw new Error(`Hora inválida: ${JSON.stringify(s)}`);
  const h = Number(m[1]);
  const min = Number(m[2]);
  if (h > 23 || min > 59) throw new Error(`Hora fuera de rango: ${s}`);
  return h * 60 + min;
}

/** 1065 -> "17:45". 1500 -> "01:00" (día siguiente). */
export function formatHora(t: Minutos): string {
  const tt = ((Math.round(t) % 1440) + 1440) % 1440;
  const h = Math.floor(tt / 60);
  const m = tt % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/**
 * Convierte una secuencia de horas "HH:MM" en minutos monótonos crecientes,
 * sumando 24 h cada vez que la hora retrocede.
 *
 * Sin esto, una cofradía que entra a las 00:30 tendría la entrada *antes*
 * que la salida y todos los cálculos saldrían negativos. Es el bug clásico
 * de la madrugá.
 */
export function normalizarSecuencia(horas: string[]): Minutos[] {
  const out: Minutos[] = [];
  let vueltas = 0;
  let anterior = -Infinity;
  for (const h of horas) {
    const base = parseHora(h);
    if (base < anterior - 720) vueltas++; // retroceso grande = ha cambiado el día
    anterior = base;
    out.push(base + vueltas * 1440);
  }
  return out;
}

/** "+12" / "-5" / "en punto", para mostrar desviaciones. */
export function formatDelta(d: Minutos): string {
  if (d === 0) return 'en punto';
  return d > 0 ? `+${d}` : String(d);
}
