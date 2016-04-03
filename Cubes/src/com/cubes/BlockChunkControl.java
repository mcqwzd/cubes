/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import java.io.IOException;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;
import com.cubes.network.*;
import com.jme3.scene.Mesh;
import java.util.HashMap;


/**
 *
 * @author Carl
 */
public class BlockChunkControl extends AbstractControl implements BitSerializable{
    // Reference to settings
    private CubesSettings settings;
    // Chunk to track sunlight in this column of chunks
    private SunlightChunk sunlightChunk; // 2d object tracking sunlight across multiple vertical chunks
    // The strength of sunlight, cached from settings for frequent use
    private byte sunlight = 0;
    // Reference to the terrain this chunk is attached too
    private BlockTerrainControl terrain;
    // Chunk coordinates of this chunk TODO: rename this to chunkLocation
    private Vector3Int location = new Vector3Int();
    // Block coordinate of this chunk (block 0, 0, 0 of this chunk)
    private Vector3Int blockLocation = new Vector3Int();
    // block contents TODO: change this to a larger bit size.  More than 256 types of blocks will be needed
    private byte[][][] blockTypes;
    // tracking light source.  TODO: stop using this for sunlight (use sunlightChunk instead)
    private byte[][][] sunlightSources;
    // tracking light levels from sunlight (other types of sources will be tracked seperatly so sunlight can be dimmed with time of day efficently)
    private byte[][][] sunlightLevels;
    // Node used to attach chunk to scene
    private Node node = new Node("Cube Chunk");
    // Mesh of chunk blocks (Mesh of blocks you can't walk thru?)
    private Geometry optimizedGeometry_Opaque;
    // Mesh of (Mesh of blocks you can see thru?)
    private Geometry optimizedGeometry_Transparent;
    // Flag if this chunk needs a new mesh built
    private boolean needsMeshUpdate = false;
    // Flag if the chunk mesh change will effect physics
    private boolean needsPhysicsUpdate = false;
    // New lights waiting to be processed
    private HashMap<Vector3Int, LightQueueElement> lightsToAdd = new HashMap<Vector3Int, LightQueueElement> ();
    // New opaque blocks waiting to be processed
    private HashMap<Vector3Int, LightQueueElement> lightsToRemove = new HashMap<Vector3Int, LightQueueElement> ();

    // Constructor
    public BlockChunkControl(BlockTerrainControl terrain, Vector3Int location) {
        this(terrain, location.getX(), location.getY(), location.getZ());
    }

    // Constructor
    public BlockChunkControl(BlockTerrainControl terrain, int x, int y, int z){
        this(terrain.getSettings());
        this.terrain = terrain;
        location.set(x, y, z);
        int cX = settings.getChunkSizeX();
        int cY = settings.getChunkSizeY();
        int cZ = settings.getChunkSizeZ();
        blockLocation.set(location.mult(cX, cY, cZ));
        node.setLocalTranslation(new Vector3f(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ()).mult(settings.getBlockSize()));
    }

    // Constructor
    public BlockChunkControl(CubesSettings settings) {
        this.settings = settings;
        int cX = settings.getChunkSizeX();
        int cY = settings.getChunkSizeY();
        int cZ = settings.getChunkSizeZ();
        if (cY > 256) {
            // to support more than 256, blocks on surface (and probobly other things) needs to be larger than byte
            throw new UnsupportedOperationException("Chunks taller than 256 are not supported");
        }
        blockTypes = new byte[cX][cY][cZ];
        sunlight = settings.getSunlightLevel();
    }
    
    @Override
    public void setSpatial(Spatial spatial){
        Spatial oldSpatial = this.spatial;
        super.setSpatial(spatial);
        if(spatial instanceof Node){
            Node parentNode = (Node) spatial;
            parentNode.attachChild(node);
        }
        else if(oldSpatial instanceof Node){
            Node oldNode = (Node) oldSpatial;
            oldNode.detachChild(node);
        }
    }

    @Override
    protected void controlUpdate(float lastTimePerFrame){
    }

