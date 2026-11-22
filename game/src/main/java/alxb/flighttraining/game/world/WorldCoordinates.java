package alxb.flighttraining.game.world;
import com.jme3.math.Vector3f;
/** Geografischer Projektursprung in double, Darstellung nahe (0,0,0). */
public final class WorldCoordinates {
 private double x,z;
 public WorldCoordinates(double x,double z){this.x=x;this.z=z;}
 public Vector3f local(double worldX,float y,double worldZ){return new Vector3f((float)(worldX-x),y,(float)(worldZ-z));}
 public Vector3f absolute(Vector3f local){return new Vector3f((float)(x+local.x),local.y,(float)(z+local.z));}
 public TerrainTile tile(Vector3f local){return new TerrainTile(TerrainTile.ORIGIN_X+(int)Math.floor((x+local.x)/1000+0.5),TerrainTile.ORIGIN_Z-(int)Math.floor((z+local.z)/1000+0.5));}
 public void shift(Vector3f delta){x+=delta.x;z+=delta.z;}
 public double x(){return x;} public double z(){return z;}
}
