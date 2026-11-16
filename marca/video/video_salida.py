"""
Vídeo vertical «La salida» (1080x1920, 30 fps, ~31 s, sin sonido).
Hora de salida → el capataz saca el paso → Marcha Real → mini recorrido
(Banco de España, Catedral, Alfalfa) → entrada → cierre con la marca.
El sonido se monta después: ver guion-salida.txt.

    python3 marca/video/video_salida.py [--fotogramas]
"""
import math, subprocess, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from video_tu_cofradia_en_hora import (  # noqa: E402
    W, H, FPS, FONDO, TINTA, TINTA2, TINTA3, ORO, LINEA, F_SB, F_MD, F_RG,
    fuente, clamp, ease, ease_io, tramo, mezcla, texto, lienzo, simbolo, oscurecer, fundido, SALIDA,
)

F_IT = "/usr/share/fonts/opentype/inter/Inter-Italic.otf"
if not Path(F_IT).exists():
    F_IT = F_RG


# ---------------------------------------------------------------- piezas
def puerta(d, cx, base, ancho, alto, alfa):
    """Puerta de iglesia: arco de medio punto, solo línea, interior oscuro."""
    if alfa <= 0:
        return
    r = ancho / 2
    x0, x1 = cx - r, cx + r
    y_arco = base - alto + r
    interior = mezcla((6, 6, 5), FONDO, alfa)
    d.rectangle([x0, y_arco, x1, base], fill=interior)
    d.pieslice([x0, y_arco - r, x1, y_arco + r], 180, 360, fill=interior)
    c = mezcla(TINTA3, FONDO, alfa)
    d.arc([x0, y_arco - r, x1, y_arco + r], 180, 360, fill=c, width=6)
    d.line([(x0, y_arco), (x0, base)], fill=c, width=6)
    d.line([(x1, y_arco), (x1, base)], fill=c, width=6)
    # dovelas sugeridas
    d.arc([x0 - 28, y_arco - r - 28, x1 + 28, y_arco + r + 28], 180, 360, fill=mezcla(LINEA, FONDO, alfa), width=3)
    d.line([(x0 - 120, base), (x1 + 120, base)], fill=mezcla(LINEA, FONDO, alfa), width=4)


def paso(d, cx, base, escala, alfa, brillo=0.0):
    """Paso visto de frente, abstracto: canasto, respiradero, candelería y faldón."""
    if alfa <= 0:
        return
    w, h = 420 * escala, 150 * escala
    oro = mezcla(ORO, FONDO, alfa)
    oro_suave = mezcla(ORO, FONDO, alfa * (0.35 + 0.4 * brillo))
    x0, x1 = cx - w / 2, cx + w / 2
    y0 = base - h
    # faldón
    d.rectangle([x0 + 10 * escala, y0 + h * 0.55, x1 - 10 * escala, base], fill=mezcla((40, 30, 18), FONDO, alfa))
    # canasto
    d.rectangle([x0, y0, x1, y0 + h * 0.55], outline=oro, width=max(2, int(5 * escala)))
    d.line([(x0, y0 + h * 0.3), (x1, y0 + h * 0.3)], fill=oro_suave, width=max(1, int(3 * escala)))
    # candelería: cirios con llama
    for i in range(7):
        x = x0 + w * (0.12 + i * 0.127)
        alto = (60 + 25 * (1 - abs(i - 3) / 3)) * escala
        d.line([(x, y0), (x, y0 - alto)], fill=mezcla(TINTA, FONDO, alfa * 0.85), width=max(2, int(6 * escala)))
        fl = (9 + 2 * brillo) * escala
        d.ellipse([x - fl * 0.6, y0 - alto - fl * 1.9, x + fl * 0.6, y0 - alto], fill=oro)
    # silueta central (el misterio), solo insinuada
    d.rounded_rectangle([cx - 40 * escala, y0 - 230 * escala, cx + 40 * escala, y0], radius=30 * escala,
                        fill=mezcla((60, 50, 36), FONDO, alfa))
    d.ellipse([cx - 26 * escala, y0 - 285 * escala, cx + 26 * escala, y0 - 233 * escala], fill=mezcla((60, 50, 36), FONDO, alfa))


def grito(d, s, t, t0, t1, y=1560, tam=78, color=TINTA):
    """Orden del capataz: entra, se queda y se va."""
    a = min(ease(tramo(t, t0, t0 + 0.25)), 1 - ease(tramo(t, t1 - 0.25, t1)))
    if a > 0:
        texto(d, (W / 2, y - (1 - a) * 16), s, fuente(F_SB, tam), color, a)


def llamador(d, cx, cy, t, golpes):
    """Tres golpes del llamador: anillo dorado que late en cada golpe."""
    for g in golpes:
        p = tramo(t, g, g + 0.35)
        if 0 < p < 1:
            r = 30 + 70 * ease(p)
            d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=mezcla(ORO, FONDO, 1 - p), width=5)


