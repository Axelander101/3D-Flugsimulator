import json,struct,math
from pathlib import Path
import numpy as np
R=Path(__file__).resolve().parent;OUT=R.parent
report=[];airports=json.loads((OUT/'airports.json').read_text());assert len(airports)==11
for a in airports:
 d=OUT/a['icao'];placements=json.loads((d/'placement.json').read_text());total=0
 expected={d/a['full_model'],*[d/c['file'] for c in a['chunks']]}
 actual=set(d.rglob('*.glb'));assert expected==actual,('Unexpected/missing exports',a['icao'],expected^actual)
 for p in sorted(actual):
  raw=p.read_bytes();magic,version,size=struct.unpack_from('<III',raw);assert magic==0x46546c67 and version==2 and size==len(raw)
  js=struct.unpack_from('<I',raw,12)[0];g=json.loads(raw[20:20+js]);assert 'KHR_draco_mesh_compression' not in g.get('extensionsRequired',[])
  bin=raw[28+js:]
  tri=0
  for mesh in g.get('meshes',[]):
   for prim in mesh['primitives']:
    assert prim.get('mode',4)==4
    pos=g['accessors'][prim['attributes']['POSITION']];assert pos['count']>0 and all(math.isfinite(x)for x in pos['min']+pos['max'])
    assert 'material' in prim
    ia=g['accessors'][prim['indices']];assert ia['count']%3==0;tri+=ia['count']//3
  for view in g.get('bufferViews',[]):assert view.get('byteOffset',0)+view['byteLength']<=len(bin)
  for im in g.get('images',[]):assert 'bufferView' in im
  assert tri>0;report.append(f'PASS {p.relative_to(OUT)}: {tri} triangles');total+=1
 report.append(f'AIRPORT {a["icao"]}: {a["building_parts"]} swissBUILDINGS parts, {a["osm_buildings"]} OSM buildings, {a["surfaces"]} pavement parts, {total} exports')
(OUT/'Validation').mkdir(exist_ok=True);(OUT/'Validation'/'structure.txt').write_text('\n'.join(report)+'\n');print('PASS',len(airports),'airports',sum(1 for s in report if s.startswith('PASS')),'GLBs')
