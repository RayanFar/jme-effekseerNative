package com.jme.effekseer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Caps;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.RendererException;
import com.jme3.renderer.opengl.GL;
import com.jme3.renderer.opengl.GLRenderer;
import com.jme3.system.NativeLibraryLoader;
import com.jme3.system.Platform;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import org.lwjgl.opengl.GL30;

import Effekseer.swig.EffekseerBackendCore;
import Effekseer.swig.EffekseerEffectCore;
import Effekseer.swig.EffekseerManagerCore;
import Effekseer.swig.EffekseerTextureType;

public class Effekseer{
    /**
     *         Effekseer.init(AssetManager);
     *         Effekseer.update(float tpf);
     *         Effekseer.render(GLRenderer,Camera,FrameBuffer);
     */
    
    static{
        NativeLibraryLoader.registerNativeLibrary("effekseer",Platform.Linux64,"native/linux/x86_64/libEffekseerNativeForJava.so");
        NativeLibraryLoader.registerNativeLibrary("effekseer",Platform.Windows64,"native/windows/x86_64/EffekseerNativeForJava.dll");
        NativeLibraryLoader.loadNativeLibrary("effekseer",true);
    }

    private static class State{
        EffekseerManagerCore core;
        AssetManager am;
        final float v16[]=new float[16];
        final Matrix4f m4=new Matrix4f();

        

        Texture2D sceneData;
        final Vector2f frustumNearFar=new Vector2f();
        final Vector2f resolution=new Vector2f();
        float particlesHardness,particlesContrast;
    }

    private static ThreadLocal<State> state=new ThreadLocal<State>(){
        @Override
        protected State initialValue() {
            EffekseerBackendCore.InitializeAsOpenGL();
            State state=new State();
            state.core=new EffekseerManagerCore();
            state.core.Initialize(8000);
            return state;
        }
    };

    private static State getState() {
        return state.get();
    }

    public static void init(AssetManager am) {        
        State state=getState();
        if(state.am!=am){
            if(state.am!=null){
                state.am.unregisterLoader(EffekseerLoader.class);
            }
            state.am=am;
            am.registerLoader(EffekseerLoader.class,"efkefc");
        }
    }

    public static void destroy(){
        State  s=getState();
        if(s.am!=null){
            s.am.unregisterLoader(EffekseerLoader.class);
            s.am=null;
        }
        
        s.core.delete();
        EffekseerBackendCore.Terminate();
        state.remove();
    }

    public static void update(float tpf){

        State  state=getState();
        float t=tpf / (1.0f / 60.0f);
        state.core.Update(t);
    }

    private static Texture2D getSceneData(GLRenderer renderer,Camera cam,float particlesHardness,float particlesContrast) {
        State  state=getState();

        boolean rebuildSceneData=false;
        if(
           state.sceneData==null
            ||state.frustumNearFar.x!=cam.getFrustumNear()
            ||state.frustumNearFar.y!=cam.getFrustumFar()
            ||state.resolution.x!=cam.getWidth()
            ||state.resolution.y!=cam.getHeight()
            ||state.particlesHardness!=particlesHardness
            ||state.particlesContrast!=particlesContrast
        ){
            rebuildSceneData=true;      
        }

        if( rebuildSceneData){
            state.frustumNearFar.x=cam.getFrustumNear();
            state.frustumNearFar.y=cam.getFrustumFar();
            state.resolution.x=cam.getWidth();
            state.resolution.y=cam.getHeight();
            state.particlesHardness=particlesHardness;
            state.particlesContrast=particlesContrast;

            if(state.sceneData==null){
                Image img=null;
                if(renderer.getCaps().contains(Caps.FloatTexture)){
                    ByteBuffer data=BufferUtils.createByteBuffer(2/*w*/ *1 /*h*/ * 3 /*components*/ * 4 /*B x component*/ );
                    img=new Image(Format.RGB32F,2,1,data,ColorSpace.Linear);
                }else{
                    ByteBuffer data=BufferUtils.createByteBuffer(2/*w*/ * 1 /*h*/ * 3 /*components*/ * 2 /*B x component*/ );
                    img=new Image(Format.RGB16F_to_RGB111110F,2,1,data,ColorSpace.Linear);
                }
                state.sceneData=new Texture2D(img);
            }

            if(renderer.getCaps().contains(Caps.FloatTexture)){
                ByteBuffer data=state.sceneData.getImage().getData(0);
                data.rewind();
                data.putFloat(state.resolution.x);
                data.putFloat(state.resolution.y);
                data.putFloat(particlesHardness);
                data.putFloat(state.frustumNearFar.x);
                data.putFloat(state.frustumNearFar.y);
                data.putFloat(particlesContrast);
                data.rewind();
            }else if(renderer.getCaps().contains(Caps.PackedFloatTexture)){
                ByteBuffer data=state.sceneData.getImage().getData(0);
                data.rewind();
                data.putShort(FastMath.convertFloatToHalf(state.resolution.x));
                data.putShort(FastMath.convertFloatToHalf(state.resolution.y));
                data.putShort(FastMath.convertFloatToHalf(particlesHardness));
                data.putShort(FastMath.convertFloatToHalf(state.frustumNearFar.x));
                data.putShort(FastMath.convertFloatToHalf(state.frustumNearFar.y));
                data.putShort(FastMath.convertFloatToHalf(particlesContrast));
                data.rewind();
            }else{
                throw new RendererException("Unsupported Platform. FloatTexture or PackedFloatTexture required for jme-effekseerNative.");
            }
            state.sceneData.getImage().setUpdateNeeded();
        }

		return state. sceneData;
	}

