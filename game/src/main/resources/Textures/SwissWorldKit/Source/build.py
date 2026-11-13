import bpy, math, random, json, os
import numpy as np
from mathutils import Vector
from pathlib import Path
OUT=Path(__file__).resolve().parent.parent
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene
scene.name='Swiss World Kit | Asset Library'
scene.unit_settings.system='METRIC'
random.seed(42)
materials={}
def mat(name,color,rough=.7,metal=0,texture=False):
 m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True
 bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*color,1);bs.inputs['Roughness'].default_value=rough;bs.inputs['Metallic'].default_value=metal
 if texture:
  rng=np.random.default_rng(22);n=128
  v=rng.normal(0,.025,(n,n,1));rgb=np.clip(np.array(color)[None,None,:]+v,0,1)
  pixels=np.concatenate([rgb,np.ones((n,n,1))],axis=2).astype('float32')
  im=bpy.data.images.new(name+'_albedo',width=n,height=n);im.pixels.foreach_set(pixels.ravel());im.pack()
  node=m.node_tree.nodes.new('ShaderNodeTexImage');node.image=im;m.node_tree.links.new(node.outputs['Color'],bs.inputs['Base Color'])
 materials[name]=m;return m
mat('Concrete',(.47,.48,.46),texture=True);mat('Plaster',(.79,.76,.67),texture=True)
mat('White',(.82,.83,.78));mat('Metal',(.22,.28,.29),.38,.65);mat('DarkMetal',(.075,.105,.12),.42,.55)
mat('Roof',(.23,.27,.28),texture=True);mat('Tile',(.37,.16,.09),texture=True)
mat('Wood',(.27,.13,.055),texture=True);mat('Glass',(.065,.18,.22),.16,.35)
mat('Asphalt',(.095,.105,.115),texture=True);mat('Yellow',(.88,.6,.07));mat('Red',(.68,.09,.055))
mat('Bark',(.18,.105,.055),texture=True)
for i,c in enumerate([(.10,.21,.065),(.15,.27,.08),(.20,.31,.10),(.08,.17,.10)]):mat('Leaf'+str(i),c,texture=True)
mat('Lamp',(.78,.88,.98),.3)
bs=materials['Lamp'].node_tree.nodes.get('Principled BSDF');bs.inputs['Emission Color'].default_value=(.6,.78,1,1);bs.inputs['Emission Strength'].default_value=.3
assets={};current=[];detail=0

def mesh(name,verts,faces,material):
 me=bpy.data.meshes.new(name);me.from_pydata(verts,[],faces);me.update()
 o=bpy.data.objects.new(name,me);scene.collection.objects.link(o);me.materials.append(materials[material]);current.append(o)
 return o

def box(name,loc,size,material,bevel=0):
 x,y,z=[s/2 for s in size]
 v=[(-x,-y,-z),(-x,-y,z),(-x,y,-z),(-x,y,z),(x,-y,-z),(x,-y,z),(x,y,-z),(x,y,z)]
 o=mesh(name,v,[(0,4,6,2),(1,3,7,5),(0,1,5,4),(2,6,7,3),(0,2,3,1),(4,5,7,6)],material);o.location=loc
 if bevel:
  mod=o.modifiers.new('Edge highlights','BEVEL');mod.width=bevel;mod.segments=2
 return o

def cyl(name,loc,radius,depth,material,n=12,r2=None):
 v=[]
 for z,r in [(-depth/2,radius),(depth/2,radius if r2 is None else r2)]:
  v.extend([(math.cos(a*math.tau/n)*r,math.sin(a*math.tau/n)*r,z) for a in range(n)])
 f=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
 o=mesh(name,v,f,material);o.location=loc;return o

def beam(a,b,r,material):
 a,b=Vector(a),Vector(b);o=cyl('Brace',(a+b)/2,r,(b-a).length,material,8);o.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler();return o

def roof(w,d,z,rise,material='Tile'):
 x=w/2;y=d/2
 return mesh('Gable roof',[(-x,-y,z),(x,-y,z),(0,-y,z+rise),(-x,y,z),(x,y,z),(0,y,z+rise)],[(0,2,1),(3,4,5),(0,3,5,2),(2,5,4,1),(0,1,4,3)],material)

def windows(w,d,floors,spacing=2.4):
 for floor in range(floors):
  z=1.8+floor*2.9
  for side in [-1,1]:
   for x in np.arange(-w/2+1.5,w/2-1,spacing):
    box('Window frame',(x,side*(d/2+.035),z),(1.24,.11,1.48),'White',.025)
    box('Window glass',(x,side*(d/2+.10),z),(1.04,.03,1.24),'Glass')
    box('Window mullion',(x,side*(d/2+.13),z),(.04,.04,1.24),'White')

