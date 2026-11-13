import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.scene.*;
import java.nio.file.*;
public class WorldKitCheck {
 static int count=0;
 static void check(Spatial s){
  if(s instanceof Geometry g){
   if(g.getMesh().getTriangleCount()==0)throw new AssertionError("Empty mesh: "+g.getName());
   count+=g.getMesh().getTriangleCount();
   if(g.getMaterial()==null)throw new AssertionError("Missing material");
  }
  if(s instanceof Node n)for(Spatial c:n.getChildren())check(c);
 }
 public static void main(String[] args)throws Exception{
  var a=new DesktopAssetManager(true);a.registerLocator(args[0],FileLocator.class);
  try(var files=Files.list(Path.of(args[0],"Models"))){
   for(var f:files.filter(p->p.toString().endsWith(".glb")).sorted().toList()){
    count=0;Spatial s=a.loadModel("Models/"+f.getFileName());check(s);s.updateGeometricState();
    if(s.getWorldBound()==null)throw new AssertionError("No bound");
    System.out.println("PASS "+f.getFileName()+" triangles="+count);
   }
  }
 }
}
