import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.scene.*;
import java.nio.file.*;
public class AirportImportCheck {
 static int triangles;
 static void visit(Spatial s) {
  if(s instanceof Geometry g) {
   if(g.getMesh().getTriangleCount()==0) throw new AssertionError("Empty geometry "+g.getName());
   if(g.getMaterial()==null) throw new AssertionError("No material");
   triangles+=g.getMesh().getTriangleCount();
  }
  if(s instanceof Node n) for(Spatial c:n.getChildren()) visit(c);
 }
 public static void main(String[] args)throws Exception {
  var a=new DesktopAssetManager(true);a.registerLocator(args[0],FileLocator.class);
  try(var paths=Files.walk(Path.of(args[0]))) {
   int count=0;
   for(var file:paths.filter(p->p.toString().endsWith(".glb")).sorted().toList()) {
    var key=Path.of(args[0]).relativize(file).toString();
    Spatial model=a.loadModel(key);triangles=0;visit(model);model.updateGeometricState();
    if(model.getWorldBound()==null)throw new AssertionError("No bound");
    System.out.println("PASS "+key+" triangles="+triangles);
    a.clearCache();count++;
   }
   System.out.println("TOTAL "+count);
  }
 }
}
