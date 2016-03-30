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
import java.util.Calendar;
import java.util.HashMap;


/**
 *
 * @author Carl
 */
public class BlockChunkControl extends AbstractControl implements BitSerializable{
    private CubesSettings settings;
    private SunlightChunk sunlightChunk; // 2d object tracking sunlight across multiple vertical chunks
    private byte sunlight = 0; // The strength of sunlight
    private BlockTerrainControl terrain;
    private Vector3Int location = new Vector3Int();
    private Vector3Int blockLocation = new Vector3Int();
    private byte[][][] blockTypes;
    private byte[][][] sunlightSources;
    private byte[][][] sunlightLevels;
   //private byte[][] blocks_IsOnSurface;
    private Node node = new Node("Cube Chunk");
    private Geometry optimizedGeometry_Opaque;
    private Geometry optimizedGeometry_Transparent;
    private boolean needsMeshUpdate = false;
    private boolean needsPhysicsUpdate = false;
    public BlockChunkControl(BlockTerrainControl terrain, Vector3Int location) {
        this(terrain, location.getX(), location.getY(), location.getZ());
    }
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
        //blocks_IsOnSurface = new byte[cX][cZ];
        sunlight = settings.getSunlightLevel();
        /*if (settings.getLightsEnabled()) {
            sunlightSources = new byte[cX][cY][cZ];
            sunlightLevels = new byte[cX][cY][cZ];
        }
        if (settings.getLightsEnabled()) {
            for( int iX = 0; iX < cX; ++iX) {
                for (int iZ = 0; iZ < cZ; ++iZ) {
                //blocks_IsOnSurface[iX][iZ] = 0;
                    for (int iY = 0; iY < cY; ++iY) {
                        sunlightSources[iX][iY][iZ] = 0;
                        sunlightLevels[iX][iY][iZ] = 0;
                    }
                }
            }
        }*/

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
    