    // public static void render(Renderer renderer,Camera cam,FrameBuffer renderTarget,Texture sceneDepth) {
    //     render(renderer,cam,renderTarget,null,sceneDepth);
    // }
    // public static void render(Renderer renderer,Camera cam,FrameBuffer renderTarget,FrameBuffer sceneFb) {
    //     render(renderer,cam,renderTarget,sceneFb,sceneFb.getDepthBuffer().getTexture());
    // }

    public static void render(Renderer renderer,Camera cam,FrameBuffer renderTarget,Texture sceneDepth) {
        render( renderer, cam, renderTarget, sceneDepth,0.1f,2.0f) ;
    }
    public static void render(Renderer renderer,Camera cam,FrameBuffer renderTarget,Texture sceneDepth,float particlesHardness,float particlesContrast) {
        assert sceneDepth!=null;
   
        State  state=getState();
      
        if(!(renderer instanceof GLRenderer)){
            throw new RuntimeException("Only GLRenderer supported at this moment");
        }

        GLRenderer gl=(GLRenderer)renderer; 
         
        gl.setFrameBuffer(renderTarget);
        gl.setBackgroundColor(ColorRGBA.BlackNoAlpha);
        gl.clearBuffers(true, true, false);


        // HACKS HACKS
        // if(sceneFb!=null){
        //     GL30.glBindFramebuffer(GL30. GL_READ_FRAMEBUFFER, sceneFb.getId());
        //     GL30.glBindFramebuffer(GL30. GL_DRAW_FRAMEBUFFER, renderTarget.getId());
        //     GL30.glBlitFramebuffer(0,0,sceneFb.getWidth(),sceneFb.getHeight(), 0,0, renderTarget.getWidth(), renderTarget.getHeight(), GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        // }
        gl.setTexture(12, sceneDepth);
        gl.setTexture(13, getSceneData(gl,cam,particlesHardness,particlesContrast));
        //
                        
        cam.getProjectionMatrix().get(state.v16,true);
        state.core.SetProjectionMatrix(state.v16[0],state.v16[1],state.v16[2],state.v16[3],state.v16[4],state.v16[5],state.v16[6],state.v16[7],state.v16[8],state.v16[9],state.v16[10],state.v16[11],state.v16[12],state.v16[13],state.v16[14],state.v16[15] );

        cam.getViewMatrix().get(state.v16,true);
        state.core.SetCameraMatrix(state.v16[0],state.v16[1],state.v16[2],state.v16[3],state.v16[4],state.v16[5],state.v16[6],state.v16[7],state.v16[8],state.v16[9],state.v16[10],state.v16[11],state.v16[12],state.v16[13],state.v16[14],state.v16[15]  );
        
        state.core.DrawBack();
        state.core.DrawFront();

    }

    static int playEffect(EffekseerEffectCore e){
        State state=getState();
        return state.core.Play(e);
    }

    static void pauseEffect(int e,boolean v){
        State state=getState();
         state.core.SetPaused(e,v);
    }

    static  void setEffectVisibility(int e,boolean v){
        State state=getState();
         state.core.SetShown(e,v);
    }

    static boolean isEffectAlive(int e){
        State state=getState();
        return state.core.Exists(e);
    }

    static void setDynamicInput(int e,int index,float value){
        State state=getState();
        state.core.SetDynamicInput(e,index,value);
    }


