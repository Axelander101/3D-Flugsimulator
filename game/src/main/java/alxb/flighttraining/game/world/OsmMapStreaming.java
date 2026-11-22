package alxb.flighttraining.game.world;
import com.google.gson.*;
import com.jme3.math.Vector3f;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Begrenzte 1-km-Kartenzellen, asynchron, mit dauerhaftem Cache und OSM-Quell-IDs. */
final class OsmMapStreaming implements AutoCloseable {
 private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("osm-map-loader").factory());
 private final Map<String,Long> attempted=new HashMap<>();
 private final Path cache=Path.of("map-cache");
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
 private Future<List<JsonObject>> pending;
 private String current;
 private long next;
 List<JsonObject> poll(Vector3f absolute) {
  List<JsonObject> result=List.of();
  if(pending!=null&&pending.isDone()){
   try{result=pending.get();attempted.put(current,Long.MAX_VALUE);}catch(Exception e){attempted.put(current,System.currentTimeMillis()+300000);System.err.println("OSM: "+e.getMessage());}
   pending=null;
  }
  if(pending==null&&System.currentTimeMillis()>next){
   int x=(int)Math.floor((absolute.x+2621500)/1000),z=(int)Math.floor((1092500-absolute.z)/1000);
   outer:for(int radius=0;radius<=1;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
    String id=(x+dx)+"_"+(z+dz);
    if(attempted.getOrDefault(id,0L)>System.currentTimeMillis())continue;
    current=id;int east=x+dx,north=z+dz;pending=worker.submit(()->fetch(east,north));next=System.currentTimeMillis()+5000;break outer;
   }
  }
  return result;
 }
 private List<JsonObject> fetch(int east,int north)throws Exception {
  double[] lower=wgs(east*1000.0,north*1000.0),upper=wgs((east+1)*1000.0,(north+1)*1000.0);
  if(lower[0]>=47.451&&upper[0]<=47.469&&lower[1]>=8.541&&upper[1]<=8.569){
   try(InputStream bundled=OsmMapStreaming.class.getResourceAsStream("/MapData/Zuerich_Source.osm")){
    if(bundled!=null)return parse(bundled);
   }
  }
  Files.createDirectories(cache);Path file=cache.resolve(east+"_"+north+".osm");
  if(!Files.exists(file)){
   double[] sw=wgs(east*1000.0,north*1000.0),ne=wgs((east+1)*1000.0,(north+1)*1000.0);
   if(sw[0]<45.7||ne[0]>47.95||sw[1]<5.8||ne[1]>10.7)return List.of();
   String bbox=String.format(Locale.ROOT,"%.7f,%.7f,%.7f,%.7f",sw[1]-0.001,sw[0]-0.001,ne[1]+0.001,ne[0]+0.001);
   HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.openstreetmap.org/api/0.6/map?bbox="+bbox))
    .header("User-Agent","FlightTraining-map-streaming/1.0").timeout(Duration.ofSeconds(35)).GET().build();
   HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
   byte[] bytes;try(InputStream in=response.body()){if(response.statusCode()!=200)throw new IOException("HTTP "+response.statusCode());bytes=in.readNBytes(20_000_001);}
   if(bytes.length>20_000_000)throw new IOException("Kartenabschnitt zu groß");
   // Vor dem Speichern parsen, damit Fehlerantworten nicht dauerhaft im Cache landen.
   List<JsonObject> entries=parse(new ByteArrayInputStream(bytes));
   Path temporary=Files.createTempFile(cache,"osm-",".part");Files.write(temporary,bytes);Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
   return entries;
  }
  try(InputStream in=Files.newInputStream(file)){return parse(in);}
 }
 static List<JsonObject> parse(InputStream input)throws Exception {
  var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  Document doc=factory.newDocumentBuilder().parse(input);
  Map<String,double[]> points=new HashMap<>();List<JsonObject> result=new ArrayList<>();
  NodeList nodes=doc.getElementsByTagName("node");
  for(int i=0;i<nodes.getLength();i++){
   Element e=(Element)nodes.item(i);double[] p=lv(Double.parseDouble(e.getAttribute("lat")),Double.parseDouble(e.getAttribute("lon")));points.put(e.getAttribute("id"),p);
   Map<String,String> t=tags(e);if("tree".equals(t.get("natural")))result.add(entry("node/"+e.getAttribute("id"),"Tree_Broadleaf_12m",p[0],p[1],0,1));
  }
  NodeList ways=doc.getElementsByTagName("way");
  for(int i=0;i<ways.getLength();i++){
   Element way=(Element)ways.item(i);Map<String,String> t=tags(way);boolean forest="forest".equals(t.get("landuse"))||"wood".equals(t.get("natural"));
   if((!t.containsKey("building")||"no".equals(t.get("building")))&&!forest)continue;
   List<double[]> polygon=new ArrayList<>();NodeList refs=way.getElementsByTagName("nd");
   for(int j=0;j<refs.getLength();j++){double[] p=points.get(((Element)refs.item(j)).getAttribute("ref"));if(p!=null)polygon.add(p);}
   if(polygon.size()<4 || !((Element)refs.item(0)).getAttribute("ref").equals(((Element)refs.item(refs.getLength()-1)).getAttribute("ref")))continue;
   String id="way/"+way.getAttribute("id");
   double minX=polygon.stream().mapToDouble(p->p[0]).min().orElse(0),maxX=polygon.stream().mapToDouble(p->p[0]).max().orElse(0);
   double minZ=polygon.stream().mapToDouble(p->p[1]).min().orElse(0),maxZ=polygon.stream().mapToDouble(p->p[1]).max().orElse(0);
   if(forest){
    int count=0;
    for(double x=Math.ceil(minX/45)*45;x<maxX&&count<500;x+=45)for(double z=Math.ceil(minZ/45)*45;z<maxZ&&count<500;z+=45)
     if(inside(x,z,polygon)){result.add(entry(id+"/tree/"+(int)x+"/"+(int)z,"Tree_Broadleaf_12m",x,z,0,1));count++;}
    continue;
   }
   // Polygon-Flächenschwerpunkt statt arithmetischem Mittel der Stützpunkte.
   double area=0,cx=0,cz=0;double baseX=polygon.get(0)[0],baseZ=polygon.get(0)[1];
   double longest=0,heading=0;
   for(int j=1;j<polygon.size();j++){
    double x1=polygon.get(j-1)[0]-baseX,z1=polygon.get(j-1)[1]-baseZ,x2=polygon.get(j)[0]-baseX,z2=polygon.get(j)[1]-baseZ;
    double cross=x1*z2-x2*z1;area+=cross;cx+=(x1+x2)*cross;cz+=(z1+z2)*cross;
    double length=Math.hypot(x2-x1,z2-z1);if(length>longest){longest=length;heading=Math.toDegrees(Math.atan2(x2-x1,z2-z1));}
   }
   if(Math.abs(area)<8)continue;cx=baseX+cx/(3*area);cz=baseZ+cz/(3*area);
   String kind=t.get("building");String model="House_Stucco";
   if("hangar".equals(kind)||"industrial".equals(kind)||"warehouse".equals(kind))model="Hangar_36x30";
   else if("apartments".equals(kind)||"commercial".equals(kind))model="Apartment_3Floors";
   float nominal=model.startsWith("Hangar")?36:model.startsWith("Apartment")?22:11;
   float scale=(float)Math.max(0.5,Math.min(3.5,Math.sqrt(Math.abs(area)/2)/nominal));
   result.add(entry(id,model,cx,cz,(float)heading+90f,scale));
  }
  return result;
 }
 private static Map<String,String> tags(Element e){Map<String,String> t=new HashMap<>();NodeList tags=e.getElementsByTagName("tag");for(int i=0;i<tags.getLength();i++){Element x=(Element)tags.item(i);t.put(x.getAttribute("k"),x.getAttribute("v"));}return t;}
 private static JsonObject entry(String id,String model,double east,double north,float heading,float scale){
  JsonObject o=new JsonObject();o.addProperty("sourceId",id);o.addProperty("model","Textures/SwissWorldKit/Models/"+model+"_LOD1.glb");o.addProperty("east",east);o.addProperty("north",north);o.addProperty("headingDeg",heading);o.addProperty("scale",scale);return o;
 }
 private static boolean inside(double x,double z,List<double[]> p){boolean in=false;for(int i=0,j=p.size()-1;i<p.size();j=i++)if((p.get(i)[1]>z)!=(p.get(j)[1]>z)&&x<(p.get(j)[0]-p.get(i)[0])*(z-p.get(i)[1])/(p.get(j)[1]-p.get(i)[1])+p.get(i)[0])in=!in;return in;}
 static double[] lv(double lat,double lon){double a=(lat*3600-169028.66)/10000,b=(lon*3600-26782.5)/10000;return new double[]{2600072.37+211455.93*b-10938.51*b*a-0.36*b*a*a-44.54*b*b*b,1200147.07+308807.95*a+3745.25*b*b+76.63*a*a-194.56*b*b*a+119.79*a*a*a};}
 static double[] wgs(double east,double north){double y=(east-2600000)/1e6,x=(north-1200000)/1e6;return new double[]{(16.9023892+3.238272*x-0.270978*y*y-0.002528*x*x-0.0447*y*y*x-0.014*x*x*x)*100/36,(2.6779094+4.728982*y+0.791484*y*x+0.1306*y*x*x-0.0436*y*y*y)*100/36};}
 public void close(){if(pending!=null)pending.cancel(true);worker.shutdownNow();client.shutdownNow();}
}