def reloj(d, minutos_float, y=300, alfa=1.0, tam=150):
    m = int(minutos_float) % 1440
    texto(d, (W / 2, y), f"{m // 60:02d}:{m % 60:02d}", fuente(F_MD, tam), TINTA, alfa)


# ---------------------------------------------------------------- escenas
def e1_hora(t, dur):
    """La hora de salida: el reloj llega a las 14:40."""
    im, d = lienzo()
    texto(d, (W / 2, 780), "Martes Santo", fuente(F_MD, 60), TINTA2, ease(tramo(t, 0.1, 0.7)))
    seg = min(59, 52 + int(t * 2.2))
    ss = 0 if t > 3.1 else seg
    mm = 40 if t > 3.1 else 39
    texto(d, (W / 2, 940), f"14:{mm:02d}:{ss:02d}", fuente(F_MD, 190), TINTA, ease(tramo(t, 0.3, 1.0)))
    a = ease(tramo(t, 3.1, 3.5))
    texto(d, (W / 2, 1110), "HORA DE SALIDA", fuente(F_SB, 46), ORO, a)
    return oscurecer(im, fundido(t, dur, salida=0.3))


def e2_salida(t, dur):
    """El capataz saca el paso de la iglesia."""
    im, d = lienzo()
    base = 1330
    puerta(d, W / 2, base, 760, 1000, ease(tramo(t, 0.0, 0.6)))
    # el paso sale: crece hacia el espectador entre 2.8 s y 6.4 s
    sale = ease_io(tramo(t, 2.8, 6.6))
    levantada = ease(tramo(t, 2.0, 2.5))
    escala = 0.62 + 0.5 * sale
    cy = base - 40 * levantada * (1 - sale) + 300 * sale
    brillo = 0.5 + 0.5 * math.sin(t * 9)
    paso(d, W / 2, cy, escala, ease(tramo(t, 0.3, 1.0)), brillo)
    llamador(d, W / 2, 380, t, (0.9, 1.25, 1.6))
    grito(d, "¡A esta es!", t, 0.8, 1.9, y=1560)
    grito(d, "¡Al cielo con ella!", t, 1.9, 2.9, y=1560, color=ORO)
    grito(d, "Un poquito más…", t, 3.0, 4.0)
    grito(d, "Menos… menos…", t, 4.0, 5.0)
    grito(d, "¡De frente!", t, 5.0, 6.0)
    # la Marcha Real cuando el paso asoma
    a = min(ease(tramo(t, 6.0, 6.5)), 1 - ease(tramo(t, dur - 0.3, dur)))
    if a > 0:
        texto(d, (W / 2, 1780), "Suena la Marcha Real", fuente(F_MD, 52), TINTA2, a)
    return oscurecer(im, fundido(t, dur, entrada=0.3, salida=0.3))


PARADAS = [("14:40", "Salida", 0), ("17:05", "Banco de España", 1), ("17:40", "Catedral", 2), ("19:30", "Alfalfa", 3), ("21:10", "Entrada", 4)]
ORDENES = [("¡Vámonos valientes!", 0.6), ("¡Poquito a poco!", 3.0), ("¡Todos por igual!", 5.3), ("¡De frente, despacio!", 7.6), ("¡Arriba con ella!", 9.9)]


def e3_recorrido(t, dur):
    """Mini recorrido con la hora de cada punto; el marcador avanza y cada punto se marca «en hora»."""
    im, d = lienzo()
    a0 = ease(tramo(t, 0, 0.4))
    # reloj que corre de 14:40 a 21:10
    prog = ease_io(tramo(t, 0.3, dur - 1.0))
    pos_r = prog * (len(PARADAS) - 1)
    mins = [int(h[:2]) * 60 + int(h[3:]) for h, _, _ in PARADAS]
    i_r = min(int(pos_r), len(mins) - 2)
    minutos = mins[i_r] + (mins[i_r + 1] - mins[i_r]) * (pos_r - i_r)
    texto(d, (W / 2, 190), "EN VIVO · PASO DE CRISTO", fuente(F_SB, 34), ORO, a0)
    reloj(d, minutos, y=320, alfa=a0)
    # raíl vertical
    x_rail, y0, paso_y = 300, 560, 210
    ys = [y0 + i * paso_y for i in range(len(PARADAS))]
    d.line([(x_rail, ys[0]), (x_rail, ys[-1])], fill=mezcla(LINEA, FONDO, a0), width=6)
    pos = prog * (len(PARADAS) - 1)
    yb = ys[0] + (ys[-1] - ys[0]) * prog
    d.line([(x_rail, ys[0]), (x_rail, yb)], fill=mezcla(ORO, FONDO, a0), width=6)
    for i, (hora, nombre, _) in enumerate(PARADAS):
        y = ys[i]
        alcanzado = pos >= i - 0.02
        actual = abs(pos - i) < 0.5
        r = 18 if actual else 13
        if alcanzado:
            d.ellipse([x_rail - r, y - r, x_rail + r, y + r], fill=mezcla(ORO, FONDO, a0))
        else:
            d.ellipse([x_rail - r, y - r, x_rail + r, y + r], outline=mezcla(TINTA3, FONDO, a0), width=5, fill=FONDO)
        col = TINTA if (actual or alcanzado) else TINTA3
        texto(d, (x_rail - 50, y), hora, fuente(F_MD, 44), col, a0, anchor="rm")
        texto(d, (x_rail + 50, y), nombre, fuente(F_SB if actual else F_RG, 56 if actual else 50), col, a0, anchor="lm")
        if alcanzado and i > 0:
            a = ease(tramo(pos, i - 0.02, i + 0.25))
            texto(d, (x_rail + 50, y + 50), "en hora", fuente(F_MD, 34), (125, 184, 148), a * a0, anchor="lm")
    # marcador que avanza (la cofradía)
    d.ellipse([x_rail - 30, yb - 30, x_rail + 30, yb + 30], outline=mezcla(ORO, FONDO, a0), width=4)
    # órdenes del capataz de fondo
    for s, t0 in ORDENES:
        grito(d, s, t, t0, t0 + 1.9, y=1720, tam=50, color=TINTA2)
    return oscurecer(im, fundido(t, dur, entrada=0.3, salida=0.3))