    @Override
    protected void controlRender(RenderManager renderManager, ViewPort viewPort){
    }

    @Override
    public Control cloneForSpatial(Spatial spatial){
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    // Get the neighboring block in the direction of face
    // TODO: Move this logic to block navigator
    public Block getNeighborBlock_Global(Vector3Int location, Block.Face face){
        // If not yet attached to a terrain, shortcut neighbor calcuations by returning null
        // as things will need to recalculate when attached to terrain anyways.
        if (terrain == null) {
            return null;
        }
        return terrain.getBlock(getNeighborBlockGlobalLocation(location, face));
    }
    
    // TODO: Move this to blockNavigator
    public Vector3Int getNeighborBlockGlobalLocation(Vector3Int location, Block.Face face){
        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, face);
        neighborLocation.addLocal(blockLocation);
        return neighborLocation;
    }

    // TODO: Move this to blockNavigator
    public Vector3Int getBlockGlobalLocation(Vector3Int location){
        Vector3Int neighborLocation = location.clone();
        neighborLocation.addLocal(blockLocation);
        return neighborLocation;
    }
    
    // Get block within chunk
    // TODO: log if blocks outside chunk are requested
    public Block getBlock(Vector3Int location){
        if(isValidBlockLocation(location)){
            byte blockType = blockTypes[location.getX()][location.getY()][location.getZ()];
            return BlockManager.getBlock(blockType);
        }
        return null;
    }
    
    // Change block within chunk
    public void setBlock(Vector3Int location, Block block){
        if(isValidBlockLocation(location)){
            byte blockType = BlockManager.getType(block);
            blockTypes[location.getX()][location.getY()][location.getZ()] = blockType;
            lightsToRemove.put(location, new LightQueueElement(location, this));
            updateBlockState(location);
            this.markNeedUpdate(true);
        }
    }
    
    // Change block within chunk to air
    public void removeBlock(Vector3Int location){
        if(isValidBlockLocation(location)){
            blockTypes[location.getX()][location.getY()][location.getZ()] = 0;
            lightsToRemove.put(location, new LightQueueElement(location, this));
            updateBlockState(location);
            this.markNeedUpdate(true);
        }
    }
    
    // Mark chunk as needing update and add to update queue
    // @param needPhysicsUpdate 
    public void markNeedUpdate(boolean needPhysicsUpdate) {
        needsMeshUpdate = true;
        this.needsPhysicsUpdate = this.needsPhysicsUpdate || needPhysicsUpdate;
        if (this.terrain != null) {
            this.terrain.addChunkToNeedsUpdateList(this);
        } 
    }
    
    // Return true if block is within chunk size
    private boolean isValidBlockLocation(Vector3Int location){
        return Util.isValidIndex(blockTypes, location);
    }
    
    // Update mesh & notify listeners
    // will return true if physics changed.
    // simple skin updates will still return false.
    public boolean updateSpatial(){
        // if the mesh changed
        if(needsMeshUpdate){
            if(optimizedGeometry_Opaque == null){
                optimizedGeometry_Opaque = new Geometry("Cube optimized_opaque");
                optimizedGeometry_Opaque.setQueueBucket(Bucket.Opaque);
                node.attachChild(optimizedGeometry_Opaque);
                updateBlockMaterial();
            }
            if(optimizedGeometry_Transparent == null){
                optimizedGeometry_Transparent = new Geometry("Cube optimized_transparent");
                optimizedGeometry_Transparent.setQueueBucket(Bucket.Transparent);
                node.attachChild(optimizedGeometry_Transparent);
                updateBlockMaterial();
            }
            
            Mesh optimizedOpaque = BlockChunk_MeshOptimizer.generateOptimizedMesh(this, false);
            Mesh optimizedTransparent = BlockChunk_MeshOptimizer.generateOptimizedMesh(this, true);
            optimizedGeometry_Opaque.setMesh(optimizedOpaque);
            optimizedGeometry_Transparent.setMesh(optimizedTransparent);
            needsMeshUpdate = false;
            if (needsPhysicsUpdate) {
                needsPhysicsUpdate = false;
                // Return true if the physics model changed
                return true;
            }
        }
        return false;
    }
    
