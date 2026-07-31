import {
  recalcularPlan,
  recalcularSeccion,
  retrasoMaximoAbsorbible,
  indiceAncla,
  horariosOriginales,
  estadoEnVivo,
  frasePunto,
  formatHora,
  formatDelta,
} from './vendor/core/index.js';

const $ = (id) => document.getElementById(id);

const estado = {
  datos: null,
  plan: null,
  seccionId: null,
  retraso: 0,
  resultado: null,
  enVivo: false,
  horaSim: null,
};

/* ---------- hora actual, en la misma escala que el itinerario ---------- */

function ahoraMin() {
  let base;
  if (estado.horaSim != null) {
    base = estado.horaSim;
  } else {
    const d = new Date();
    base = d.getHours() * 60 + d.getMinutes() + d.getSeconds() / 60;
  }
  // Si el itinerario cruza medianoche y el reloj ya ha dado la vuelta,
  // hay que sumarle el día. Sin esto, la madrugá va al revés.
  const h = estado.resultado?.horarios;
  if (h && h[h.length - 1].nueva > 1440 && base < h[0].nueva - 720) base += 1440;
  return base;
}

/* ---------- audio: bucle casi silencioso para retener la sesión ---------- */
/* El botón de los auriculares solo llega si hay audio realmente sonando.
   Generamos un WAV de un segundo a amplitud mínima (no cero: algunos
   dispositivos consideran el silencio absoluto como "sin reproducción"). */

function wavCasiSilencioso(segundos = 1, frecuencia = 8000) {
  const n = segundos * frecuencia;
  const buf = new ArrayBuffer(44 + n * 2);
  const v = new DataView(buf);
  const txt = (off, s) => [...s].forEach((c, i) => v.setUint8(off + i, c.charCodeAt(0)));
  txt(0, 'RIFF'); v.setUint32(4, 36 + n * 2, true); txt(8, 'WAVEfmt ');
  v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, frecuencia, true); v.setUint32(28, frecuencia * 2, true);
  v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  txt(36, 'data'); v.setUint32(40, n * 2, true);
  for (let i = 0; i < n; i++) v.setInt16(44 + i * 2, i % 2 ? 1 : -1, true);
  return URL.createObjectURL(new Blob([buf], { type: 'audio/wav' }));
}

let audio = null;
let wakeLock = null;

async function arrancarVivo() {
  if (!audio) {
    audio = new Audio(wavCasiSilencioso());
    audio.loop = true;
    audio.volume = 0.02;
  }
  await audio.play();

  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: estado.resultado?.nombre ?? 'Itinerario',
      artist: estado.plan?.nombre ?? '',
      album: estado.datos?.hermandad ?? '',
    });
    navigator.mediaSession.playbackState = 'playing';
    const decir = () => {
      hablarSiguiente();
      // El botón alterna play/pause: lo devolvemos siempre a "sonando"
      // para que la siguiente pulsación vuelva a llegar.
      navigator.mediaSession.playbackState = 'playing';
      if (audio.paused) audio.play().catch(() => {});
    };
    for (const accion of ['play', 'pause', 'nexttrack', 'previoustrack', 'stop']) {
      try { navigator.mediaSession.setActionHandler(accion, decir); } catch { /* no soportado */ }
    }
  }

  try { wakeLock = await navigator.wakeLock?.request('screen'); } catch { /* opcional */ }

  estado.enVivo = true;
  $('vivo').dataset.activo = '1';
  $('vivo').textContent = 'Modo en vivo activo — pulsa para parar';
  $('tarjeta').hidden = false;
  hablarSiguiente();
}

async function pararVivo() {
  audio?.pause();
  try { await wakeLock?.release(); } catch { /* da igual */ }
  wakeLock = null;
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'paused';
  speechSynthesis.cancel();
  estado.enVivo = false;
  $('vivo').dataset.activo = '0';
  $('vivo').textContent = 'Empezar modo en vivo';
  $('tarjeta').hidden = true;
}

/* ---------- voz ---------- */

function hablar(texto) {
  speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(texto);
  u.lang = 'es-ES';
  u.rate = 1.0;
  const voz = speechSynthesis.getVoices().find((v) => v.lang.startsWith('es'));
  if (voz) u.voice = voz;
  speechSynthesis.speak(u);
}

function hablarSiguiente() {
  if (!estado.resultado) return;
  const t = ahoraMin();
  hablar(frasePunto(estado.resultado, t));
  pintarTarjeta(t);
}

function pintarTarjeta(t) {
  const e = estadoEnVivo(estado.resultado, t);
  if (e.terminado) {
    $('cifra').textContent = '✓';
    $('destino').textContent = 'Itinerario terminado';
  } else {
    $('cifra').textContent = Math.max(0, Math.round(e.faltan));
    $('destino').textContent = e.siguiente.nombre;
  }
  $('reloj').textContent = `son las ${formatHora(t)}`;
}

/* ---------- render ---------- */

