#define main WorldBaseMain
#import "MatDefs/WorldObjects/BaseUnshadedFragment.glsllib"
#undef main
#import "MatDefs/WorldObjects/Fade.glsllib"
void main() {
    applyWorldObjectFade();
    WorldBaseMain();
}
