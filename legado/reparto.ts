/**
 * Reparte `total` minutos enteros entre varias ranuras, proporcionalmente
 * a su capacidad y sin pasarse del tope de ninguna.
 *
 * La garantía importante: la suma del resultado es EXACTAMENTE `total`
 * (o la capacidad total si `total` la supera). Redondear cada tramo por
 * separado es lo que hacía que un retraso de 30 minutos se convirtiera en
 * un recorte de 32.
 */
export function repartirConTope(total: number, topes: number[]): number[] {
  const n = topes.length;
  const out = new Array<number>(n).fill(0);
  const objetivo = Math.max(0, Math.round(total));
  if (objetivo === 0 || n === 0) return out;

  const capacidad = topes.reduce((a, b) => a + Math.max(0, b), 0);
  if (capacidad === 0) return out;
  if (objetivo >= capacidad) return topes.map((t) => Math.max(0, t));

  // Reparto proporcional en real. Como objetivo < capacidad, el ratio es < 1
  // y ninguna ranura puede saturar: no hace falta iterar.
  const ratio = objetivo / capacidad;
  const real = topes.map((t) => Math.max(0, t) * ratio);

  // Integerización por resto mayor, respetando topes.
  const base = real.map((x) => Math.floor(x));
  let falta = objetivo - base.reduce((a, b) => a + b, 0);
  for (let i = 0; i < n; i++) out[i] = base[i];

  const orden = real
    .map((x, i) => ({ i, frac: x - Math.floor(x) }))
    .sort((a, b) => b.frac - a.frac || a.i - b.i);

  let pasada = 0;
  while (falta > 0 && pasada < 2) {
    for (const { i } of orden) {
      if (falta === 0) break;
      if (out[i] < topes[i]) {
        out[i]++;
        falta--;
      }
    }
    pasada++;
  }
  return out;
}
