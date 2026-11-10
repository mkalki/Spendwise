"""Render original Spendwise shopping objects into a seamless cinematic background.
Install: python -m pip install -r frontend/scripts/scene-requirements.txt
Then: python -m pip install --upgrade PyOpenGL (Windows / Python 3.12 compatibility)
Run from repository root: python frontend/scripts/render_shopping_film.py
"""
import math
from pathlib import Path
import numpy as np
import trimesh
import pyrender
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[1] / 'public' / 'media'
OUT.mkdir(parents=True, exist_ok=True)
W, H, FPS, SECONDS = 1280, 720, 24, 10

def transform(position=(0,0,0), rotation=(0,0,0), scale=1):
    m=trimesh.transformations.euler_matrix(*rotation)
    m[:3,:3] *= scale
    m[:3,3]=position
    return m

def mat(color, metal=.6, rough=.25, emission=None):
    return pyrender.MetallicRoughnessMaterial(baseColorFactor=color, metallicFactor=metal, roughnessFactor=rough, emissiveFactor=emission or [0,0,0])

silver=mat([.47,.52,.65,1],.9,.2)
black=mat([.012,.014,.025,1],.25,.3)
red=mat([.65,.009,.024,1],.65,.2)
white=mat([.64,.7,.8,1],.45,.24)

def torus(radius,tube,start=0,end=2*math.pi):
    u=np.linspace(start,end,81);v=np.linspace(0,2*math.pi,13)
    verts=np.array([[(radius+tube*math.cos(b))*math.cos(a),(radius+tube*math.cos(b))*math.sin(a),tube*math.sin(b)] for a in u for b in v])
    faces=[]
    for i in range(len(u)-1):
      for j in range(len(v)-1):
        a=i*len(v)+j; b=a+len(v); faces += [[a,b,a+1],[a+1,b,b+1]]
    return trimesh.Trimesh(vertices=verts,faces=faces)

def headphones():
    parts=[]
    parts.append((torus(1.08,.14,0,math.pi),silver,transform((0,.25,0))))
    parts.append((torus(.91,.08,0,math.pi),black,transform((0,.25,0))))
    for side in [-1,1]:
      body=trimesh.creation.icosphere(subdivisions=3);body.apply_scale([.22,.55,.38])
      parts.append((body,silver,transform((side*1.05,-.25,0),(0,0,side*.15))))
      cushion=torus(.29,.115);cushion.apply_scale([1,1.4,1])
      parts.append((cushion,black,transform((side*.86,-.25,0),(0,math.pi/2,0))))
      parts.append((trimesh.creation.cylinder(radius=.055,height=.5),red,transform((side*1.05,.23,0),(math.pi/2,0,0))))
    return parts

def shoe():
    parts=[]
    # Sculpted rings form an original futuristic runner, toe to heel.
    stations=[(-2,.18,.22,.04),(-1.8,.48,.47,.02),(-1.1,.7,.6,.02),(-.3,.85,.58,.04),(.35,1.22,.51,.12),(.9,1.44,.46,.18),(1.4,1.31,.42,.17),(1.65,.62,.35,.12)]
    def loft(offset=0,height=1,width=1):
      vertices=[]
      for x,h,w,bottom in stations:
        for j in range(24):
          a=2*math.pi*j/24
          vertices.append([x,bottom+offset+(h*height)*(.5+.5*math.sin(a)),w*width*math.cos(a)])
      faces=[]
      for i in range(len(stations)-1):
        for j in range(24):
          a=i*24+j;b=i*24+(j+1)%24;faces += [[a,a+24,b],[b,a+24,b+24]]
      faces += [[0,j+1,j] for j in range(1,23)]
      return trimesh.Trimesh(vertices=vertices,faces=faces)
    parts.append((loft(.16),silver,np.eye(4)))
    parts.append((loft(-.12,.28,1.08),white,np.eye(4)))
    parts.append((loft(-.2,.13,1.1),black,np.eye(4)))
    # Red architectural side panels and sole ribs.
    for side in [-1,1]:
      vertices=[[-1.5,.42,side*.51],[-.2,.76,side*.57],[1.3,.85,side*.42],[.45,.39,side*.53]]
      panel=trimesh.Trimesh(vertices=vertices,faces=[[0,1,2],[0,2,3]])
      parts.append((panel,red,np.eye(4)))
      for i in range(13):
        rib=trimesh.creation.box([.035,.16,.03]);x=-1.5+i*.23
        parts.append((rib,silver,transform((x,.03,side*(.46+.07*math.sin(i/3))))))
    for i in range(6):
      lace=trimesh.creation.cylinder(radius=.026,height=.75-i*.035)
      parts.append((lace,white,transform((-.65+i*.23,.83+i*.065,0),(math.pi/2,.12,0))))
    collar=torus(.32,.085);collar.apply_scale([.85,1.3,1])
    parts.append((collar,black,transform((.94,1.3,0),(math.pi/2,0,0))))
    return parts

def bag():
    parts=[(trimesh.creation.box([1.6,1.9,.65]),silver,transform((0,-.2,0)))]
    for z in [-.31,.31]:
      parts.append((torus(.43,.045,0,math.pi),red,transform((0,.75,z))))
    stripe=trimesh.creation.box([.5,1.91,.025]);parts.append((stripe,black,transform((.35,-.2,.34))))
    return parts

scene=pyrender.Scene(bg_color=[.008,.009,.026,1],ambient_light=[.16,.17,.24])
camera=pyrender.PerspectiveCamera(yfov=math.radians(46))
def look(eye,target):
    z=np.array(eye,dtype=float)-np.array(target);z/=np.linalg.norm(z)
    x=np.cross([0,1,0],z);x/=np.linalg.norm(x);y=np.cross(z,x)
    m=np.eye(4);m[:3,0]=x;m[:3,1]=y;m[:3,2]=z;m[:3,3]=eye
    return m