    public Block getNeighborBlock_Local(Vector3Int location, Block.Face face){
        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, face);
        return getBlock(neighborLocation);
    }
    
    public Block getNeighborBlock_Global(Vector3Int location, Block.Face face){
        if (terrain == null) {
            return null;
        }
        return terrain.getBlock(getNeighborBlockGlobalLocation(location, face));
    }
    
    public Vector3Int getNeighborBlockGlobalLocation(Vector3Int location, Block.Face face){
        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, face);
        neighborLocation.addLocal(blockLocation);
        return neighborLocation;
    }
    public Vector3Int getBlockGlobalLocation(Vector3Int location){
        Vector3Int neighborLocation = location.clone();
        neighborLocation.addLocal(blockLocation);
        return neighborLocation;
    }
    
    public Block getBlock(Vector3Int location){
        if(isValidBlockLocation(location)){
            byte blockType = blockTypes[location.getX()][location.getY()][location.getZ()];
            return BlockManager.getBlock(blockType);
        }
        return null;
    }
    
    public void setBlock(Vector3Int location, Block block){
        if(isValidBlockLocation(location)){
            byte blockType = BlockManager.getType(block);
            blockTypes[location.getX()][location.getY()][location.getZ()] = blockType;
            lightsToRemove.put(location, new LightQueueElement(location, this));
            updateBlockState(location);
            this.markNeedUpdate(true);
        }
    }
    
    public void removeBlock(Vector3Int location){
        if(isValidBlockLocation(location)){
            blockTypes[location.getX()][location.getY()][location.getZ()] = 0;
            lightsToRemove.put(location, new LightQueueElement(location, this));
            updateBlockState(location);
            this.markNeedUpdate(true);
        }
    }
    
    public void markNeedUpdate(boolean needPhysicsUpdate) {
        needsMeshUpdate = true;
        this.needsPhysicsUpdate = this.needsPhysicsUpdate || needPhysicsUpdate;
        if (this.terrain != null) {
            this.terrain.addChunkToNeedsUpdateList(this);
        } 
    }
    
    private boolean isValidBlockLocation(Vector3Int location){
        return Util.isValidIndex(blockTypes, location);
    }
    
    public boolean updateSpatial(){
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
            //long startTime = Calendar.getInstance().getTimeInMillis();
            //long endTime;
            
            Mesh optimizedOpaque = BlockChunk_MeshOptimizer.generateOptimizedMesh(this, false);
            Mesh optimizedTransparent = BlockChunk_MeshOptimizer.generateOptimizedMesh(this, true);
            //endTime = Calendar.getInstance().getTimeInMillis();
            //System.out.println(" generating optimized mesh took " + (endTime - startTime));
            //startTime = endTime;
            optimizedGeometry_Opaque.setMesh(optimizedOpaque);
            optimizedGeometry_Transparent.setMesh(optimizedTransparent);
            //endTime = Calendar.getInstance().getTimeInMillis();
            //System.out.println(" setting optimized mesh took " + (endTime - startTime));
            needsMeshUpdate = false;
            if (needsPhysicsUpdate) {
                needsPhysicsUpdate = false;
                return true;
            }
        }
        return false;
    }
    
    public void updateBlockMaterial(){
        if(optimizedGeometry_Opaque != null){
            optimizedGeometry_Opaque.setMaterial(settings.getBlockMaterial());
        }
        if(optimizedGeometry_Transparent != null){
            optimizedGeometry_Transparent.setMaterial(settings.getBlockMaterial());
        }
    }
    private void updateBlockState(Vector3Int location){
        //HashMap<String, LightQueueElement> lightsToAdd = new HashMap<String, LightQueueElement> ();
        //HashMap<String, LightQueueElement> lightsToRemove = new HashMap<String, LightQueueElement> ();

        if (terrain != null) {
            updateBlockInformation(location);
            for(int i=0;i<Block.Face.values().length;i++){
                Vector3Int neighborLocation = getNeighborBlockGlobalLocation(location, Block.Face.values()[i]);
                BlockChunkControl chunk = terrain.getChunkByBlockLocation(neighborLocation);
                if(chunk != null){
                    //System.out.println("Updating face " + i);
                    chunk.updateBlockInformation(neighborLocation.subtract(chunk.getBlockLocation()));
                    chunk.markNeedUpdate(true);
                }
            }
            //terrain.removeLightSource(lightsToRemove);
            //terrain.addLightSource(lightsToAdd);
        }
    }
    private void updateBlockInformation(Vector3Int localLocation){
    //    Block neighborBlock_Top = terrain.getBlock(getNeighborBlockGlobalLocation(localLocation, Block.Face.Top));
    //    if blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()] = (neighborBlock_Top == null);
        Block block = getBlock(localLocation);
        // If the block is set
        if (block != null) {
            if (settings.getLightsEnabled() && terrain != null) {
                // Todo: this will be more relevant when we implement torches
                //if (sunlightLevels[localLocation.getX()][localLocation.getY()][localLocation.getZ()] != 0) {
                //    lightsToRemove.put(BlockTerrainControl.keyify(terrain.getGlobalBlockLocation(localLocation, this)), new LightQueueElement(localLocation, this));
                //}
            }                    
            if (sunlightChunk != null) {
                sunlightChunk.setSolidBlock(this.location.getX(), this.location.getY(), this.location.getZ(), localLocation.getX(),localLocation.getY(),localLocation.getZ(), true);
            }
            // if the block is higher up than the surface block
            // TODO support blocks that ARE light sources.
            /*if (localLocation.getY() > blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()]) {
 
                // then it is the new surface block
                if (settings.getLightsEnabled() && terrain != null) {
                    int searchY = localLocation.getY() + 1;
                    // TODO:
                    // Change "sun" light sources to be 'is above' checks.
                    // and make a 2d array of top blocks outside of chunks.
                    for (; searchY < settings.getChunkSizeY(); ++searchY) {
                        Vector3Int searchLoc = new Vector3Int(localLocation.getX(), searchY, localLocation.getZ());
                        if (sunlightSources[localLocation.getX()][searchY][localLocation.getZ()] > 0) {
                            break;
                        }
                        lightsToAdd.put(terrain.keyify(terrain.getGlobalBlockLocation(searchLoc, this)), new LightQueueElement(searchLoc, this, sunlight));
                    }
                    searchY = localLocation.getY();
                    for (; searchY > blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()]; --searchY) {
                        Vector3Int searchLoc = new Vector3Int(localLocation.getX(), searchY, localLocation.getZ());
                        lightsToRemove.put(terrain.keyify(terrain.getGlobalBlockLocation(searchLoc, this)), new LightQueueElement(searchLoc, this));
                    }
                }
                blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()] = (byte)localLocation.getY();
            }*/
        }
        // else block is cleared
        else {
            if (sunlightChunk != null) {
                sunlightChunk.setOpenBlock(this.location.getX(), this.location.getY(), this.location.getZ(), localLocation.getX(),localLocation.getY(),localLocation.getZ(), true);
            }
            
            // if the block used to be the surface block
           /* if (localLocation.getY() == blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()]) {
                // then find the next block down to be surface
                int searchY = localLocation.getY();
                for (; searchY > 0; --searchY) {
                    Vector3Int searchLoc = new Vector3Int(localLocation.getX(), searchY, localLocation.getZ());
                    block = getBlock(searchLoc);
                    if (block != null) {
                        blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()] = (byte)searchY;
                        break;
                    } else {
                        if (settings.getLightsEnabled() && terrain != null) {
                            lightsToAdd.put(terrain.keyify(terrain.getGlobalBlockLocation(searchLoc, this)), new LightQueueElement(searchLoc, this, sunlight));
                        }
                    }
                }
                if (0 == searchY) {
                    blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()] = 0;    
                }    
            } else if (localLocation.getY() >= blocks_IsOnSurface[localLocation.getX()][localLocation.getZ()]) {
                if (settings.getLightsEnabled() && terrain != null) {
                    lightsToAdd.put(terrain.keyify(terrain.getGlobalBlockLocation(localLocation, this)), new LightQueueElement(localLocation, this, sunlight));
                }
            } else {
                if (settings.getLightsEnabled() && terrain != null) {
                    byte brightestLight = 0;
                    if (sunlightLevels[localLocation.getX()][localLocation.getY()][localLocation.getZ()] == 0) {
                        for(int i=0;i<Block.Face.values().length;i++){
                            Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(localLocation, Block.Face.values()[i]);
                            neighborLocation = terrain.getGlobalBlockLocation(neighborLocation, this);
                            byte neighborLight = terrain.getLightLevelOfBlock(neighborLocation);
                            brightestLight = (byte)Math.max((int)neighborLight, (int)brightestLight);
                        }
                        if ( brightestLight > 1 ) {
                            lightsToAdd.put(terrain.keyify(terrain.getGlobalBlockLocation(localLocation, this)), new LightQueueElement(localLocation, this, (byte)(brightestLight - 1), false));
                        }
                    }
                }
            }*/
        }
    }

    public boolean isBlockOnSurface(Vector3Int blockLocation){
        if (sunlightChunk != null) {
            return sunlightChunk.isBlockAtSurface(this.location.getY(), blockLocation);
        } else {
            return false;
        }
    }
    
    public boolean isBlockAboveSurface(Vector3Int blockLocation) {
        if (sunlightChunk != null) {
            return sunlightChunk.isBlockAtSurface(this.location.getY(), blockLocation);
        } else {
            return false;
        }
    }

    public BlockTerrainControl getTerrain(){
        return terrain;
    }

    public Vector3Int getLocation(){
        return location;
    }

    public Vector3Int getBlockLocation(){
        return blockLocation;
    }

    public Node getNode(){
        return node;
    }

    public Geometry getOptimizedGeometry_Opaque(){
        return optimizedGeometry_Opaque;
    }

    public Geometry getOptimizedGeometry_Transparent(){
        return optimizedGeometry_Transparent;
    }

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

    public void write(int sliceIndex, BitOutputStream outputStream){
        for(int x=0;x<blockTypes.length;x++){
            for(int z=0;z<blockTypes[0][0].length;z++){
                outputStream.writeBits(blockTypes[x][sliceIndex][z], 8);
            }
        }
    }
    
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
    private HashMap<Vector3Int, LightQueueElement> lightsToAdd = new HashMap<Vector3Int, LightQueueElement> ();
    private HashMap<Vector3Int, LightQueueElement> lightsToRemove = new HashMap<Vector3Int, LightQueueElement> ();
    public void read(int slice, BitInputStream inputStream) throws IOException{
        for(int x=0;x<blockTypes.length;x++){
            for(int z=0;z<blockTypes[0][0].length;z++){
                blockTypes[x][slice][z] = (byte) inputStream.readBits(8);
            }
        }
        Vector3Int tmpLocation = new Vector3Int();
        //long startTime = Calendar.getInstance().getTimeInMillis();
        //long endTime;
        
        for(int x=0;x<blockTypes.length;x++){
            for(int z=0;z<blockTypes[0][0].length;z++){
                tmpLocation.set(x, slice, z);
                updateBlockInformation(tmpLocation);
            }
        }
        //endTime = Calendar.getInstance().getTimeInMillis();
        //if (endTime - startTime > 2) {
        //    System.err.println("update block info took " + (endTime - startTime) + "ms");
        //}
        this.markNeedUpdate(true);
    }
    
    public void updateLights() {
        //long startTime = Calendar.getInstance().getTimeInMillis();
        //long endTime;
        //startTime = Calendar.getInstance().getTimeInMillis();
        terrain.removeLightSource(lightsToRemove);
        lightsToRemove = new HashMap<Vector3Int, LightQueueElement> ();
        //endTime = Calendar.getInstance().getTimeInMillis();
        //if (endTime - startTime > 2) {
        //    System.err.println("removing lights took " + (endTime - startTime) + "ms");
        //}
        //startTime = Calendar.getInstance().getTimeInMillis();
        terrain.addLightSource(lightsToAdd);
        lightsToAdd = new HashMap<Vector3Int, LightQueueElement> ();
        //endTime = Calendar.getInstance().getTimeInMillis();
        //if (endTime - startTime > 2) {
        //    System.err.println("putting lights took " + (endTime - startTime) + "ms");
        //}
    }
    
    private Vector3Int getNeededBlockChunks(Vector3Int blocksCount){
        int chunksCountX = (int) Math.ceil(((float) blocksCount.getX()) / settings.getChunkSizeX());
        int chunksCountY = (int) Math.ceil(((float) blocksCount.getY()) / settings.getChunkSizeY());
        int chunksCountZ = (int) Math.ceil(((float) blocksCount.getZ()) / settings.getChunkSizeZ());
        return new Vector3Int(chunksCountX, chunksCountY, chunksCountZ);
    }

    boolean addLightSource(Vector3Int localBlockLocation, byte brightness) {
        if (brightness == 0 || sunlightSources[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] < brightness) {
            sunlightSources[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = brightness;
            if (brightness == 0) {
                sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = 0;
            }
            return true;
        }
        return false;
    }

    boolean propigateLight(Vector3Int localBlockLocation, byte brightness) {
        if (brightness < 0) {
            return false;
        }
        if (getBlock(localBlockLocation) != null) {
            sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = 0;
            markNeedUpdate(false);
            return false;
        }
        if (localBlockLocation.getX() < 0 || localBlockLocation.getX() > 15) {
            localBlockLocation.setX(0);
        }
        if (localBlockLocation.getY() < 0 || localBlockLocation.getY() > 15) {
            localBlockLocation.setY(0);
        }
        if (localBlockLocation.getZ() < 0 || localBlockLocation.getZ() > 15) {
            localBlockLocation.setZ(0);
        }
        byte oldLightLevel = sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()];
        if (oldLightLevel < brightness) {
            sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()] = brightness;
            this.markNeedUpdate(false);
            return true;
        }
        return false;
    }

    boolean propigateDark(Vector3Int localBlockLocation, byte oldLight) {
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

    byte getLightSourceAt(Vector3Int localBlockLocation) {
        if (localBlockLocation.getX() > 15 || localBlockLocation.getZ() > 15) {
            System.out.println("out of bounds");
            return 0;
        }
        if (sunlightChunk.isBlockAboveSurface(this.location.getY(), localBlockLocation)) {
            return sunlight;
        }
        return sunlightSources[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()];
    }    

    byte getLightAt(Vector3Int localBlockLocation) {
        if (sunlightLevels != null) {
            return sunlightLevels[localBlockLocation.getX()][localBlockLocation.getY()][localBlockLocation.getZ()];
        } else {
            System.out.println("Attempted to get light at " + localBlockLocation);
            return 0;
        }
    }

    void setLocation(BlockTerrainControl terrain, Vector3Int chunkLocation) {
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

    void setSunlight(SunlightChunk sunlight) {
        this.sunlightChunk = sunlight;
        this.sunlightChunk.validateChunk(this.location);
        if (sunlightChunk.isChunkUnderground(this)) {
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
    }
    void updateSunlight(int blockX, int blockY, int blockZ) {
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
        //this.updateLights();
    }

}