function recalcular() {
  estado.resultado = recalcularSeccion(estado.plan, estado.seccionId, estado.retraso);
  const plan = recalcularPlan(estado.plan, estado.retraso);
  const r = estado.resultado;
  const a = indiceAncla(estado.plan);

  $('retrasoValor').textContent = estado.retraso;
  $('margen').textContent = `${r.margenRestante}′`;
  $('maximo').textContent = `${retrasoMaximoAbsorbible(estado.plan)}′`;
  $('acel').textContent = r.aceleracionAndar
    ? `+${(r.aceleracionAndar * 100).toFixed(1)}%`
    : '—';
  $('anclaSalida').textContent = `salida ${formatHora(r.nuevaSalida)}`;
  $('anclaNombre').textContent = estado.plan.puntos[a].nombre;

  pintarRegleta(a);
  pintarEstado(r, plan);
  pintarTabla(r);
  if (estado.enVivo) pintarTarjeta(ahoraMin());
}

function pintarRegleta(a) {
  const seccion = estado.plan.secciones.find((s) => s.id === estado.seccionId);
  const r = estado.resultado;
  const trozos = [];
  for (let i = 0; i < a; i++) {
    const original = seccion.tramos[i];
    const andarNuevo = r.horarios[i + 1].nueva - r.horarios[i].nueva - (original.paron - comido(i));
    trozos.push(`<i class="andar" style="flex-grow:${original.andar}"></i>`);
    const queda = original.paron - comido(i);
    if (queda > 0) trozos.push(`<i class="paron" style="flex-grow:${queda}"></i>`);
    if (comido(i) > 0) trozos.push(`<i class="comido" style="flex-grow:${comido(i)}"></i>`);
  }
  $('barra').innerHTML = trozos.join('');

  function comido(i) {
    const huecoOriginal = seccion.tramos[i].andar + seccion.tramos[i].paron;
    const huecoNuevo = r.horarios[i + 1].nueva - r.horarios[i].nueva;
    return huecoOriginal - huecoNuevo;
  }
}

function pintarEstado(r, plan) {
  const avisos = [...r.avisos, ...plan.avisosCortejo];
  const clase = r.viable ? 'ok' : 'mal';
  const titulo = r.viable
    ? `Llegas al punto fijo a las ${formatHora(r.horarios.find((h) => h.ancla).nueva)}.`
    : 'No se llega al punto fijo con este retraso.';
  $('estado').innerHTML = avisos.length
    ? `<div class="banda ${clase}"><strong>${titulo}</strong><ul>${
        avisos.map((a) => `<li>${escapar(a)}</li>`).join('')}</ul></div>`
    : `<div class="banda ${clase}"><strong>${titulo}</strong></div>`;
}

function pintarTabla(r) {
  const t = ahoraMin();
  $('tabla').innerHTML = r.horarios
    .map((h) => {
      const clases = [h.ancla ? 'es-ancla' : '', h.nueva < t ? 'pasado' : ''].join(' ');
      const hora = h.delta
        ? `<span class="tachado">${formatHora(h.original)}</span>${formatHora(h.nueva)}`
        : formatHora(h.nueva);
      return `<tr class="${clases}"><td class="hora">${hora}</td><td>${
        escapar(h.nombre)}</td><td class="delta">${h.delta ? formatDelta(h.delta) : ''}</td></tr>`;
    })
    .join('');
}

const escapar = (s) =>
  String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

/* ---------- arranque ---------- */

function elegirPlan(id) {
  estado.plan = estado.datos.planes.find((p) => p.id === id);
  $('seccion').innerHTML = estado.plan.secciones
    .map((s) => `<option value="${s.id}">${escapar(s.nombre)}</option>`)
    .join('');
  estado.seccionId = estado.plan.secciones[0].id;
  recalcular();
}

async function iniciar() {
  const res = await fetch('planes.json');
  estado.datos = await res.json();
  $('hermandad').textContent = estado.datos.hermandad.replace(/-/g, ' ');
  $('plan').innerHTML = estado.datos.planes
    .map((p) => `<option value="${p.id}">${escapar(p.nombre)}</option>`)
    .join('');
  elegirPlan(estado.datos.planes[0].id);

  $('plan').onchange = (e) => elegirPlan(e.target.value);
  $('seccion').onchange = (e) => { estado.seccionId = e.target.value; recalcular(); };
  $('mas').onclick = () => { estado.retraso += 5; recalcular(); };
  $('menos').onclick = () => { estado.retraso = Math.max(0, estado.retraso - 5); recalcular(); };
  $('vivo').onclick = () => (estado.enVivo ? pararVivo() : arrancarVivo());
  $('horaSim').onchange = (e) => {
    if (!e.target.value) return;
    const [h, m] = e.target.value.split(':').map(Number);
    estado.horaSim = h * 60 + m;
    recalcular();
  };
  $('limpiarHora').onclick = () => {
    estado.horaSim = null;
    $('horaSim').value = '';
    recalcular();
  };

  setInterval(() => { if (estado.enVivo) pintarTarjeta(ahoraMin()); }, 15000);
  speechSynthesis.getVoices();

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
}

iniciar();