def start():
 global current
 current=[]
def finish(name):
 bpy.ops.object.select_all(action='DESELECT')
 for o in current:o.select_set(True)
 bpy.context.view_layer.objects.active=current[0]
 # apply bevels and convert, join by material via exporter primitive splitting
 bpy.ops.object.convert(target='MESH');bpy.ops.object.join();o=bpy.context.object
 scene.cursor.location=(0,0,0);bpy.ops.object.origin_set(type='ORIGIN_CURSOR')
 bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
 o.name=name+'_LOD0';o['asset_id']=name;o['units']='metres';o['front_blender']='-Y';o['front_gltf']='+Z';o['pivot']='ground center'
 # Planar UV per face, consistent metre-based texel scale
 uv=o.data.uv_layers.active
 if uv is None: uv=o.data.uv_layers.new(name='UVMap')
 for p in o.data.polygons:
  axis=max(range(3),key=lambda i:abs(p.normal[i]));axes=[i for i in range(3) if i!=axis]
  for li in p.loop_indices:
   co=o.data.vertices[o.data.loops[li].vertex_index].co
   uv.data[li].uv=(co[axes[0]]*.25,co[axes[1]]*.25)
 assets[name]=o
 print('BUILT',name,flush=True)

start()
# Hangar with interior opening and sliding door leaves (closed side bays)
box('Slab',(0,0,.15),(36,30,.3),'Concrete',.10)
for x in [-17.7,17.7]:box('Sidewall',(x,0,5.4),(.45,29.5,10.5),'Metal',.06)
box('Backwall',(0,14.65,5.4),(35.5,.4,10.5),'Metal')
roof(36.8,30.8,10.5,3,'Roof')
for x in [-16,-12,12,16]:
 box('Sliding panel',(x,-14.7,4.65),(3.85,.25,9),'Metal',.04)
 for z in [1,3,5,7,9]:box('Panel seam',(x,-14.88,z),(3.7,.04,.05),'DarkMetal')
for x in [-17.5,17.5]:box('Corner column',(x,-14.8,5.4),(.45,.6,10.5),'White',.03)
box('Door header',(0,-14.9,10),(35,.6,.5),'White',.05)
for y in [-12,-6,0,6,12]:
 for x in [-17.45,17.45]:box('Structural rib',(x,y,5.3),(.3,.3,10),'DarkMetal')
 for s in [-1,1]:beam((s*17.4,y,10.5),(0,y,13.1),.1,'White')
for x in np.arange(-17,18,.8):
 for s in [-1,1]:box('Cladding seam',(x,14.9 if s==1 else -14.92,10.05),(.025,.03,.3),'DarkMetal')
box('Service door',(17.96,8,1.25),(.05,1.3,2.3),'DarkMetal')
finish('Hangar_36x30')

start()
box('Foundation',(0,0,.15),(8.8,8.8,.3),'Concrete',.10)
box('Tower shaft',(0,0,8),(6,6,16),'Plaster',.1)
for z in [3,6,9,12]:
 for s in [-1,1]:box('Stairwell glass',(s*3.04,0,z),(.05,1.25,1.8),'Glass')
box('Observation platform',(0,0,16.3),(10,10,.6),'Concrete',.08)
box('Cabin base',(0,0,16.8),(8.8,8.8,.6),'DarkMetal',.05)
box('Cabin glazing',(0,0,18.15),(8.7,8.7,2.1),'Glass',.04)
for side in [-1,1]:
 for p in np.linspace(-4.4,4.4,7):
  box('Cabin mullion',(p,side*4.4,18.15),(.10,.12,2.3),'White')
  box('Cabin mullion',(side*4.4,p,18.15),(.12,.10,2.3),'White')
box('Cabin roof',(0,0,19.5),(10,10,.4),'Roof',.12)
for side in [-1,1]:
 for z in [16.7,17.2]:
  box('Balcony rail',(side*4.9,0,z),(.05,9.8,.05),'Metal')
  box('Balcony rail',(0,side*4.9,z),(9.8,.05,.05),'Metal')
for x in [-4.9,4.9]:
 for y in [-4.9,0,4.9]:cyl('Balcony post',(x,y,16.8),.035,.8,'Metal',8)
cyl('Antenna',(0,0,21.4),.07,3.4,'White',10)
box('Service door',(0,-3.06,1.2),(1.2,.06,2.4),'DarkMetal')
finish('ControlTower_23m')