cam=scene.add(camera,pose=look([5,3.5,10],[0,.5,0]))
for position,color,intensity in [([0,6,5],[.7,.78,1],35),([-5,2,3],[1,.015,.03],70),([5,1,-1],[.08,.12,1],90)]:
    scene.add(pyrender.PointLight(color=color,intensity=intensity),pose=transform(position))
scene.add(pyrender.DirectionalLight(color=[.6,.65,1],intensity=2),pose=transform((0,6,5),(-.6,0,0)))
renderer=pyrender.OffscreenRenderer(W,H)

def add_parts(parts,parent):
  for geometry, material, pose in parts:
    scene.add(pyrender.Mesh.from_trimesh(geometry,material=material,smooth=True),pose=pose,parent_node=parent)

# Make original product thumbnails for a wall of luminous display cabinets.
thumbs=[]
for index,parts in enumerate([shoe(),headphones(),bag()]):
    group=pyrender.Node(matrix=transform((0,0,0),(0,-.25,0),.9));scene.add_node(group);add_parts(parts,group)
    scene.set_pose(cam,look([2.3,1.6,4.6],[0,.4,0]))
    pixels,_=renderer.render(scene)
    thumb=Image.fromarray(pixels).resize((640,360));thumb.save(OUT/f'product-{index+1:03d}.jpg',quality=92);thumbs.append(thumb.resize((384,216)))
    scene.remove_node(group)

for row in range(5):
  for col in range(9):
    index=(row*9+col)%3
    tile=Image.new('RGB',(384,252),(14,16,35));tile.paste(thumbs[index],(0,0));d=ImageDraw.Draw(tile)
    d.rectangle((1,1,382,250),outline=(75,81,120),width=2)
    d.text((12,229),f'SW / {row*9+col+1:03d}',fill=(210,216,240))
    d.text((232,229),['STUDIO RUNNER','SOUND OBJECT','DAILY CARRY'][index],fill=(140,148,180))
    # Plane in XY, facing the camera (+Z).
    mesh=trimesh.Trimesh(vertices=[[-1.05,-.69,0],[1.05,-.69,0],[1.05,.69,0],[-1.05,.69,0]],faces=[[0,1,2],[0,2,3]],process=False)
    mesh.visual=trimesh.visual.texture.TextureVisuals(uv=[[0,0],[1,0],[1,1],[0,1]],image=tile)
    material=pyrender.MetallicRoughnessMaterial(baseColorTexture=pyrender.Texture(source=np.array(tile),source_channels='RGB'),emissiveTexture=pyrender.Texture(source=np.array(tile),source_channels='RGB'),emissiveFactor=[.65,.65,.85],metallicFactor=0,roughnessFactor=1)
    scene.add(pyrender.Mesh.from_trimesh(mesh,material=material),pose=transform(((col-4)*2.24,(row-2)*1.55+1,-5),(0,0,.07)))

floor=trimesh.creation.box([50,.05,50]);scene.add(pyrender.Mesh.from_trimesh(floor,material=mat([.008,.009,.02,1],.8,.24)),pose=transform((0,-1.5,0)))
hero=pyrender.Node(matrix=transform((0,-.25,.7),(.08,-.45,-.16),1.4));scene.add_node(hero);add_parts(headphones(),hero)
left=pyrender.Node(matrix=transform((-3.5,.7,-.8),(.1,-.6,-.23),.95));scene.add_node(left);add_parts(bag(),left)
right=pyrender.Node(matrix=transform((3.5,1.8,-1),(.1,.4,.18),1));scene.add_node(right);add_parts(shoe(),right)
# Rings behind the hero provide the red/blue light-trail silhouette.
for radius in [3.2,3.35]:
    scene.add(pyrender.Mesh.from_trimesh(torus(radius,.018),material=mat([.9,.005,.02,1],.4,.2,[.7,.003,.01])),pose=transform((0,.5,-1.4),(.3,.4,-.15)))

writer=imageio_ffmpeg.write_frames(str(OUT/'spendwise-world.mp4'),(W,H),fps=FPS,codec='libx264',quality=6,pix_fmt_out='yuv420p',output_params=['-movflags','+faststart','-preset','fast'])
writer.send(None)
for frame in range(FPS*SECONDS):
    t=frame/(FPS*SECONDS)*2*math.pi
    scene.set_pose(cam,look([3.1+.65*math.sin(t),2.3+.25*math.sin(t),9.5+.4*math.cos(t)],[0,.65,0]))
    scene.set_pose(hero,transform((0,.25+.14*math.sin(t),1),(.13*math.sin(t),-.45+.26*math.sin(t),-.32+.07*math.cos(t)),2.1))
    scene.set_pose(left,transform((-3.5,.65+.18*math.sin(t+1),-.8),(.1,-.6+.2*math.sin(t),-.23),.95))
    scene.set_pose(right,transform((3.5,.9+.2*math.sin(t+2),-1),(.1,-.3+.3*math.sin(t),.18),.85))
    pixels,_=renderer.render(scene)
    if frame==0: Image.fromarray(pixels).save(OUT/'spendwise-world-poster.jpg',quality=92)
    writer.send(np.ascontiguousarray(pixels))
    if frame%48==0: print(f'Rendered {frame}/{FPS*SECONDS}',flush=True)
writer.close();renderer.delete()
print('Saved',OUT/'spendwise-world.mp4',flush=True)
