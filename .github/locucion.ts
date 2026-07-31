import { formatHora } from './tiempo.js';
import type { HorarioPunto, Minutos, ResultadoSeccion } from './tipos.js';

export interface EstadoEnVivo {
  /** Punto al que se dirige ahora la cofradía. null si ya ha terminado. */
  siguiente: HorarioPunto | null;
  /** Último punto ya rebasado según horario. */
  anterior: HorarioPunto | null;
  /** Minutos hasta el siguiente punto (negativo = ya deberías haber pasado). */
  faltan: Minutos;
  /** Desviación acumulada respecto al plan recalculado. */
  desviacion: Minutos;
  terminado: boolean;
}

/**
 * Dónde estamos según el horario recalculado.
 *
 * `ahora` va en minutos desde medianoche del día de salida, en la misma
 * escala que los horarios (puede pasar de 1440 en la madrugá).
 */
export function estadoEnVivo(
  resultado: ResultadoSeccion,
  ahora: Minutos,
): EstadoEnVivo {
  const h = resultado.horarios;
  const idx = h.findIndex((p) => p.nueva > ahora);

  if (idx === -1) {
    const ultimo = h[h.length - 1];
    return {
      siguiente: null,
      anterior: ultimo,
      faltan: 0,
      desviacion: ahora - ultimo.nueva,
      terminado: true,
    };
  }

  return {
    siguiente: h[idx],
    anterior: idx > 0 ? h[idx - 1] : null,
    faltan: h[idx].nueva - ahora,
    desviacion: 0,
    terminado: false,
  };
}

function minutosHablados(m: Minutos): string {
  if (m <= 0) return 'ya mismo';
  if (m === 1) return 'en un minuto';
  return `en ${m} minutos`;
}

/**
 * Frase que se le lee al diputado por el auricular.
 *
 * Está construida como plantilla a propósito: todas las piezas
 * (números del 0 al 120, nombres de los puntos, conectores) se pueden
 * pregenerar como clips de audio. Eso evita depender del TTS del sistema,
 * que en web se suspende con la pantalla apagada.
 */
export function frasePunto(
  resultado: ResultadoSeccion,
  ahora: Minutos,
): string {
  const e = estadoEnVivo(resultado, ahora);
  const hora = formatHora(ahora).replace(':', ' ');

  if (e.terminado) {
    return `Son las ${hora}. El itinerario ha terminado.`;
  }

  const s = e.siguiente!;
  const partes = [
    `Son las ${hora}.`,
    `${capitalizar(minutosHablados(e.faltan))} deberías estar en ${s.nombre}.`,
  ];

  if (s.ancla) {
    partes.push('Atención: es el punto de horario fijo.');
  }
  if (!resultado.viable) {
    partes.push('Aviso: con el retraso actual no se llega al punto fijo.');
  }
  return partes.join(' ');
}

/** Lectura completa del itinerario, para revisarlo antes de salir. */
export function fraseItinerarioCompleto(resultado: ResultadoSeccion): string {
  const cuerpo = resultado.horarios
    .map((p) => `${formatHora(p.nueva).replace(':', ' ')}, ${p.nombre}.`)
    .join(' ');
  return `Itinerario de ${resultado.nombre}. ${cuerpo} Fin del itinerario.`;
}

function capitalizar(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
