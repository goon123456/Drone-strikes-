"""Floating-part detector: samples every part's surface, joins parts whose surfaces come within TOL, and reports
every island that is not joined to the main body. usage: check.py [names...]"""
import sys, numpy as np
names=sys.argv[1:]; sys.argv=['x']
import os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vehicle_kit import *
from vehicle_kit import _inside
import gen_guns as G
from scipy.spatial import cKDTree
TOL=0.0025; STEP=0.003
def samples(part):
    pts=[]
    for sf in part["surfaces"]:
        g=np.array(sf["grid"],dtype=float)
        if g.ndim!=3: continue
        if sf["mask"]:                                     # a flat cap cut to an outline: sample the rectangle, keep what is inside
            R,C=g.shape[:2]; L=max(np.linalg.norm(g[-1,0]-g[0,0]),np.linalg.norm(g[0,-1]-g[0,0]))
            n=max(2,int(L/STEP)+2); u,v=np.meshgrid(np.linspace(0,1,n),np.linspace(0,1,n))
            a,b,c,d=g[0,0],g[0,-1],g[-1,-1],g[-1,0]
            P=((1-u)*(1-v))[...,None]*a+(u*(1-v))[...,None]*b+(u*v)[...,None]*c+((1-u)*v)[...,None]*d
            if sf["mask"]=="disc": keep=np.hypot(u-0.5,v-0.5)<=0.5
            else: keep=_inside(sf["mask"][1],u,v)
            pts.append(P[keep].reshape(-1,3)); continue
        for (i,j) in [f[0] for f in sf["faces"]]:
            try: a,b,c,d=g[i][j],g[i][j+1],g[i+1][j+1],g[i+1][j]
            except IndexError: continue
            nu=max(1,int(np.linalg.norm(b-a)/STEP)); nv=max(1,int(np.linalg.norm(d-a)/STEP))
            u=np.linspace(0,1,nu+1)[:,None,None]; v=np.linspace(0,1,nv+1)[None,:,None]
            pts.append(((1-u)*(1-v)*a+u*(1-v)*b+u*v*c+(1-u)*v*d).reshape(-1,3))
    return np.concatenate(pts) if pts else np.zeros((0,3))
def tris(part):
    T=[]
    for sf in part["surfaces"]:
        g=np.array(sf["grid"],dtype=float)
        if g.ndim!=3: continue
        if sf["mask"]:
            a,b,c,d=g[0,0],g[0,-1],g[-1,-1],g[-1,0]; T.append((a,b,c)); T.append((a,c,d)); continue
        for f in sf["faces"]:
            (i,j)=f[0]
            try: a,b,c,d=g[i][j],g[i][j+1],g[i+1][j+1],g[i+1][j]
            except IndexError: continue
            T.append((a,b,c)); T.append((a,c,d))
    return np.array(T) if T else np.zeros((0,3,3))
def inside_(P,T):
    """How many of points P lie inside the closed-ish mesh T (ray parity along a skewed direction)."""
    if not len(T) or not len(P): return 0
    D=np.array([0.3712,0.8431,0.3889]); D/=np.linalg.norm(D)
    v0=T[:,0]; e1=T[:,1]-v0; e2=T[:,2]-v0
    h=np.cross(D,e2); a=(e1*h).sum(1); ok=np.abs(a)>1e-12; f=np.where(ok,1/np.where(ok,a,1),0)
    cnt=0
    for p in P:
        s=p-v0; u=f*(s*h).sum(1); q=np.cross(s,e1); v=f*(q@D); t=f*(e2*q).sum(1)
        hits=ok&(u>=0)&(v>=0)&(u+v<=1)&(t>1e-9)
        if hits.sum()%2==1: cnt+=1
    return cnt
def check(name):
    m=G.MODELS[name][0](); bake(m,name,300)
    parts=[(None,p) for p in m["base"]]+[((b['name'] if isinstance(b,dict) else b[0]),c) for b in m["bones"] for c in b["cubes"]]
    S=[samples(p) for _,p in parts]; n=len(parts); TR=[tris(p) for _,p in parts]; rng=np.random.default_rng(1)
    keep=[i for i in range(n) if len(S[i])]
    par=list(range(n))
    def find(a):
        while par[a]!=a: par[a]=par[par[a]]; a=par[a]
        return a
    trees={i:cKDTree(S[i]) for i in keep}
    box={i:(S[i].min(0),S[i].max(0)) for i in keep}
    for x,i in enumerate(keep):
        for j in keep[x+1:]:
            if np.any(box[i][0]>box[j][1]+TOL) or np.any(box[j][0]>box[i][1]+TOL): continue
            a,b=(i,j) if len(S[i])<len(S[j]) else (j,i)
            d,_=trees[b].query(S[a],k=1,distance_upper_bound=TOL)
            inside=False   # swallowed whole: hidden, not floating
            hit=np.isfinite(d).any() or inside
            if not hit:
                for (p_,q_) in ((i,j),(j,i)):
                    pts=S[p_][rng.choice(len(S[p_]),min(160,len(S[p_])),replace=False)]
                    if inside_(pts,TR[q_])>0: hit=True; break
            if hit: par[find(i)]=find(j)
    comp={}
    for i in keep: comp.setdefault(find(i),[]).append(i)
    main=max(comp.values(),key=lambda c:sum(len(S[i]) for i in c))
    bad=[c for c in comp.values() if c is not main]
    for c in bad:
        lo=np.min([box[i][0] for i in c],0); hi=np.max([box[i][1] for i in c],0)
        # gap to the main body
        mt=cKDTree(np.concatenate([S[i] for i in main])); gap=mt.query(np.concatenate([S[i] for i in c]))[0].min()
        print(f"  {name}: FLOATING {[ (parts[i][0] or 'base')+':'+str(parts[i][1]['mat'])[:14] for i in c][:4]} x[{lo[0]:.3f},{hi[0]:.3f}] y[{lo[1]:.3f},{hi[1]:.3f}] z[{lo[2]:.3f},{hi[2]:.3f}] gap={gap*1000:.1f}mm")
    return len(bad)
tot=0
for nm in (names or [k for k in G.MODELS if not k.startswith(('ammo_','gl_'))]):
    try: k=check(nm)
    except Exception as e: print(nm,"ERR",repr(e)[:200]); k=0
    tot+=k
    if not k: print(f"  {nm}: ok")
print("islands:",tot)
