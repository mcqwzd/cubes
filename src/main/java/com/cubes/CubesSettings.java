/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;

/**
 *
 * @author Carl
 */
public class CubesSettings{
    
    public CubesSettings(Application application){
        if (application != null) {
            assetManager = application.getAssetManager();
        }
    }
    
    // Access to textures
    private AssetManager assetManager;
    
    // JME scene units per block
    // Keep this > 1 to prevent hiding bugs with it
    private float blockSize = 3;
    
    // Size of chunks across (in blocks)
    private int chunkSizeX = 16;
    // Size of chunks tall (in blocks)
    private int chunkSizeY = 16;
    // Size of chunks deep (in blocks)
    private int chunkSizeZ = 16;
    
    // Block render material
    private Material blockMaterial;
    
    // ?
    private int texturesCountX = 16;
    private int texturesCountY = 16;
    
    // Enable light casting?
    private boolean lightsEnabled = false;
    
    // Max light level
    private byte sunlight = 9;
    
    // Chunks visible in any direction
    // TODO: Use this!
    private int chunkViewX = 5;
    private int chunkViewY = 5;
    private int chunkViewZ = 5;
    
    public Vector3Int getChunkViewDistance() {
        return Vector3Int.create(chunkViewX, chunkViewY, chunkViewZ);
    }

    // Accessor for assetManager
    public AssetManager getAssetManager(){
        return assetManager;
    }

    // Accessor for block size
    public float getBlockSize(){
        return blockSize;
    }

    // Modifier to change block size
    // TODO: lock this once terrain starts rendering
    public void setBlockSize(float blockSize){
        this.blockSize = blockSize;
    }

    // Accessor for chunk size
    public int getChunkSizeX(){
        return chunkSizeX;
    }

    // Modifier to change chunk size
    // TODO: lock this once terrain starts rendering
    public void setChunkSizeX(int chunkSizeX){
        this.chunkSizeX = chunkSizeX;
    }

    // Accessor for chunk size
    public int getChunkSizeY(){
        return chunkSizeY;
    }

    // Modifier to change chunk size
    // TODO: lock this once terrain starts rendering
    public void setChunkSizeY(int chunkSizeY){
        this.chunkSizeY = chunkSizeY;
    }

    // Accessor for chunk size
    public int getChunkSizeZ(){
        return chunkSizeZ;
    }

    // Modifier to change chunk size
    // TODO: lock this once terrain starts rendering
    public void setChunkSizeZ(int chunkSizeZ){
        this.chunkSizeZ = chunkSizeZ;
    }

    // Accessor for block material
    public Material getBlockMaterial(){
        return blockMaterial;
    }

    // Modifier for block material using resource path
    public void setDefaultBlockMaterial(String textureFilePath){
        if (assetManager != null) {
            setBlockMaterial(new BlockChunk_Material(assetManager, textureFilePath));
        }
    }

    // Modifier for block material
    // TODO: Update terrain when this gets modified, or lock it once terrain starts to render
    public void setBlockMaterial(Material blockMaterial){
        this.blockMaterial = blockMaterial;
    }

    // ?
    public int getTexturesCountX(){
        return texturesCountX;
    }

    // ?
    public int getTexturesCountY(){
        return texturesCountY;
    }

    // ?
    public void setTexturesCount(int texturesCountX, int texturesCountY){
        this.texturesCountX = texturesCountX;
        this.texturesCountY = texturesCountY;
    }
    
    // Accessor for lights enabled
    public boolean getLightsEnabled() {
        return this.lightsEnabled;
    }
    
    // Modifier for lights enabled
    // TODO: lock this once terrain starts to render
    public void setLightsEnabled(boolean lights) {
        this.lightsEnabled = lights;
    }

    // Accessor for max light level
    byte getSunlightLevel() {
        return this.sunlight;
    }
    
    // Modifier for max light level
    void setSunlightLevel(byte maxSun) {
        this.sunlight = maxSun; 
    }

    // TODO: seperate terrain generation from terrain rendering
    public long getSeed() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    // TODO: seperate terrain generation from terrain rendering
    public long getTerrainSeed() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
