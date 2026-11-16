import { describe, expect, it } from 'vitest';
import { parseHora, formatHora, normalizarSecuencia } from './tiempo.js';
import { repartirConTope } from './reparto.js';
import {
  horariosOriginales,
  recalcularPlan,
  recalcularSeccion,
  retrasoMaximoAbsorbible,
} from './recalculo.js';
import { estadoEnVivo, frasePunto } from './locucion.js';
import { validarPlan } from './validacion.js';
import type { Plan, Tramo } from './tipos.js';

const t = (andar: number, paron: number): Tramo => ({ andar, paron });

/** Plan mínimo de juguete: 5 puntos, ancla en el índice 3. */
function planDemo(): Plan {
  return {
    id: 'demo',
    nombre: 'Demo',
    hermandadId: 'test',
    puntos: [
      { id: 'p0', nombre: 'Salida', ancla: false },
      { id: 'p1', nombre: 'Plaza', ancla: false },
      { id: 'p2', nombre: 'Esquina', ancla: false },
      { id: 'p3', nombre: 'Campana', ancla: true },
      { id: 'p4', nombre: 'Entrada', ancla: false },
    ],
    secciones: [
      {
        id: 'cruz',
        nombre: 'Cruz de guía',
        salida: parseHora('14:00'),
        // hasta el ancla: 3 tramos, 60 min de andar + 30 de parón = 90 min
        tramos: [t(20, 10), t(20, 10), t(20, 10), t(30, 0)],
      },
      {
        id: 'cristo',
        nombre: 'Paso de Cristo',
        salida: parseHora('14:30'),
        tramos: [t(20, 10), t(20, 10), t(20, 10), t(30, 0)],
      },
    ],
  };
}

describe('tiempo', () => {
  it('parsea y formatea', () => {
    expect(parseHora('17:45')).toBe(1065);
    expect(formatHora(1065)).toBe('17:45');
  });

  it('rechaza basura', () => {
    expect(() => parseHora('25:00')).toThrow();
    expect(() => parseHora('nope')).toThrow();
  });

  it('cruza medianoche sin volverse loco', () => {
    const s = normalizarSecuencia(['23:30', '23:50', '00:20', '01:05']);
    expect(s).toEqual([1410, 1430, 1460, 1505]);
    expect(s.every((x, i) => i === 0 || x > s[i - 1])).toBe(true);
    expect(formatHora(s[2])).toBe('00:20');
  });
});

describe('reparto', () => {
  it('la suma es exacta, siempre', () => {
    for (const total of [1, 7, 13, 29, 30, 31, 97]) {
      const topes = [9, 4, 12, 7, 3];
      const r = repartirConTope(total, topes);
      const cap = topes.reduce((a, b) => a + b, 0);
      expect(r.reduce((a, b) => a + b, 0)).toBe(Math.min(total, cap));
      r.forEach((v, i) => expect(v).toBeLessThanOrEqual(topes[i]));
    }
  });

  it('no reparte a quien no tiene capacidad', () => {
    expect(repartirConTope(5, [0, 0, 10])).toEqual([0, 0, 5]);
  });

  it('satura sin pasarse', () => {
    expect(repartirConTope(100, [3, 2])).toEqual([3, 2]);
  });
});

