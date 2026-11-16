"""
Genera todos los iconos de Statio a partir de una sola definición.

    python3 marca/generar.py        (necesita: pip install cairosvg)

El símbolo: una S hecha de paradas (el recorrido) que termina en el punto
de horario fijo (rombo dorado), con una cruz dorada tenue detrás.
Coordenadas en una caja de 108 x 108 (la del icono adaptativo de Android).
"""
import json
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
P = json.loads((RAIZ / "marca" / "puntos.json").read_text())

TINTA, ORO, FONDO_ICONO, FONDO_OSCURO = "#F1ECE2", "#D8A24A", "#1B1A17", "#0E0E0D"
TINTA_CLARA, ORO_CLARO = "#1B1A17", "#9C6A17"

# Cruz: palo de y=22 a y=91, brazo de 38 de ancho a la altura y=39, grosor 4, 62 % de opacidad.
CRUZ = dict(cx=54, top=22, bot=91, brazo_y=39, ancho=38, w=4, opacidad=0.62)
R_PRIMERO, R_PUNTO, ROMBO = 4.8, 3.4, 7.6


def rombo_d(x, y, s=ROMBO):
    return f"M{x},{y - s}L{x + s},{y}L{x},{y + s}L{x - s},{y}Z"


def circulo_d(x, y, r):
    return f"M{x},{y}m-{r},0a{r},{r} 0,1 1,{2 * r},0a{r},{r} 0,1 1,-{2 * r},0"


def rect_d(x, y, w, h):
    return f"M{x},{y}h{w}v{h}h-{w}Z"


def cruz_rects():
    c = CRUZ
    return [
        (c["cx"] - c["w"] / 2, c["top"], c["w"], c["bot"] - c["top"]),
        (c["cx"] - c["ancho"] / 2, c["brazo_y"] - c["w"] / 2, c["ancho"], c["w"]),
    ]


# ---------------------------------------------------------------- SVG
def svg_simbolo(tinta=TINTA, oro=ORO, cruz=True, fondo=None, rx=24, escala=1.0):
    partes = []
    if fondo:
        partes.append(f'<rect width="108" height="108" rx="{rx}" fill="{fondo}"/>')
    g = []
    if cruz:
        r = "".join(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{CRUZ["w"] / 2}"/>' for x, y, w, h in cruz_rects())
        g.append(f'<g fill="{oro}" opacity="{CRUZ["opacidad"]}">{r}</g>')
    g += [f'<circle cx="{x}" cy="{y}" r="{R_PRIMERO if i == 0 else R_PUNTO}" fill="{tinta}"/>' for i, (x, y) in enumerate(P[:-1])]
    g.append(f'<path d="{rombo_d(*P[-1])}" fill="{oro}"/>')
    contenido = "".join(g)
    if escala != 1.0:
        contenido = f'<g transform="translate(54 56.5) scale({escala}) translate(-54 -56.5)">{contenido}</g>'
    partes.append(contenido)
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">' + "".join(partes) + "</svg>"


# ---------------------------------------------------------------- Android
def vector(tinta, oro, cruz=True, ancho=108, alto=108, vw=108, vh=108, tx=0, ty=0, alfa_cruz=None):
    alfa = CRUZ["opacidad"] if alfa_cruz is None else alfa_cruz
    lineas = []
    if cruz:
        for x, y, w, h in cruz_rects():
            lineas.append(f'        <path android:fillColor="{oro}" android:fillAlpha="{alfa}" android:pathData="{rect_d(x, y, w, h)}" />')
    for i, (x, y) in enumerate(P[:-1]):
        lineas.append(f'        <path android:fillColor="{tinta}" android:pathData="{circulo_d(x, y, R_PRIMERO if i == 0 else R_PUNTO)}" />')
    lineas.append(f'        <path android:fillColor="{oro}" android:pathData="{rombo_d(*P[-1])}" />')
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Statio: S de paradas con cruz detrás. Generado por marca/generar.py; no editar a mano. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{ancho}dp" android:height="{alto}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n'
        f'    <group android:translateX="{tx}" android:translateY="{ty}">\n' + "\n".join(lineas) + "\n    </group>\n</vector>\n"
    )


def main():
    import cairosvg

    res = RAIZ / "android/app/src/main/res/drawable"
    (res / "ic_marca.xml").write_text(vector(TINTA, ORO))
    (res / "ic_monocromo.xml").write_text(vector("#FFFFFFFF", "#FFFFFFFF", alfa_cruz=0.45))
    (res / "ic_splash.xml").write_text(vector(TINTA_CLARA, "#C8963E"))
    # La notificación es monocroma y diminuta: sin cruz para que la S se lea.
    (res / "ic_notificacion.xml").write_text(vector("#FFFFFFFF", "#FFFFFFFF", cruz=False, ancho=24, alto=24, vw=58, vh=58, tx=-25, ty=-27))

    m = RAIZ / "marca"
    (m / "statio-icono.svg").write_text(svg_simbolo(fondo=FONDO_ICONO))
    (m / "statio-icono-claro.svg").write_text(svg_simbolo(TINTA_CLARA, ORO_CLARO, fondo="#F5F2EC"))
    (m / "statio-simbolo.svg").write_text(svg_simbolo(TINTA_CLARA, ORO_CLARO))
    (RAIZ / "web/favicon.svg").write_text(svg_simbolo(fondo=FONDO_ICONO))

    play = svg_simbolo(fondo=FONDO_ICONO, rx=0, escala=1.3)
    (m / "statio-play-512.svg").write_text(play)
    cairosvg.svg2png(bytestring=play.encode(), write_to=str(m / "statio-play-512.png"), output_width=512, output_height=512)

    interior = svg_simbolo(fondo=FONDO_ICONO).split(">", 1)[1].rsplit("</svg>", 1)[0]
    destacado = f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<rect width="1024" height="500" fill="{FONDO_OSCURO}"/>
<g transform="translate(70 70) scale(3.3)">{interior}</g>
<text x="480" y="232" font-family="Inter, DejaVu Sans, sans-serif" font-size="96" font-weight="600" fill="{TINTA}" letter-spacing="-2">Statio</text>
<text x="484" y="300" font-family="Inter, DejaVu Sans, sans-serif" font-size="34" fill="#A69F93">El horario de tu cofradía,</text>
<text x="484" y="345" font-family="Inter, DejaVu Sans, sans-serif" font-size="34" fill="#A69F93">al oído.</text>
</svg>"""
    (m / "statio-grafico-destacado.svg").write_text(destacado)
    cairosvg.svg2png(bytestring=destacado.encode(), write_to=str(m / "statio-grafico-destacado-1024x500.png"))
    print("Iconos generados.")


if __name__ == "__main__":
    main()
