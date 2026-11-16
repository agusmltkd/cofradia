// El nombre de caché lo reescribe el workflow en cada despliegue.
const CACHE_NAME = 'itinerario-dev';
const ASSETS = [
  './',
  './index.html',
  './app.js',
  './planes.json',
  './manifest.webmanifest',
  './icono.svg',
  './vendor/core/index.js',
  './vendor/core/tipos.js',
  './vendor/core/tiempo.js',
  './vendor/core/reparto.js',
  './vendor/core/recalculo.js',
  './vendor/core/locucion.js',
  './vendor/core/validacion.js'
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE_NAME).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((ks) => Promise.all(ks.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Cache-first y sin excepciones: el día de la cofradía no se toca la red.
self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then((hit) => hit || fetch(e.request))
  );
});