describe('recálculo', () => {
  it('sin retraso no toca nada', () => {
    const p = planDemo();
    const r = recalcularSeccion(p, 'cruz', 0);
    expect(r.viable).toBe(true);
    expect(r.horarios.every((h) => h.delta === 0)).toBe(true);
    expect(formatHora(r.nuevaSalida)).toBe('14:00');
  });

  it('el ancla NO se mueve nunca', () => {
    const p = planDemo();
    for (const retraso of [0, 5, 15, 24, 27]) {
      const r = recalcularSeccion(p, 'cruz', retraso);
      const ancla = r.horarios.find((h) => h.ancla)!;
      expect(formatHora(ancla.nueva)).toBe('15:30');
      expect(ancla.delta).toBe(0);
    }
  });

  it('recorta de los parones antes que del andar', () => {
    const p = planDemo();
    // 3 parones de 10 min, mínimo 1 => 27 min disponibles
    const r = recalcularSeccion(p, 'cruz', 15);
    expect(r.recorteDeParones).toBe(15);
    expect(r.recorteDeAndar).toBe(0);
    expect(r.aceleracionAndar).toBe(0);
    expect(r.viable).toBe(true);
    expect(r.margenRestante).toBe(12);
  });

  it('la suma del recorte es exacta (no 32 en vez de 30)', () => {
    const p = planDemo();
    for (const retraso of [1, 7, 13, 20, 27]) {
      const r = recalcularSeccion(p, 'cruz', retraso);
      const salida = r.horarios[0];
      const ancla = r.horarios.find((h) => h.ancla)!;
      const span = ancla.nueva - salida.nueva;
      const spanOriginal = ancla.original - salida.original;
      expect(spanOriginal - span).toBe(retraso);
    }
  });

  it('avisa cuando ya no se llega', () => {
    const p = planDemo();
    // 27 min de parón disponible + 60 de andar. Un retraso de 40 exige
    // acelerar el andar un 21,7%: inviable.
    const r = recalcularSeccion(p, 'cruz', 40);
    expect(r.viable).toBe(false);
    expect(r.recorteDeParones).toBe(27);
    expect(r.recorteDeAndar).toBe(13);
    expect(r.aceleracionAndar).toBeGreaterThan(0.08);
    expect(r.avisos.join(' ')).toMatch(/no se recupera andando/i);
  });

  it('calcula el retraso máximo absorbible', () => {
    const p = planDemo();
    const max = retrasoMaximoAbsorbible(p);
    expect(recalcularPlan(p, max).viable).toBe(true);
    expect(recalcularPlan(p, max + 1).viable).toBe(false);
    // 27 de parón + 8% de 60 de andar = 27 + 4 = 31
    expect(max).toBe(31);
  });

  it('los horarios posteriores al ancla no se tocan', () => {
    const p = planDemo();
    const original = horariosOriginales(p.secciones[0]);
    const r = recalcularSeccion(p, 'cruz', 20);
    expect(r.horarios[4].nueva).toBe(original[4]);
    expect(r.horarios[4].delta).toBe(0);
  });

  it('vigila la separación entre secciones', () => {
    const p = planDemo();
    const r = recalcularPlan(p, 20);
    // Ambas secciones son idénticas, así que la separación se mantiene.
    expect(r.avisosCortejo).toEqual([]);
  });
});

describe('locución', () => {
  it('encuentra el siguiente punto', () => {
    const p = planDemo();
    const r = recalcularSeccion(p, 'cruz', 0);
    const e = estadoEnVivo(r, parseHora('14:25'));
    expect(e.siguiente?.nombre).toBe('Plaza');
    expect(e.faltan).toBe(5);
    expect(e.terminado).toBe(false);
  });

  it('detecta el final', () => {
    const p = planDemo();
    const r = recalcularSeccion(p, 'cruz', 0);
    expect(estadoEnVivo(r, parseHora('23:00')).terminado).toBe(true);
  });

  it('construye la frase y avisa en el ancla', () => {
    const p = planDemo();
    const r = recalcularSeccion(p, 'cruz', 0);
    const f = frasePunto(r, parseHora('15:20'));
    expect(f).toContain('Campana');
    expect(f).toContain('En 10 minutos');
    expect(f).toContain('horario fijo');
  });

  it('singulariza el minuto', () => {
    const p = planDemo();
    const r = recalcularSeccion(p, 'cruz', 0);
    expect(frasePunto(r, parseHora('14:29'))).toContain('En un minuto');
  });
});

describe('validación', () => {
  it('un plan sano no da errores', () => {
    expect(validarPlan(planDemo()).filter((i) => i.nivel === 'error')).toEqual([]);
  });

  it('detecta dos anclas', () => {
    const p = planDemo();
    p.puntos[1].ancla = true;
    expect(validarPlan(p).some((i) => /2 puntos ancla/.test(i.mensaje))).toBe(true);
  });

  it('detecta que un paso adelanta al anterior', () => {
    const p = planDemo();
    p.secciones[1].salida = parseHora('13:00');
    expect(validarPlan(p).some((i) => i.nivel === 'error' && /antes o a la vez/.test(i.mensaje))).toBe(true);
  });

  it('detecta espacios sobrantes en los nombres', () => {
    const p = planDemo();
    p.puntos[2].nombre = 'ANTIGUA  HERRADURA';
    expect(validarPlan(p).some((i) => /espacios sobrantes/.test(i.mensaje))).toBe(true);
  });
});
