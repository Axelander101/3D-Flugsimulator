package alxb.flighttraining.game.audio;

import alxb.flighttraining.game.flight.*;
import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.config.DogfightSettings;
import com.jme3.asset.AssetManager;
import com.jme3.audio.*;
import com.jme3.input.*;
import com.jme3.input.controls.*;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.renderer.Camera;
import com.google.gson.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Buffered game audio; simulation events drive sounds, never animation frames or wall-clock timers. */
public final class GameAudio {
    private final Node root=new Node("FlightAudio");
    private final Listener listener;
    private final boolean available;
    private final Map<String,AudioNode> nodes=new HashMap<>();
    private final Map<String,Float> duration=new HashMap<>(),cooldown=new HashMap<>();
    private final Map<String,Float> volumes=new HashMap<>();
    private final List<AudioNode> spatial=new ArrayList<>();
    private final Map<String,List<AudioNode>> pools=new HashMap<>();
    private boolean active,initialized,grounded,engine,park,gearSelection;
    private float master=.65f,unmuted=.65f,voiceTime,gunTime,contactTime,elapsed,enemyShotCooldown,impactCooldown;
    private int voicePriority=-1,lastContacts,airport=-1;
    private AudioNode voice;
    private final float[] previous=new float[6];
    public GameAudio(AssetManager assets,Node scene,Listener listener,AudioRenderer renderer,InputManager input){
        this.listener=listener!=null?listener:new Listener();available=renderer!=null;scene.attachChild(root);setVolume(master);
        try(var reader=new InputStreamReader(assets.locateAsset(new com.jme3.asset.AssetKey<>("Sounds/Flight/manifest.json")).openStream(),StandardCharsets.UTF_8)){
            for(var entry:JsonParser.parseReader(reader).getAsJsonObject().entrySet()){
                String name=entry.getKey();duration.put(name,entry.getValue().getAsJsonObject().get("seconds").getAsFloat());
                AudioNode node=new AudioNode(assets,"Sounds/Flight/"+name+".wav",AudioData.DataType.Buffer);node.setPositional(false);root.attachChild(node);nodes.put(name,node);
            }
        }catch(IOException error){throw new IllegalStateException("Cannot load flight sound manifest",error);}
        for(String name:new String[]{"engine_idle","engine_power","afterburner","wind","cannon","hydraulics","brakes","seeker","caution"})nodes.get(name).setLooping(true);
        for(String name:new String[]{"launch","impact","explosion","cannon"}){
            List<AudioNode> pool=new ArrayList<>();for(int i=0;i<8;i++){
                AudioNode n=nodes.get(name).clone();n.setPositional(true);n.setLooping(false);n.setRefDistance(15);n.setMaxDistance(2500);root.attachChild(n);pool.add(n);spatial.add(n);
            }pools.put(name,pool);
        }
        for(String name:new String[]{"AudioMute","AudioDown","AudioUp"})input.addMapping(name,new KeyTrigger(name.equals("AudioMute")?KeyInput.KEY_F8:name.equals("AudioDown")?KeyInput.KEY_F9:KeyInput.KEY_F10));
        ActionListener controls=(name,pressed,tpf)->{if(!pressed)return;if(name.equals("AudioMute"))toggleMute();else setVolume(master+(name.equals("AudioUp")?.1f:-.1f));};input.addListener(controls,"AudioMute","AudioDown","AudioUp");
    }
    public float getVolume(){return master;}
    public void toggleMute(){if(master>0){unmuted=master;setVolume(0);}else setVolume(unmuted);}
    public void uiClick(){AudioNode n=nodes.get("switch");if(available){n.setVolume(.2f);n.playInstance();}}
    public void setVolume(float value){master=FastMath.clamp(value,0,1);listener.setVolume(master);}
    public void reset(){stopAll();active=false;initialized=false;elapsed=gunTime=enemyShotCooldown=0;airport=-1;lastContacts=0;cooldown.clear();}
    private void stop(AudioNode n){if(available)n.stop();}
    private void stopAll(){for(AudioNode n:nodes.values())stop(n);for(AudioNode n:spatial)stop(n);voice=null;voiceTime=0;voicePriority=-1;}
    public void setActive(boolean enabled){if(active==enabled)return;active=enabled;if(!enabled)stopAll();}
    private void loop(String name,float volume,float pitch){
        AudioNode n=nodes.get(name);volume=FastMath.clamp(volume,0,1);volumes.put(name,volume);n.setVolume(volume);n.setPitch(FastMath.clamp(pitch,.5f,2));
        if(!available)return;if(active&&volume>.002f){if(n.getStatus()!=AudioSource.Status.Playing)n.play();}else n.stop();
    }
    public void effect(String name){if(!active)return;AudioNode n=nodes.get(name);if(n!=null&&available){n.setVolume(.45f);n.playInstance();}}
    public void at(String name,Vector3f position){
        if(!active)return;List<AudioNode> pool=pools.get(name);if(pool==null)return;
        if(name.equals("cannon")){if(enemyShotCooldown>0)return;enemyShotCooldown=.10f;}
        if(name.equals("impact")){if(impactCooldown>0)return;impactCooldown=.045f;}
        AudioNode selected=pool.stream().filter(n->n.getStatus()!=AudioSource.Status.Playing).findFirst().orElse(pool.get(0));
        if(available)selected.stop();selected.setLocalTranslation(root.worldToLocal(position,new Vector3f()));selected.setVolume(name.equals("cannon")?.35f:.65f);if(available)selected.play();
    }
    public void cannonFire(){if(active)gunTime=.12f;}
    public void rebase(Vector3f delta){root.setLocalTranslation(Vector3f.ZERO);for(AudioNode n:spatial)n.move(delta.negate());}
    private boolean speak(String name,int priority,float repeat){
        if(cooldown.getOrDefault(name,0f)>0||voiceTime>0&&priority<=voicePriority)return false;
        if(voice!=null)stop(voice);voice=nodes.get(name);if(voice==null)return false;
        voiceTime=duration.get(name)+.15f;voicePriority=priority;cooldown.put(name,repeat);voice.setVolume(.8f);if(available)voice.play();return true;
    }
    public void update(float dt,FlightPhysics physics,Enemy enemies,Node plane,Camera camera,Vector3f absolute,float ground,int stores,boolean weapons){
        if(!active||!Float.isFinite(dt)||dt<=0)return;dt=Math.min(dt,.25f);elapsed+=dt;gunTime=Math.max(0,gunTime-dt);enemyShotCooldown=Math.max(0,enemyShotCooldown-dt);impactCooldown=Math.max(0,impactCooldown-dt);voiceTime=Math.max(0,voiceTime-dt);final float step=dt;cooldown.replaceAll((k,v)->Math.max(0,v-step));
        listener.setLocation(camera.getLocation());listener.setRotation(camera.getRotation());listener.setVelocity(physics.getVelocity());
        var s=physics.getSystems();float speed=physics.getSpeed(),power=physics.getEnginePower();
        float duck=voiceTime>0?.45f:1;
        loop("engine_idle",physics.isEngineRunning()||power>.005f?(.10f+.12f*power)*duck:0,.75f+.4f*power);
        loop("engine_power",power*.25f*duck,.65f+.7f*power);loop("afterburner",Math.max(0,(power-.85f)/.15f)*.24f*duck,.9f+.1f*power);
        loop("wind",FastMath.clamp(physics.getAirspeed()/350,0,1)*.18f*duck,.8f+FastMath.clamp(speed/500,0,.5f));
        loop("cannon",gunTime>0?.42f:0,1);loop("brakes",s.isOnGround()&&s.getBrake()>.5f&&speed>1?.16f:0,1);
        float[] now={s.getGear(),s.getAirbrake(),s.getFlaps(),s.getCanopy(),s.getHook(),s.getWingFold()};boolean moving=false;
        if(initialized){
            for(int i=0;i<now.length;i++)moving|=Math.abs(now[i]-previous[i])>.00001f;
            if(engine!=physics.isEngineRunning()){effect("switch_heavy");speak(physics.isEngineRunning()?"engine_on":"engine_off",1,2);}
            if(park!=s.isParkingBrake()){effect("switch");speak(s.isParkingBrake()?"brake_on":"brake_off",0,1);}
            if(gearSelection!=s.isGearDownSelected())effect("gear");
            if(previous[0]<.99f&&s.getGear()>=.99f)speak("gear_down",1,2);
            if(previous[0]>.01f&&s.getGear()<=.01f)speak("gear_up",1,2);
            if(!grounded&&s.isOnGround())effect("touchdown");
            for(int i=1;i<now.length;i++)if(Math.abs(now[i]-previous[i])>.00001f&&Math.abs(now[i]-previous[i])<dt*2&&cooldown.getOrDefault("mechanical_click",0f)<=0){effect("switch");cooldown.put("mechanical_click",2f);break;}
        }
        loop("hydraulics",moving?.12f:0,1);System.arraycopy(now,0,previous,0,now.length);initialized=true;grounded=s.isOnGround();engine=physics.isEngineRunning();park=s.isParkingBrake();gearSelection=s.isGearDownSelected();
        float agl=Float.isFinite(ground)?plane.getWorldTranslation().y-ground:Float.POSITIVE_INFINITY;
        boolean terrainWarning=!grounded&&speed>45&&agl<150&&physics.getVerticalSpeed()<-5&&(s.getGear()<.5f||agl/Math.max(1,-physics.getVerticalSpeed())<5);
        loop("caution",terrainWarning||!grounded&&physics.isStalling()?.07f:0,1);
        if(terrainWarning)speak("terrain",3,5);
        else if(!grounded&&physics.isStalling())speak("stall",2,8);
        else if(physics.getMach()>1.8f)speak("speed",2,12);
        else if(s.getGear()>.1f&&physics.getAirspeed()>135)speak("gear_speed",1,12);
        else if(physics.getNormalG()>7.5f||physics.getNormalG()<-2)speak("g_limit",2,10);
        int contacts=0;boolean seeker=false;
        Vector3f forward=plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate());
        for(int i=0;i<enemies.getCount();i++){
            Vector3f offset=enemies.getPosition(i).subtract(plane.getWorldTranslation());float distance=offset.length();
            if(distance>1&&distance<5000){contacts++;if(offset.divide(distance).dot(forward)>.7f)seeker=true;}
        }
        if(contacts>lastContacts&&elapsed>1)effect("contact");lastContacts=contacts;
        loop("seeker",weapons&&stores>0&&seeker?.08f:0,.5f);
        // Proximity only: this does not determine controlled airspace or grant landing clearance.
        DogfightSettings.Region[] regions=DogfightSettings.Region.values();int nearest=-1;float distance=Float.POSITIVE_INFINITY;
        for(int i=0;i<regions.length;i++){float d=(float)Math.hypot(absolute.x-regions[i].x,absolute.z-regions[i].z);if(d<distance){distance=d;nearest=i;}}
        if(distance>10000)airport=-1;
        if(distance<8000&&airport!=nearest&&elapsed>3&&speak("airport_"+nearest,0,45))airport=nearest;
    }
    public void close(){stopAll();root.removeFromParent();}
}