start()
box('Terminal base',(0,0,.2),(32,14,.4),'Concrete',.1)
box('Terminal walls',(0,0,3.3),(31,13,6.2),'Plaster',.08)
box('Roof',(0,0,6.65),(33,15,.45),'Roof',.1)
for x in np.arange(-14,15,2):
 box('Curtain glass',(x,-6.56,3),(1.85,.06,4.8),'Glass')
 box('Curtain mullion',(x+1,-6.65,3),(.10,.13,4.9),'Metal')
box('Entrance canopy',(0,-8.1,4.9),(12,4,.25),'Metal',.05)
for x in [-5.6,5.6]:cyl('Canopy column',(x,-9.5,2.5),.09,4.6,'Metal')
for x in [-9,8]:box('HVAC unit',(x,1,7.3),(3,2,1),'Metal',.08)
finish('RegionalTerminal_32x14')

for name,w,d,f,style in [('House_Stucco',10,9,2,'Plaster'),('House_Chalet',12,10,2,'Wood'),('Apartment_3Floors',18,12,3,'Plaster')]:
 start();height=f*2.9+.3
 box('Foundation',(0,0,.18),(w+.4,d+.4,.36),'Concrete',.06)
 box('Walls',(0,0,height/2+.25),(w,d,height),style,.045)
 windows(w,d,f)
 box('Entrance',(0,-d/2-.13,1.25),(1.15,.12,2.2),'Wood',.025)
 roof(w+1,d+1,height+.25,2.2,'Tile' if style=='Plaster' else 'Roof')
 # roof tile courses as visible geometric strips
 for side in [-1,1]:
  for j in range(1,12):
   x=side*j*(w+1)/24;z=height+.25+2.2*(1-abs(x)/((w+1)/2))+.03
   o=box('Roof course',(x,0,z),(.045,d+1,.035),'Roof' if style=='Wood' else 'Tile')
 box('Chimney',(w*.25,1,height+2),( .7,.7,2),'Concrete',.03)
 if style=='Wood':
  for z in np.arange(.6,height,.35):
   for side in [-1,1]:box('Timber course',(0,side*(d/2+.02),z),(w,.05,.025),'Bark')
  box('Balcony deck',(0,-d/2-1.05,3.2),(w,2,.25),'Wood',.03)
  for x in np.arange(-w/2,w/2+.1,.6):box('Baluster',(x,-d/2-1.9,3.9),(.07,.08,1.2),'Wood')
  box('Handrail',(0,-d/2-1.9,4.5),(w,.13,.13),'Wood',.025)
 finish(name)

start()
cyl('Trunk',(0,0,7),.36,14,'Bark',12,r2=.12)
for j in range(12):
 z=4+j*.9;r=(1-j/13)*3
 # irregular scalloped layers rather than perfectly smooth cones
 n=18;v=[(0,0,z+3)]
 v += [(math.cos(i*math.tau/n)*r*random.uniform(.8,1.1),math.sin(i*math.tau/n)*r*random.uniform(.8,1.1),z+random.uniform(-.3,.1)) for i in range(n)]
 mesh('Needle tier',v,[(0,i+1,(i+1)%n+1) for i in range(n)]+[tuple(reversed(range(1,n+1)))],'Leaf'+str(j%4))
finish('Tree_Spruce_17m')

start()
cyl('Trunk',(0,0,3.5),.4,7,'Bark',12,r2=.20)
for i in range(34):
 ang=random.random()*math.tau;r=math.sqrt(random.random())*3.1
 pos=Vector((math.cos(ang)*r,math.sin(ang)*r,8+random.uniform(-1.3,2)))
 if i%4==0:beam((0,0,4.2),pos,.13,'Bark')
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2,radius=1,location=pos)
 o=bpy.context.object;o.name='Leaf crown';o.scale=(random.uniform(1.3,2),random.uniform(1.3,2),random.uniform(1.2,1.8));o.data.materials.append(materials['Leaf'+str(i%3)]);current.append(o)
 for p in o.data.polygons:p.use_smooth=True
finish('Tree_Broadleaf_12m')

start()
box('Pavement',(0,0,-.15),(45,100,.3),'Asphalt')
for side in [-1,1]:box('Edge stripe',(side*21,0,.012),(.18,100,.024),'White')
for y in [-35,15]:box('Center dash',(0,y,.015),(.9,30,.03),'White')
finish('Runway_Straight_45x100')
start()
box('Taxi pavement',(0,0,-.15),(18,50,.3),'Asphalt')
box('Taxi center',(0,0,.015),(.15,50,.03),'Yellow')
for side in [-1,1]:box('Taxi edge',(side*8,0,.015),(.12,50,.03),'Yellow')
finish('Taxiway_Straight_18x50')
start()
for x in [-5,0,5]:
 cyl('Fence post',(x,0,1.3),.045,2.6,'Metal',8)
