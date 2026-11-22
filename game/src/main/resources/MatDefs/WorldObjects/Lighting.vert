#define main WorldBaseMain
#import "MatDefs/WorldObjects/BaseLightingVertex.glsllib"
#undef main
varying vec2 worldObjectXZ;
void main() {
    WorldBaseMain();
    worldObjectXZ = TransformWorld(vec4(inPosition, 1.0)).xz;
}