    // Set material on chunk mesh
    public void updateBlockMaterial(){
        if(optimizedGeometry_Opaque != null){
            optimizedGeometry_Opaque.setMaterial(settings.getBlockMaterial());
        }
        if(optimizedGeometry_Transparent != null){
            optimizedGeometry_Transparent.setMaterial(settings.getBlockMaterial());
        }
    }

    // Treat block location state and trigger neighbor updates
    // this is mostly for updating lights after placing or removing a block
    // TODO: Revisit once transparent blocks are supported
    private void updateBlockState(Vector3Int location){
        if (terrain != null) {
            updateBlockInformation(location);
            for(int i=0;i<Block.Face.values().length;i++){
                Vector3Int neighborLocation = getNeighborBlockGlobalLocation(location, Block.Face.values()[i]);
                BlockChunkControl chunk = terrain.getChunkByBlockLocation(neighborLocation);
                if(chunk != null){
                    chunk.updateBlockInformation(neighborLocation.subtract(chunk.getBlockLocation()));
                    chunk.markNeedUpdate(true);
                }
            }
        }
    }
    // Push block opacity to sunlight 
    // TODO: This will need to be redone to support block lights (other than sun lights)
    private void updateBlockInformation(Vector3Int localLocation){
        Block block = getBlock(localLocation);
        // If the block is set
        if (block != null) {
            if (sunlightChunk != null) {
                sunlightChunk.setSolidBlock(this.location.getX(), this.location.getY(), this.location.getZ(), localLocation.getX(),localLocation.getY(),localLocation.getZ(), true);
            }
        }
        // else block is cleared
        else {
            if (sunlightChunk != null) {
                sunlightChunk.setOpenBlock(this.location.getX(), this.location.getY(), this.location.getZ(), localLocation.getX(),localLocation.getY(),localLocation.getZ(), true);
            }
        }
    }

    // Return true if block is exposed to the sky
    // sky means no opaque blocks in any loaded chunk above the block.
    public boolean isBlockOnSurface(Vector3Int blockLocation){
        if (sunlightChunk != null) {
            return sunlightChunk.isBlockAtSurface(this.location.getY(), blockLocation);
        } else {
            return false;
        }
    }
    
    // Return true if block is transparent or empty and exposed to the sky
    // sky means no opaque blocks in any loaded chunk above the block.
    public boolean isBlockAboveSurface(Vector3Int blockLocation) {
        if (sunlightChunk != null) {
            return sunlightChunk.isBlockAtSurface(this.location.getY(), blockLocation);
        } else {
            return false;
        }
    }

    // Terrain accessor
    public BlockTerrainControl getTerrain(){
        return terrain;
    }

    // Location accessor
    public Vector3Int getLocation(){
        return location;
    }

    // Block location accessor
    public Vector3Int getBlockLocation(){
        return blockLocation;
    }

    // Node Accessor
    public Node getNode(){
        return node;
    }

    // Mesh accessor
    public Geometry getOptimizedGeometry_Opaque(){
        return optimizedGeometry_Opaque;
    }

    // Mesh accessor
    public Geometry getOptimizedGeometry_Transparent(){
        return optimizedGeometry_Transparent;
    }

    // Write entire block to a stream
    @Override
    public void write(BitOutputStream outputStream){
        for(int x=0;x<blockTypes.length;x++){
            for(int y=0;y<blockTypes[0].length;y++){
                for(int z=0;z<blockTypes[0][0].length;z++){
                    outputStream.writeBits(blockTypes[x][y][z], 8);
                }
            }
        }
    }

    // Write horizontal slice of block to a stream
    public void write(int sliceIndex, BitOutputStream outputStream){
        for(int x=0;x<blockTypes.length;x++){
            for(int z=0;z<blockTypes[0][0].length;z++){
                outputStream.writeBits(blockTypes[x][sliceIndex][z], 8);
            }
        }
    }
    
