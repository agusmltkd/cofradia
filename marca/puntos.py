import math, json
# S como recorrido: puntos equidistantes sobre dos curvas de Bézier; el último es el punto fijo (rombo dorado)
segs=[((70,36),(70,25),(38,25),(38,39.5)),((38,39.5),(38,54),(70,50),(70,66)),((70,66),(70,82),(38,82),(38,71))]
def bez(p,t):
    a,b,c,d=p; m=1-t
    return (m**3*a[0]+3*m*m*t*b[0]+3*m*t*t*c[0]+t**3*d[0], m**3*a[1]+3*m*m*t*b[1]+3*m*t*t*c[1]+t**3*d[1])
pts=[]
for s in segs:
    for i in range(400): pts.append(bez(s,i/400))
pts.append(segs[-1][3])
L=[0]
for i in range(1,len(pts)): L.append(L[-1]+math.dist(pts[i-1],pts[i]))
N=13
out=[]
for k in range(N):
    target=L[-1]*k/(N-1)
    j=min(range(len(L)),key=lambda i:abs(L[i]-target)); out.append(pts[j])
json.dump([(round(x,2),round(y,2)) for x,y in out],open('puntos.json','w'))
print(len(out), round(L[-1],1))