def e4_entrada(t, dur):
    """La entrada: el paso vuelve a la iglesia. ¡Ahí quedó!"""
    im, d = lienzo()
    base = 1330
    puerta(d, W / 2, base, 760, 1000, ease(tramo(t, 0.0, 0.4)))
    entra = ease_io(tramo(t, 0.2, 2.2))
    escala = 1.12 - 0.5 * entra
    cy = base + 300 * (1 - entra)
    paso(d, W / 2, cy, escala, 1.0 - 0.6 * ease(tramo(t, 2.2, 3.0)), 0.6)
    grito(d, "¡Ahí quedó!", t, 2.0, dur, y=1560, color=ORO)
    return oscurecer(im, fundido(t, dur, entrada=0.3, salida=0.4))


def e5_cierre(t, dur):
    im, d = lienzo()
    simbolo(d, W / 2, 760, 7.2, progreso=ease_io(tramo(t, 0.2, 1.8)))
    texto(d, (W / 2, 1180), "Statio", fuente(F_SB, 150), TINTA, ease(tramo(t, 1.6, 2.2)))
    texto(d, (W / 2, 1300), "El horario de tu cofradía,", fuente(F_RG, 56), TINTA2, ease(tramo(t, 2.0, 2.6)))
    texto(d, (W / 2, 1372), "al oído.", fuente(F_RG, 56), TINTA2, ease(tramo(t, 2.2, 2.8)))
    return oscurecer(im, ease(tramo(t, 0, 0.35)))


ESCENAS = [("s1", e1_hora, 4.0), ("s2", e2_salida, 8.0), ("s3", e3_recorrido, 12.5), ("s4", e4_entrada, 3.5), ("s5", e5_cierre, 4.0)]


def render(nombre, fn, dur):
    SALIDA.mkdir(parents=True, exist_ok=True)
    tmp, fin = SALIDA / f"_{nombre}.tmp.mp4", SALIDA / f"salida-{nombre}.mp4"
    p = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
                          "-i", "-", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", str(tmp)],
                         stdin=subprocess.PIPE)
    for i in range(round(dur * FPS)):
        p.stdin.write(fn(i / FPS, dur).tobytes())
    p.stdin.close()
    if p.wait() != 0:
        raise SystemExit(f"ffmpeg falló en {nombre}")
    tmp.replace(fin)
    return fin


def main():
    if "--fotogramas" in sys.argv:
        SALIDA.mkdir(parents=True, exist_ok=True)
        momentos = {"s1": 3.6, "s2": 2.3, "s3": 6.0, "s4": 2.6, "s5": 3.5}
        extra = {"s2b": (e2_salida, 8.0, 6.8)}
        for n, fn, dur in ESCENAS:
            fn(momentos[n], dur).save(SALIDA / f"salida-fotograma-{n}.png")
        for n, (fn, dur, t) in extra.items():
            fn(t, dur).save(SALIDA / f"salida-fotograma-{n}.png")
        print("Fotogramas en", SALIDA)
        return
    archivos = [render(n, f, d) for n, f, d in ESCENAS]
    lista = SALIDA / "lista-salida.txt"
    lista.write_text("".join(f"file '{a.name}'\n" for a in archivos))
    final, tmp = SALIDA / "statio-la-salida.mp4", SALIDA / "_salida-final.tmp.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(lista), "-c", "copy",
                    "-movflags", "+faststart", str(tmp)], check=True)
    tmp.replace(final)
    print("Vídeo:", final)


if __name__ == "__main__":
    main()