    // Read entire block from a stream
    @Override
    public void read(BitInputStream inputStream) throws IOException{
        for(int x=0;x<blockTypes.length;x++){
            for(int y=0;y<blockTypes[0].length;y++){
                for(int z=0;z<blockTypes[0][0].length;z++){
                    blockTypes[x][y][z] = (byte) inputStream.readBits(8);
                }
            }
        }
        Vector3Int tmpLocation = new Vector3Int();
        //HashMap<String, LightQueueElement> lightsToAdd = new HashMap<String, LightQueueElement> ();
        //HashMap<String, LightQueueElement> lightsToRemove = new HashMap<String, LightQueueElement> ();
        for(int x=0;x<blockTypes.length;x++){
            for(int y=0;y<blockTypes[0].length;y++){
                for(int z=0;z<blockTypes[0][0].length;z++){
                    tmpLocation.set(x, y, z);
                    updateBlockInformation(tmpLocation);
                }
            }
        }
        if (terrain != null) {
            //terrain.removeLightSource(lightsToRemove);
            //terrain.addLightSource(lightsToAdd);
        }
        this.markNeedUpdate(true);
    }
    
    // Read one horizontal slice from input stream
    public void read(int slice, BitInputStream inputStream) throws IOException{
        for(int x=0;x<blockTypes.length;x++){
            for(int z=0;z<blockTypes[0][0].length;z++){
                blockTypes[x][slice][z] = (byte) inputStream.readBits(8);
            }
        }
        Vector3Int tmpLocation = new Vector3Int();        
        for(int x=0;x<blockTypes.length;x++){
            for(int z=0;z<blockTypes[0][0].length;z++){
                tmpLocation.set(x, slice, z);
                // TODO: updateBlockInformation also updates neighboring blocks
                // this could be much more efficent.
                updateBlockInformation(tmpLocation);
            }
        }
        this.markNeedUpdate(true);
    }
    
    // Processes pending light updates
    public void updateLights() {
        // Hypothetically the order you update lights should not matter, but this needs more experimenting/testing.
        terrain.removeLightSource(lightsToRemove);
        lightsToRemove = new HashMap<Vector3Int, LightQueueElement> ();

        terrain.addLightSource(lightsToAdd);
        lightsToAdd = new HashMap<Vector3Int, LightQueueElement> ();        
    }
    
    // Used by terrain to change the actual light state
    public boolean addLightSource(Vector3Int localBlockLocation, byte brightness) {
        if (brightness == 0 || sunlightSources[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] < brightness) {
            sunlightSources[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = brightness;
            if (brightness == 0) {
                sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = 0;
            }
            return true;
        }
        return false;
    }

    // Used by terrain to allow light source to propigate to neighboring blocks
    public boolean propigateLight(Vector3Int localBlockLocation, byte brightness) {
        if (brightness < 0) {
            return false;
        }
        if (getBlock(localBlockLocation) != null) {
            sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = 0;
            markNeedUpdate(false);
            return false;
        }
        byte oldLightLevel = sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()];
        if (oldLightLevel < brightness) {
            sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = brightness;
            this.markNeedUpdate(false);
            return true;
        }
        return false;
    }