    static void setEffectTransform(int handler,Transform tr){
        State state=getState();
        state.m4.setTranslation(tr.getTranslation());
        state.m4.setRotationQuaternion(tr.getRotation());
        state.m4.setScale(tr.getScale());
        
        state.m4.get(state.v16, true);
        state.core.SetEffectTransformMatrix(handler, state.v16[0],state.v16[1],state.v16[2],state.v16[3],state.v16[4],state.v16[5],state.v16[6],state.v16[7],state.v16[8],state.v16[9],state.v16[10],state.v16[11] );
    }

    private static byte[] readAll(InputStream is) throws IOException {
        byte chunk[]=new byte[1024*1024];
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        int read;
        while((read=is.read(chunk)) != -1) bos.write(chunk,0,read);
        return bos.toByteArray();
    }


    private static String normalizePath(String ...parts){
        String path="";

        for(String part:parts){
            path+="/"+part.replace("\\", "/");
        }

        path=path.replace("/",File.separator);
        path=Paths.get(path).normalize().toString();
        path=path.replace("\\", "/");

        
        int sStr=0;
        while(path.startsWith("../",sStr))sStr+=3;
        path=path.substring(sStr);

        if(path.startsWith("/"))path=path.substring(1);

        return path;
    }

    private static Collection<String> guessPossibleRelPaths(String root,String opath){
        root=normalizePath(root);

        String path=opath;
        path=normalizePath(path);       
        
        System.out.println("Guesses for "+opath+" normalized "+path+" in root "+root);

        ArrayList<String> paths=new ArrayList<String>();   

        paths.add(path);
        paths.add(normalizePath(root,path));

        ArrayList<String> pathsNoRoot=new ArrayList<String>();   

        while(true){
            int i=path.indexOf("/");
            if(i==-1)break;
            path=path.substring(i+1);
            if(path.isEmpty())break;
            pathsNoRoot.add(path);
            paths.add(normalizePath(root,path));
        }

        paths.addAll(pathsNoRoot);


        for(String p:paths){
            System.out.println(" > "+p);
        }

        return paths;
    }

    private static InputStream openStream(AssetManager am, String rootPath,String path) {
        AssetInfo info=null;
        Collection<String> guessedPaths=guessPossibleRelPaths(rootPath,path);
        for(String p:guessedPaths){
            try{
                System.out.println("Try to locate assets "+Paths.get(path).getFileName()+" in "+p);
                info=am.locateAsset(new AssetKey(p));
                if(info!=null){
                    System.out.println("Found in "+p);
                    break;
                }
            }catch(AssetNotFoundException e){
                System.out.println("Not found in "+p);
            }
        }
        if(info==null)throw new AssetNotFoundException(path);
        return info.openStream();
    }

    public static EffekseerEmitterControl loadEffect(AssetManager am, String path, InputStream is) throws IOException {
        String root=path.contains("/")?path.substring(0,path.lastIndexOf("/")):"";
        assert !path.endsWith("/");

        EffekseerEffectCore effectCore=new EffekseerEffectCore();
        byte bytes[]=readAll(is);
        if(!effectCore.Load(bytes,bytes.length,1f)){
            throw new AssetLoadException("Can't load effect "+path);
        }

        EffekseerTextureType[] textureTypes=new EffekseerTextureType[]{
            EffekseerTextureType.Color,EffekseerTextureType.Normal,EffekseerTextureType.Distortion};

        // Textures
        for(int t=0;t < 3;t++){
            for(int i=0;i < effectCore.GetTextureCount(textureTypes[t]);i++){
                String p=effectCore.GetTexturePath(i,textureTypes[t]);
                InputStream iss=openStream(am,root,p);
                bytes=readAll(iss);
                if(!effectCore.LoadTexture(bytes,bytes.length,i,textureTypes[t]) ){
                    throw new AssetLoadException("Can't load effect texture "+p);
                }  else{
                    System.out.println("Load textures "+bytes.length+" bytes");
                }
            }
        }

        // Models
        for(int i=0;i < effectCore.GetModelCount();i++){
            String p=effectCore.GetModelPath(i);
            InputStream iss=openStream(am,root,p);
            bytes=readAll(iss);
            if(!effectCore.LoadModel(bytes,bytes.length,i) ){
                throw new AssetLoadException("Can't effect load model "+p);
            }        
        }

        // Materials
        for(int i=0;i < effectCore.GetMaterialCount();i++){   
            String p= effectCore.GetMaterialPath(i); 
            InputStream iss=openStream(am,root,p);            
            bytes=readAll(iss);
            if(!effectCore.LoadMaterial(bytes,bytes.length,i) ){
                throw new AssetLoadException("Can't load effect material "+p);
            }        
        }


        // Sounds?
        
        
        return new EffekseerEmitterControl(effectCore);
    }

}