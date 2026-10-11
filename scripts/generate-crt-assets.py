#!/usr/bin/env python3
"""Offline developer asset generator: requires Pillow/NumPy, never runs in the app or Gradle."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageFilter
import numpy as np, subprocess
W,H=320,180
work=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable-nodpi';work.mkdir(exist_ok=True)
y,x=np.mgrid[0:H,0:W]; qx=(x+.5)/W; qy=1-(y+.5)/H
cx=(qx-.5)*2*1.1;cy=(qy-.5)*2*1.1
cx*=1+(abs(cy)/5)**2;cy*=1+(abs(cx)/4)**2
ux=.5*qx+.5*((cx/2+.5)*.92+.04); uy=.5*qy+.5*((cy/2+.5)*.92+.04)
inside=(ux>=0)&(ux<=1)&(uy>=0)&(uy<=1)
# Make ordinary cached image assets. No GLSL, AGSL, WebGL, or per-frame pixel effects.
# Approximate dark corners and curved tube boundaries as an alpha overlay.
vig=np.clip(16*ux*uy*(1-ux)*(1-uy),0,1)**.3
shade=np.clip((1-vig)*1.45,0,1)
shade=np.maximum(shade,np.clip((.09-np.minimum.reduce([ux,uy,1-ux,1-uy]))/.09,0,1)*.34)
vignette=np.zeros((H,W,4),np.uint8);vignette[:,:,3]=np.where(inside,0,255)
Image.fromarray(vignette).save(work/'crt_vignette.png')
# Four fixed grain variations; regenerated neither per frame nor per pixel in playback.
rng=np.random.default_rng(932)
overlays=[]
for n in range(8):
 phase=n/8*2*np.pi
 scan=np.clip(.35+.15*np.sin(uy*H*1.5+phase),0,1)**.9
 brightness=np.clip(3.8*scan*vig,0,3.0)
 grille=1-.15*np.maximum((x%2)*2-1,0)
 grain=(1-rng.random((H,W))*.25)
 brightness=np.clip(brightness*grille*grain,0,1)**.45
 arr=np.zeros((H,W,4),np.uint8);arr[:,:,3]=np.rint((1-brightness)*255)
 # Subtle green tube tint, drawn as ordinary translucent color.
 arr[:,:,0]=5;arr[:,:,1]=18;arr[:,:,2]=8
 im=Image.fromarray(arr);im.save(work/f'crt_texture_{n}.png');overlays.append(im)
# Pre-blurred ball sprite and RGB edge fringe; these images are reused at all positions.
S=112; sy,sx=np.mgrid[0:S,0:S]; coverage=Image.new('L',(S*4,S*4)); d=ImageDraw.Draw(coverage);r=21.6
c=S/2;d.ellipse(((c-r)*4,(c-r)*4,(c+r)*4,(c+r)*4),fill=255)
coverage=coverage.resize((S,S),Image.Resampling.LANCZOS)
soft=coverage.filter(ImageFilter.GaussianBlur(.9)); halo=coverage.filter(ImageFilter.GaussianBlur(5.2))
base=np.asarray(soft,dtype=float)/255; hg=np.asarray(halo,dtype=float)/255
alpha=np.maximum(base,hg*.12)
color=np.empty((S,S,3));color[:]=[61,35,188]
fringe_r=np.asarray(soft.transform((S,S),Image.Transform.AFFINE,(1,0,.35,0,1,.2)),dtype=float)/255
fringe_b=np.asarray(soft.transform((S,S),Image.Transform.AFFINE,(1,0,-.55,0,1,0)),dtype=float)/255
rgb=np.ones((S,S,3))*255
for channel,cov in enumerate([fringe_r,base,fringe_b]): rgb[:,:,channel]=255-(255-color[:,:,channel])*np.maximum(cov,hg*.12)
alpha=1-rgb.min(axis=2)/255
straight=(rgb/255-(1-alpha[:,:,None]))/np.maximum(alpha[:,:,None],1e-6)
sprite=np.dstack([np.clip(straight*255,0,255),alpha*255]).astype(np.uint8)
ball=Image.fromarray(sprite);ball.save(work/'crt_ball.png')
# A softly bowed baseline path is cached with the background.
background=Image.new('RGBA',(W,H),'white'); d=ImageDraw.Draw(background)
points=[(j,174-2.5*((j-W/2)/(W/2))**2) for j in range(8,W-8)]
d.line(points,fill='#C7C0DF',width=2)
background.save(work/'crt_background.png');vignette=Image.fromarray(vignette)

print('Generated native CRT preview assets.')
