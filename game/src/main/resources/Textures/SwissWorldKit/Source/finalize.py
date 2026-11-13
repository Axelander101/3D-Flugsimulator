import bpy,json,math
from pathlib import Path
from mathutils import Vector
OUT=Path(__file__).resolve().parent.parent
s=bpy.context.scene
entries=json.loads((OUT/'manifest.json').read_text())
originals={a['id']:bpy.data.objects[a['id']+'_LOD0'] for a in entries}
# re-export clean geometry; leave low-cost pavement untouched in each LOD
for a in entries:
 o=originals[a['id']]; saved=o.location.copy();o.location=(0,0,0)
 for lod in a['lods']:
  level=lod['level'];c=o.copy();c.data=o.data.copy();s.collection.objects.link(c)
  bpy.ops.object.select_all(action='DESELECT');c.select_set(True);bpy.context.view_layer.objects.active=c
  c.name=a['id']+'_export'
  if level and not a['id'].startswith(('Runway','Taxiway')):
   d=c.modifiers.new('Simplify','DECIMATE');d.ratio=.48 if level==1 else .20;bpy.ops.object.modifier_apply(modifier=d.name)
  c.data.validate(clean_customdata=False);c.data.update();c.data.calc_loop_triangles();lod['triangles']=len(c.data.loop_triangles)
  bpy.ops.export_scene.gltf(filepath=str(OUT/lod['path']),export_format='GLB',use_selection=True,export_apply=True,export_yup=True,export_animations=False)
  bpy.data.objects.remove(c,do_unlink=True)
 o.location=saved
 a['dimensions_gltf_xyz']=[a['dimensions_blender_xyz'][0],a['dimensions_blender_xyz'][2],a['dimensions_blender_xyz'][1]]
 a['lod_selection']='separate GLBs, manual selection in game'
(OUT/'manifest.json').write_text(json.dumps(entries,indent=2))
# Refresh overview framing
cam=s.camera;cam.data.ortho_scale=245
s.render.resolution_x=1600;s.render.resolution_y=1300;s.cycles.samples=24
s.render.filepath=str(OUT/'Previews'/'Overview.png');bpy.ops.render.render(write_still=True)
# Individual thumbnails; preview environment never part of exports
for a in entries:
 o=originals[a['id']]
 for other in originals.values():other.hide_render=other!=o
 center=o.location+Vector((0,0,o.dimensions.z*.40))
 scale=max(o.dimensions.x,o.dimensions.y,o.dimensions.z)*1.6
 cam.location=center+Vector((scale*.8,-scale,scale*.75))
 cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=scale
 s.render.resolution_x=640;s.render.resolution_y=480;s.cycles.samples=16
 s.render.filepath=str(OUT/'Previews'/(a['id']+'.png'));bpy.ops.render.render(write_still=True)
for o in originals.values():o.hide_render=False
cam.location=(150,-205,160);cam.rotation_euler=(Vector((-3,-20,1))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=245
s.render.resolution_x=1600;s.render.resolution_y=1300
# save without backup clutter
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'SwissWorldKit.blend'))
print('FINALIZED',flush=True)
