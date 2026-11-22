package alxb.flighttraining.game.ui;

import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.material.*;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Reusable full-window overlay. The bar follows preparation progress, not a timed simulation. */
public final class LoadingScreen {
    private final AssetManager assets;
    private final Node gui;
    private final Node layer=new Node("MapLoadingScreen");
    private final Picture backdrop=new Picture("LoadingBackdrop");
    private final Geometry track,fill,runner;
    private final BitmapText title,caption,status,percentage,hint;
    private BufferedImage photo;
    private int width,height;
    private float target,displayed,clock;
    private boolean active;

    public LoadingScreen(AssetManager assets,Node gui,BitmapFont font){
        this.assets=assets;this.gui=gui;
        layer.setLocalTranslation(0,0,100);
        layer.attachChild(backdrop);
        title=text(font,38,ColorRGBA.White);title.setText(English.text("FLIGHT TRAINING"));
        caption=text(font,14,new ColorRGBA(.35f,.8f,1,1));caption.setText(English.text("FLUG WIRD VORBEREITET"));
        status=text(font,17,new ColorRGBA(.85f,.9f,.96f,1));
        percentage=text(font,18,ColorRGBA.White);
        hint=text(font,13,new ColorRGBA(.68f,.75f,.84f,1));hint.setText(English.text("Terrain und Umgebung werden für deinen Start vorbereitet."));
        track=rectangle("LoadingTrack",new ColorRGBA(.18f,.24f,.32f,1));
        fill=rectangle("LoadingProgress",new ColorRGBA(.25f,.76f,1,1));
        runner=rectangle("LoadingActivity",new ColorRGBA(.65f,.91f,1,1));
    }
    private BitmapText text(BitmapFont font,int size,ColorRGBA color){
        BitmapText text=new BitmapText(font);text.setSize(size);text.setColor(color);layer.attachChild(text);return text;
    }
    private Geometry rectangle(String name,ColorRGBA color){
        Geometry geometry=new Geometry(name,new Quad(1,1));Material material=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color",color);geometry.setMaterial(material);layer.attachChild(geometry);return geometry;
    }
    public void begin(int width,int height){
        if(photo==null)photo=MenuImages.load(assets,"LSZH","Zürich");
        target=displayed=clock=0;active=true;status.setText(English.text("Höhendaten werden angefordert …"));
        layout(width,height);gui.attachChild(layer);percentage.setText(English.text("…"));update(0,width,height);
    }
    public void progress(String message,float progress){
        if(!active)return;
        status.setText(English.text(message));target=Math.max(target,Math.min(.99f,Math.max(0,progress)));
    }
    public void update(float tpf,int width,int height){
        if(!active)return;
        if(this.width!=width||this.height!=height)layout(width,height);
        clock+=Math.max(0,tpf);
        displayed+=(target-displayed)*(1-(float)Math.exp(-8*Math.max(0,tpf)));
        float barWidth=barWidth();fill.setLocalScale(Math.max(.001f,barWidth*displayed),7,1);
        percentage.setText(English.text(target==0?"…":Math.round(displayed*100)+" %"));
        percentage.setLocalTranslation(margin()+barWidth-percentage.getLineWidth(),barY()+38,2);
        runner.setCullHint(target==0?Spatial.CullHint.Inherit:Spatial.CullHint.Always);
        runner.setLocalTranslation(margin()+(barWidth-70)*(.5f+.5f*(float)Math.sin(clock*1.8f)),barY(),3);
        runner.setLocalScale(Math.min(70,barWidth),7,1);
    }
    private float margin(){return Math.max(24,Math.min(80,width*.065f));}
    private float barWidth(){return Math.max(1,Math.min(760,width-2*margin()));}
    private float barY(){return Math.max(60,height*.13f);}
    private void layout(int width,int height){
        this.width=Math.max(1,width);this.height=Math.max(1,height);
        BufferedImage image=new BufferedImage(this.width,this.height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try{
            g.setColor(new Color(12,22,36));g.fillRect(0,0,this.width,this.height);
            if(photo!=null){
                float scale=Math.max((float)this.width/photo.getWidth(),(float)this.height/photo.getHeight());
                int w=Math.round(photo.getWidth()*scale),h=Math.round(photo.getHeight()*scale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(photo,(this.width-w)/2,(this.height-h)/2,w,h,null);
            }
            g.setColor(new Color(4,12,24,105));g.fillRect(0,0,this.width,this.height);
            g.setPaint(new GradientPaint(0,0,new Color(3,10,20,20),0,this.height,new Color(3,10,20,245)));
            g.fillRect(0,0,this.width,this.height);
        }finally{g.dispose();}
        Texture2D texture=new Texture2D(new AWTLoader().load(image,true));
        backdrop.setTexture(assets,texture,false);backdrop.setWidth(this.width);backdrop.setHeight(this.height);
        caption.setLocalTranslation(margin(),barY()+170,2);
        title.setSize(Math.min(38,Math.max(22,(this.width-2*margin())/15)));title.setLocalTranslation(margin(),barY()+140,2);
        status.setBox(new com.jme3.font.Rectangle(0,0,barWidth(),60));
        status.setSize(this.width<650?13:17);status.setLocalTranslation(margin(),barY()+76,2);
        hint.setLocalTranslation(margin(),barY()-23,2);
        track.setLocalTranslation(margin(),barY(),1);track.setLocalScale(barWidth(),7,1);
        fill.setLocalTranslation(margin(),barY(),2);fill.setLocalScale(Math.max(.001f,barWidth()*displayed),7,1);
    }
    public void finish(){active=false;target=displayed=1;layer.removeFromParent();}
    public boolean isActive(){return active;}
}
