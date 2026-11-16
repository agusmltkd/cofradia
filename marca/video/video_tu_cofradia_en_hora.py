"""
Vídeo vertical «Tu cofradía, en hora» (1080x1920, 30 fps, ~27 s, sin sonido).
La locución se añade después (ElevenLabs + CapCut). Guion en guion.txt.

    python3 marca/video/video_tu_cofradia_en_hora.py [--fotogramas]

Necesita: Pillow, ffmpeg, las capturas de la rama `capturas` en CAPTURAS.
"""
import json, math, os, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parents[2]
SALIDA = Path(os.environ.get("SALIDA", RAIZ / "marca/video/salida"))
CAPTURAS = Path(os.environ.get("CAPTURAS", RAIZ / "marca/video/capturas"))
W, H, FPS = 1080, 1920, 30

FONDO = (14, 14, 13)
TINTA = (241, 236, 226)
TINTA2 = (166, 159, 147)
TINTA3 = (110, 104, 95)
ORO = (216, 162, 74)
LINEA = (45, 42, 38)

F_SB = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"
F_MD = "/usr/share/fonts/opentype/inter/Inter-Medium.otf"
F_RG = "/usr/share/fonts/opentype/inter/Inter-Regular.otf"
_fuentes = {}


def fuente(ruta, tam):
    k = (ruta, tam)
    if k not in _fuentes:
        f = ImageFont.truetype(ruta, tam)
        try:
            f.set_variation_by_axes  # noqa
        except Exception:
            pass
        _fuentes[k] = f
    return _fuentes[k]


def tnum(ruta, tam):
    """Fuente con cifras tabulares para relojes."""
    return fuente(ruta, tam)


P = json.loads((RAIZ / "marca/puntos.json").read_text())


# ---------------------------------------------------------------- utilidades
def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def ease(x):
    x = clamp(x)
    return 1 - (1 - x) ** 3


def ease_io(x):
    x = clamp(x)
    return 3 * x * x - 2 * x * x * x


def tramo(t, a, b):
    return clamp((t - a) / (b - a)) if b > a else float(t >= a)


def mezcla(c, fondo, alfa):
    return tuple(round(fondo[i] + (c[i] - fondo[i]) * alfa) for i in range(3))


def texto(d, xy, s, f, color, alfa=1.0, anchor="mm", fondo=FONDO):
    if alfa <= 0.001:
        return
    d.text(xy, s, font=f, fill=mezcla(color, fondo, alfa), anchor=anchor, features=["tnum"])


def lienzo():
    im = Image.new("RGB", (W, H), FONDO)
    return im, ImageDraw.Draw(im)


def simbolo(d, cx, cy, escala, progreso=1.0, alfa=1.0):
    """La S de paradas; progreso 0..1 revela las paradas en orden y el rombo al final."""
    n = len(P) - 1
    for i, (x, y) in enumerate(P[:-1]):
        a = ease(tramo(progreso * (n + 1), i, i + 1)) * alfa
        if a <= 0:
            continue
        r = (4.8 if i == 0 else 3.4) * escala * (0.6 + 0.4 * a)
        X, Y = cx + (x - 54) * escala, cy + (y - 57) * escala
        d.ellipse([X - r, Y - r, X + r, Y + r], fill=mezcla(TINTA, FONDO, a))
    a = ease(tramo(progreso * (n + 1), n, n + 1)) * alfa
    if a > 0:
        x, y = P[-1]
        X, Y = cx + (x - 54) * escala, cy + (y - 57) * escala
        s = 7.6 * escala * (0.4 + 0.6 * a)
        d.polygon([(X, Y - s), (X + s, Y), (X, Y + s), (X - s, Y)], fill=mezcla(ORO, FONDO, a))


def fundido(t, dur, entrada=0.35, salida=0.35):
    """Opacidad global de la escena: entra y sale desde negro."""
    return min(ease(tramo(t, 0, entrada)), 1 - ease_io(tramo(t, dur - salida, dur)))


def oscurecer(im, k):
    if k >= 0.999:
        return im
    negro = Image.new("RGB", im.size, FONDO)
    return Image.blend(negro, im, k)


_cache = {}


