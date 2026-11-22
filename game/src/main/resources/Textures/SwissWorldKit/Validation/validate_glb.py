import struct,json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
lines=[]
for path in sorted((ROOT/'Models').glob('*.glb')):
 data=path.read_bytes();magic,ver,length=struct.unpack_from('<III',data)
 assert magic==0x46546c67 and ver==2 and length==len(data),path
 offset=12;chunks={}
 while offset<len(data):
  n,t=struct.unpack_from('<II',data,offset);offset+=8;chunks[t]=data[offset:offset+n];offset+=n
 doc=json.loads(chunks[0x4e4f534a]);binary=chunks[0x004e4942]
 for v in doc.get('bufferViews',[]):assert v.get('byteOffset',0)+v['byteLength']<=len(binary)
 for im in doc.get('images',[]):assert 'bufferView' in im and 'uri' not in im
 tris=0
 for m in doc['meshes']:
  for p in m['primitives']:
   assert p.get('mode',4)==4
   a=doc['accessors'][p['attributes']['POSITION']];assert all(math.isfinite(v) for v in a['min']+a['max'])
   assert 'TEXCOORD_0' in p['attributes']
   tris+=doc['accessors'][p['indices']]['count']//3
 assert tris>0
 lines.append(f'PASS {path.name}: {tris} triangles, embedded images, UVs, finite bounds, valid buffer ranges')
assert len(lines)==36
(ROOT/'Validation'/'glb-structure.txt').write_text('\n'.join(lines)+'\n')
print('PASS 36 GLB structures')