for z in [.12,2.4]:beam((-5,0,z),(5,0,z),.02,'Metal')
# wire diamond infill
for x in np.arange(-7.4,5.01,.30):
 a=max(-5,x);b=min(5,x+2.4)
 if b>a:beam((a,0,a-x+.05),(b,0,b-x+.05),.007,'Metal')
for x in np.arange(-5,7.41,.30):
 a=max(-5,x-2.4);b=min(5,x)
 if b>a:beam((a,0,x-a+.05),(b,0,x-b+.05),.007,'Metal')
finish('Fence_10m')
start()
cyl('Concrete foot',(0,0,.12),.3,.24,'Concrete',12)
cyl('Light mast',(0,0,4),.055,7.8,'Metal',12)
box('Lamp housing',(0,-.35,7.9),(.6,1,.22),'DarkMetal',.06)
box('Lamp lens',(0,-.35,7.77),(.45,.8,.025),'Lamp',.02)
finish('ApronLight_8m')

# Export independent assets; origin is ground center. No cameras/lights in GLBs.
manifest=[]
for name,o in assets.items():
 bpy.ops.object.select_all(action='DESELECT');o.select_set(True);bpy.context.view_layer.objects.active=o
 lods=[]
 for level,ratio in [(0,1),(1,.48),(2,.20)]:
  obj=o if level==0 else o.copy()
  if level:
   obj.data=o.data.copy();scene.collection.objects.link(obj);o.select_set(False);obj.select_set(True);bpy.context.view_layer.objects.active=obj
   mod=obj.modifiers.new('LOD simplification','DECIMATE');mod.ratio=ratio
   bpy.ops.object.modifier_apply(modifier=mod.name)
  obj.name=name+'_LOD'+str(level)
  obj.data.calc_loop_triangles();tris=len(obj.data.loop_triangles)
  path=OUT/'Models'/(obj.name+'.glb')
  bpy.ops.export_scene.gltf(filepath=str(path),export_format='GLB',use_selection=True,export_apply=True,export_yup=True,export_animations=False,export_cameras=False,export_lights=False)
  lods.append({'level':level,'path':'Models/'+path.name,'triangles':tris})
  if level:bpy.data.objects.remove(obj,do_unlink=True);o.select_set(True);bpy.context.view_layer.objects.active=o
 o.name=name+'_LOD0'
 manifest.append({'id':name,'dimensions_blender_xyz':list(o.dimensions),'lods':lods,'collision':'not supplied','pivot':'ground center','front_gltf':'+Z'})
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2))

# Catalog layout. These translations are for inspection only, exports above remain at origin.
layout=[(-55,30),(0,30),(43,30),(-48,-10),(-25,-10),(4,-10),(34,-10),(48,-10),(-56,-80),(0,-80),(35,-43),(48,-43)]
for (name,o),(x,y) in zip(assets.items(),layout):o.location=(x,y,0)
# ground is preview-only
current=[]
box('Preview ground',(0,-25,-.45),(165,210,.5),'Concrete',.5)
# Camera
bpy.ops.object.camera_add(location=(150,-205,160));cam=bpy.context.object
cam.rotation_euler=(Vector((-3,-20,1))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=208;scene.camera=cam
scene.world=bpy.data.worlds.new('Daylight');scene.world.use_nodes=True;scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.5,.65,.8,1);scene.world.node_tree.nodes['Background'].inputs[1].default_value=.55
bpy.ops.object.light_add(type='SUN',location=(0,0,100));sun=bpy.context.object;sun.rotation_euler=(.4,-.5,-.5);sun.data.energy=3;sun.data.angle=.12
scene.render.engine='CYCLES';scene.cycles.samples=32
scene.render.resolution_x=1500;scene.render.resolution_y=1300;scene.render.resolution_percentage=100
scene.view_settings.view_transform='AgX'
# overview composition
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'SwissWorldKit.blend'))
scene.render.filepath=str(OUT/'Previews'/'Overview.png');bpy.ops.render.render(write_still=True)
# detail shot of airport side
cam.location=(75,-60,42);cam.rotation_euler=(Vector((-15,26,5))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=112
scene.render.resolution_x=1600;scene.render.resolution_y=1000
scene.render.filepath=str(OUT/'Previews'/'Airport.png');bpy.ops.render.render(write_still=True)
print('COMPLETE',flush=True)