def captura(nombre, ancho):
    k = (nombre, ancho)
    if k not in _cache:
        im = Image.open(CAPTURAS / f"{nombre}.png").convert("RGB")
        r = ancho / im.width
        im = im.resize((ancho, round(im.height * r)), Image.LANCZOS)
        mask = Image.new("L", im.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.width - 1, im.height - 1], radius=46, fill=255)
        _cache[k] = (im, mask)
    return _cache[k]


def movil(im, d, nombre, x, y, ancho, alto_visible):
    """Pega una captura como pantalla de móvil, con marco fino, recortada abajo."""
    cap, mask = captura(nombre, ancho)
    alto = min(cap.height, alto_visible)
    c = cap.crop((0, 0, ancho, alto))
    m = mask.crop((0, 0, ancho, alto))
    d.rounded_rectangle([x - 8, y - 8, x + ancho + 7, y + alto + 60], radius=54, fill=(26, 25, 23), outline=LINEA, width=3)
    im.paste(c, (int(x), int(y)), m)


# ---------------------------------------------------------------- escenas
def escena1(t, dur):
    """Martes Santo, 18:28. El reloj corre (misma hora que la captura en vivo)."""
    im, d = lienzo()
    texto(d, (W / 2, 760), "Martes Santo", fuente(F_MD, 64), TINTA2, ease(tramo(t, 0.2, 0.9)))
    seg = 30 + int(t)  # 18:28:30 → 18:28:33
    reloj = f"18:28:{seg:02d}"
    texto(d, (W / 2, 930), reloj, fuente(F_MD, 200), TINTA, ease(tramo(t, 0.6, 1.3)))
    # línea de tiempo bajo el reloj
    a = ease(tramo(t, 1.2, 2.2))
    if a > 0:
        x0, x1 = 240, 240 + 600 * a
        d.line([(240, 1080), (840, 1080)], fill=LINEA, width=4)
        d.line([(240, 1080), (x1, 1080)], fill=ORO, width=4)
    return oscurecer(im, fundido(t, dur))


def escena2(t, dur):
    """El fiscal no puede ir mirando el móvil."""
    im, d = lienzo()
    f = fuente(F_SB, 84)
    lineas = ["El fiscal de tramo", "no puede ir", "mirando el móvil."]
    for i, s in enumerate(lineas):
        a = ease(tramo(t, 0.25 + i * 0.45, 0.85 + i * 0.45))
        texto(d, (W / 2, 820 + i * 112 + (1 - a) * 30), s, f, TINTA if i < 2 else ORO, a)
    return oscurecer(im, fundido(t, dur))


def escena3(t, dur):
    """Pulsa el botón de los auriculares: la pantalla en vivo sube."""
    im, d = lienzo()
    texto(d, (W / 2, 210), "Pulsa el botón", fuente(F_SB, 76), TINTA, ease(tramo(t, 0.1, 0.7)))
    texto(d, (W / 2, 300), "de los auriculares", fuente(F_SB, 76), TINTA, ease(tramo(t, 0.3, 0.9)))
    ancho = 700
    y = 490 + (1 - ease(tramo(t, 0.2, 1.2))) * 900
    movil(im, d, "enVivo", (W - ancho) / 2, y, ancho, 1500)
    # pulsación: una píldora dorada bajo el título que «late» dos veces
    a = ease(tramo(t, 1.9, 2.3))
    if a > 0:
        pulso = 1 + 0.08 * math.sin(clamp((t - 2.3) / 1.2) * math.pi * 4) * (1 - tramo(t, 2.3, 3.5))
        w, h = 420 * pulso, 74 * pulso
        cx, cy = W / 2, 405
        d.rounded_rectangle([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], radius=h / 2, fill=mezcla(ORO, FONDO, a))
        texto(d, (cx, cy), "una pulsación", fuente(F_SB, 40), (22, 18, 10), a, fondo=ORO)
    return oscurecer(im, fundido(t, dur))