    // Suck in the light from a removed light source.
    public boolean propigateDark(Vector3Int localBlockLocation, byte oldLight) {
        if (oldLight < 0) {
            return false;
        }
        if (sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] == 0) {
            return false;
        }
        if (sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] <= oldLight) {
            sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = 0;
            this.markNeedUpdate(false);
            return true;
        }
        return false;
    }

    // Accessor to get current light source at block location
    public byte getLightSourceAt(Vector3Int localBlockLocation) {
        if (sunlightChunk.isBlockAboveSurface(this.location.getY(), localBlockLocation)) {
            return sunlight;
        }
        return sunlightSources[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()];
    }    

    // Accessor to get current light LEVEL at block location (source may be 0,
    // but light level could be raised by other lights around
    public byte getLightAt(Vector3Int localBlockLocation) {
        if (sunlightLevels != null) {
            return sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()];
        } else {
            System.out.println("Attempted to get light at " + localBlockLocation);
            return 0;
        }
    }

    // TODO: Rename to finishInitialize
    // TODO: Shouldn't chunk already know its chunkLocation?
    // For chunks that are attached to the terrain after they are loaded (compared to attached empty then filled)
    // finish attaching the chunk to the terrain
    public void setLocation(BlockTerrainControl terrain, Vector3Int chunkLocation) {
        this.terrain = terrain;
        this.settings = terrain.getSettings();
        location.set(chunkLocation.getX(), chunkLocation.getY(), chunkLocation.getZ());
        int cX = settings.getChunkSizeX();
        int cY = settings.getChunkSizeY();
        int cZ = settings.getChunkSizeZ();
        if (cY > 256) {
            // to support more than 256, blocks on surface (and probobly other things) needs to be larger than byte
            throw new UnsupportedOperationException("Chunks taller than 256 are not supported");
        }
        blockLocation.set(location.mult(cX, cY, cZ));
        node.setLocalTranslation(new Vector3f(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ()).mult(settings.getBlockSize()));
    }

    // Called on new chunks to let sunlight know of new opaque blocks
    // TODO: This might be made more efficent by only updating the top opaque block?  Or would that break updateSunlight?
    void setSunlight(SunlightChunk sunlight) {
        this.sunlightChunk = sunlight;
        this.sunlightChunk.validateChunk(this.location);
        if (sunlightChunk.isChunkUnderground(this)) {
            this.addSunlights();
            return;
        }
        for (int iX = 0; iX < blockTypes.length; ++iX) {
            for (int iZ = 0; iZ < blockTypes[0][0].length; ++iZ) {
                for (int iY = blockTypes[0].length - 1; iY >= 0; --iY) {
                    if (blockTypes[iX][iY][iZ] != 0) {
                        sunlightChunk.setSolidBlock(this.location.getX(), this.location.getY(), this.location.getZ(), iX, iY, iZ, false);
                        break;
                    }
                }
            }
        }
        this.addSunlights();
    }
    
    // Translate chane in sunlight into pending block light change
    void updateSunlight(int blockX, int blockY, int blockZ) {
        if (settings.getLightsEnabled()) {
            sunlightSources[blockX][blockY][blockZ] = 0;
            //sunlightLevels[blockX][blockY][blockZ] = 0;
            Vector3Int blockLoc = new Vector3Int(blockX, blockY, blockZ);
            if (sunlightChunk.isBlockAboveSurface(this.location.getY(), blockLoc)) {
                lightsToAdd.put(blockLoc, new LightQueueElement(blockLoc, this, sunlight));
            } else {
                lightsToRemove.put(blockLoc, new LightQueueElement(blockLoc, this, sunlight));
            }
            this.markNeedUpdate(false);
        }
    }
    
    // Update the entire chunk as if it is all brand new blocks and air.
    void addSunlights() {
        // Reset light state
        if (settings.getLightsEnabled()) {
            lightsToRemove = new HashMap<Vector3Int, LightQueueElement> ();
            lightsToAdd = new HashMap<Vector3Int, LightQueueElement> ();
            int cX = settings.getChunkSizeX();
            int cY = settings.getChunkSizeY();
            int cZ = settings.getChunkSizeZ();
        
            sunlightSources = new byte[cX][cY][cZ];
            sunlightLevels = new byte[cX][cY][cZ];

            if (sunlightChunk != null) {
                if (!sunlightChunk.isChunkUnderground(this)) {
                    for (int iX = 0; iX < settings.getChunkSizeX(); ++iX) {
                        for (int iZ = 0; iZ < settings.getChunkSizeZ(); ++iZ) {
                            for (int iY = settings.getChunkSizeY() -1 ; iY >= 0; --iY) {
                                updateSunlight(iX, iY, iZ);
                            }
                        }
                    }
                }
            }
        }
    }

}
