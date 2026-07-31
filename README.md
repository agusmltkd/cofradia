# Itinerario de cofradía

Motor de horarios para cofradías, con recálculo de retrasos, y una PWA que
lo consume. El núcleo no sabe nada de Android ni de web: es TypeScript puro,
se testea solo, y lo usan las dos interfaces. Así deja de poder pasar que el
auricular diga una hora y la pantalla otra.

```
src/core/        el motor. Sin UI, sin dependencias, con tests
scripts/         importador del itineraries.json antiguo
pwa/             la app web (se despliega en GitHub Pages)
android/         (pendiente) la app nativa, para el modo en vivo
.github/         compilación del APK y despliegue de la PWA
```

## Arrancar

```bash
npm install
npm test                    # 22 tests del motor
npm run build               # compila el núcleo a pwa/vendor/core/
npm run importar -- ruta/al/itineraries.json    # regenera pwa/planes.json
npm run pwa                 # sirve la PWA en localhost:3000
```

## Las tres formas de trabajar sin Android Studio

**1. En el navegador, sin instalar nada.** Sube esto a un repo y abre
`github.dev` (pulsa `.` sobre el repo) o un Codespace. Editas, haces commit,
y los workflows compilan. Para la PWA no necesitas ni eso: GitHub Pages la
sirve sola.

**2. Solo Node.** `npm install && npm test` funciona en cualquier máquina con
Node 20+. El núcleo entero se desarrolla y se prueba así, sin SDK de Android.

**3. El APK, en la nube.** `.github/workflows/android.yml` compila la app
nativa en un runner de Ubuntu, que ya trae el SDK. Haces push y te descargas
el APK desde la pestaña Actions. Para firmar en release, mete estos secrets
en el repo:

| Secret | Qué es |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | contraseña del keystore |
| `KEY_ALIAS` | alias de la clave |
| `KEY_PASSWORD` | contraseña de la clave |

Luego lanza el workflow a mano con la opción **firmar** activada.

## El modelo de datos

Un plan tiene **una** lista de puntos. Cada sección (cruz de guía, Cristo,
Virgen) recorre esos mismos puntos con sus tiempos. El nombre del sitio
existe una vez, no nueve.

Cada tramo entre dos puntos es `{ andar, paron }`, no un número suelto.
Esa es la pieza que hace que el recálculo tenga sentido: **un retraso se
recupera comiéndose los parones, no andando más rápido.**

Un punto lleva `ancla: true`: es el de horario innegociable (el palquillo de
la Campana en Sevilla, la tribuna en Málaga). Es explícito a propósito —
buscarlo por nombre es frágil, y en los datos reales hay dos puntos que
contienen "Campana".

## Qué hace el motor

`recalcularSeccion(plan, seccionId, retraso)` devuelve el horario nuevo y,
lo más importante, **`viable: false`** cuando para llegar al ancla haría
falta acelerar el andar más de un 8%. Ese es el caso en el que hay que
avisar, no correr.

`retrasoMaximoAbsorbible(plan)` responde a la pregunta de verdad: cuánto se
puede perder antes de no llegar. Con los datos actuales de San Esteban salen
**48 minutos** — pero ojo, ese número depende del reparto andar/parón, que
ahora mismo está estimado.

## Lo que falta y no es opcional

El importador reparte cada tramo al 35% parado porque el JSON antiguo solo
tenía horas de paso. **Es una suposición.** Hasta que alguien que lleve el
paso revise esos números tramo a tramo, el recálculo es orientativo. Ese es
el trabajo de campo que hay que hacer antes de la próxima Semana Santa, y
no lo puede hacer el código.

Salen del importador otras cosas que revisar:

- `Plan Sol` y `Plan Agua` tienen **exactamente los mismos horarios**.
- El mismo punto está escrito de varias formas según la sección.
- "San Pancracio" aparece dos veces (ida y vuelta): cualquier búsqueda por
  nombre es ambigua ahí.

## Sobre el modo en vivo en web

La PWA mantiene un audio casi silencioso en bucle para que la Media Session
siga viva y el botón de los auriculares llegue. Funciona, pero sin foreground
service detrás: si entra una llamada o el sistema recupera memoria, se corta.
Además `speechSynthesis` se suspende con la pantalla apagada en Android.

Por eso la locución está construida con plantillas (`frasePunto`): todas las
piezas se pueden pregenerar como clips de audio y reproducirlas por el mismo
canal que mantiene la sesión. Ese es el siguiente paso para que el modo en
vivo aguante de verdad — y aun así, para el día de la cofradía, el nativo.