def escena4(t, dur):
    """La voz: la frase aparece palabra a palabra sobre una onda de puntos."""
    im, d = lienzo()
    # onda de puntos (como las paradas de la S)
    for i in range(15):
        x = 180 + i * 51.4
        amp = 0.5 + 0.5 * math.sin(t * 7 + i * 0.7)
        h = 10 + 50 * amp * ease(tramo(t, 0.1, 0.6)) * (1 - ease(tramo(t, dur - 0.8, dur - 0.2)))
        r = 9
        d.rounded_rectangle([x - r, 560 - h, x + r, 560 + h], radius=r, fill=TINTA if i != 7 else ORO)
    lineas = [("«Son las seis y 28,", TINTA), ("y en 3 minutos y 24", TINTA), ("segundos deberías estar", TINTA), ("en Plaza de la Merced.»", ORO)]
    f = fuente(F_SB, 74)
    for i, (s, c) in enumerate(lineas):
        a = ease(tramo(t, 0.5 + i * 0.75, 1.0 + i * 0.75))
        texto(d, (W / 2, 840 + i * 118 + (1 - a) * 24), s, f, c, a)
    texto(d, (W / 2, 1420), "Sin mirar la pantalla.", fuente(F_RG, 52), TINTA2, ease(tramo(t, 3.6, 4.2)))
    return oscurecer(im, fundido(t, dur))


def escena5(t, dur):
    """¿Salís con retraso? Recalcula para llegar a la Campana."""
    im, d = lienzo()
    texto(d, (W / 2, 210), "¿Salís con retraso?", fuente(F_SB, 76), TINTA, ease(tramo(t, 0.1, 0.7)))
    texto(d, (W / 2, 300), "Recalcula para llegar a la Campana.", fuente(F_RG, 50), TINTA2, ease(tramo(t, 1.0, 1.6)))
    ancho = 700
    y = 420 + (1 - ease(tramo(t, 0.2, 1.2))) * 900
    movil(im, d, "inicio", (W - ancho) / 2, y + 40, ancho, 1500)
    return oscurecer(im, fundido(t, dur))


def escena6(t, dur):
    """Cierre: la S se dibuja parada a parada, nombre y lema."""
    im, d = lienzo()
    simbolo(d, W / 2, 760, 7.2, progreso=ease_io(tramo(t, 0.2, 2.0)))
    texto(d, (W / 2, 1180), "Statio", fuente(F_SB, 150), TINTA, ease(tramo(t, 1.9, 2.5)))
    texto(d, (W / 2, 1300), "El horario de tu cofradía,", fuente(F_RG, 56), TINTA2, ease(tramo(t, 2.4, 3.0)))
    texto(d, (W / 2, 1372), "al oído.", fuente(F_RG, 56), TINTA2, ease(tramo(t, 2.6, 3.2)))
    return oscurecer(im, min(ease(tramo(t, 0, 0.35)), 1.0))


ESCENAS = [
    ("01", escena1, 3.5),
    ("02", escena2, 4.0),
    ("03", escena3, 5.0),
    ("04", escena4, 5.0),
    ("05", escena5, 5.0),
    ("06", escena6, 4.5),
]


# ---------------------------------------------------------------- render
def render_escena(nombre, fn, dur):
    SALIDA.mkdir(parents=True, exist_ok=True)
    tmp = SALIDA / f"_{nombre}.tmp.mp4"
    fin = SALIDA / f"escena-{nombre}.mp4"
    proc = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS), str(tmp)],
        stdin=subprocess.PIPE,
    )
    n = round(dur * FPS)
    for i in range(n):
        proc.stdin.write(fn(i / FPS, dur).tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise SystemExit(f"ffmpeg falló en la escena {nombre}")
    tmp.replace(fin)
    return fin


def main():
    if "--fotogramas" in sys.argv:
        SALIDA.mkdir(parents=True, exist_ok=True)
        momentos = {"01": 2.5, "02": 2.4, "03": 2.6, "04": 4.2, "05": 3.8, "06": 4.0}
        for nombre, fn, dur in ESCENAS:
            fn(momentos[nombre], dur).save(SALIDA / f"fotograma-{nombre}.png")
        print("Fotogramas en", SALIDA)
        return
    archivos = [render_escena(n, f, d) for n, f, d in ESCENAS]
    lista = SALIDA / "lista.txt"
    lista.write_text("".join(f"file '{a.name}'\n" for a in archivos))
    final = SALIDA / "statio-tu-cofradia-en-hora.mp4"
    tmp = SALIDA / "_final.tmp.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(lista), "-c", "copy",
                    "-movflags", "+faststart", str(tmp)], check=True)
    tmp.replace(final)
    print("Vídeo:", final)


if __name__ == "__main__":
    main()
