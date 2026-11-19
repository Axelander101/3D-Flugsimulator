#define main WorldBaseMain
#import "MatDefs/WorldObjects/BaseLightingFragment.glsllib"
#undef main
#import "MatDefs/WorldObjects/Fade.glsllib"
void main() {
    applyWorldObjectFade();
    WorldBaseMain();
}
