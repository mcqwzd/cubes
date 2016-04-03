/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import java.io.IOException;
import java.util.ArrayList;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.cubes.network.*;
import com.jme3.math.Vector3f;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Carl
 */
public class BlockTerrainControl extends AbstractControl implements BitSerializable{

    // TODO name this better.  Name is from when this was used to puth vect3's into data structures
    // creates x.y.z string out of vector
    public static String keyify(Vector3Int key) {
        return "" + key.getX() + "." + key.getY() + '.' + key.getZ();
    }
    
    // parses x.y.z string into vector3Int
    public static Vector3Int vectorify(String key) {
        String split[] = key.split("\\.");
        if (split.length != 3) {
            return null;
        }
        return new Vector3Int(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
    }
    
    // Constructor
    public BlockTerrainControl(CubesSettings settings){
        this.settings = settings;
        chunks = new HashMap<Vector3Int, BlockChunkControl>();
        chunksThatNeedUpdate = new ArrayList<BlockChunkControl> ();
    }
    
    // Reference to settings
    private CubesSettings settings;
    
    // Lock for multi threading support
    // TODO: Read/Write lock this sucker
    // TODO: find a thread safe way to subdivide some write operations like adding chunks vs updating chunks
    private Lock chunkAccessMutex = new ReentrantLock(true);    
    private HashMap<Vector3Int, BlockChunkControl> chunks;
    private List<BlockChunkControl> chunksThatNeedUpdate;
    private HashMap<Vector3Int, SunlightChunk> sunlight = new HashMap<Vector3Int, SunlightChunk>();
    private ArrayList<BlockChunkListener> chunkListeners = new ArrayList<BlockChunkListener>();
    
    // If there isn't a chunk at a given location, create a new blank one
    private void initializeChunk(Vector3Int location) {
        chunkAccessMutex.lock();
        try {
            if (!chunks.containsKey(location)) {
                BlockChunkControl chunk = new BlockChunkControl(this, location.getX(), location.getY(), location.getZ());
                chunks.put(location, chunk);
                Vector3Int sunlightKey = new Vector3Int(location.getX(), 0, location.getZ());
                if (!sunlight.containsKey(sunlightKey)) {
                    SunlightChunk sl = new SunlightChunk(this, settings, location);
                    sunlight.put(sunlightKey, sl);
                }
                chunk.setSunlight(sunlight.get(sunlightKey));
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }

    // WARNING: Slow (see addChunkToSpatial)
    // Reinitialize spatials of every chnk
    @Override
    public void setSpatial(Spatial spatial){
        Spatial oldSpatial = this.spatial;
        super.setSpatial(spatial);
        chunkAccessMutex.lock();
        try {
            for (Vector3Int chunk :  chunks.keySet()) {
                if(spatial == null){
                    oldSpatial.removeControl(chunks.get(chunk));
                }
                else{
                    spatial.addControl(chunks.get(chunk));
                }
            }
        } finally {
            chunkAccessMutex.unlock();
        }

    }
    
    // Use instaed of setSpatial where applicable
    // Add a brand new chunk to the spatial
    // Don't call on chunks already added.
    private void addChunkToSpatial(BlockChunkControl chunk) {
        if (this.spatial != null) {
            this.spatial.addControl(chunk);
        }
    }

    @Override
    protected void controlUpdate(float lastTimePerFrame){
        updateSpatial();
    }

    @Override
    protected void controlRender(RenderManager renderManager, ViewPort viewPort){
        
    }

    @Override
    public Control cloneForSpatial(Spatial spatial){
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    // Get block via global coordinates
    public Block getBlock(int x, int y, int z){
        return getBlock(new Vector3Int(x, y, z));
    }
    
    // Get block via global coordinates
    // TODO: this is using localBlockState, and that seems overkill.  Could be faster?
    public Block getBlock(Vector3Int globalLocation){
        BlockTerrain_LocalBlockState localBlockState = getLocalBlockState(globalLocation);
        if(localBlockState != null){
            return localBlockState.getBlock();
        }
        return null;
    }

    // Helper method to set a cubic area to a block
    public void setBlockArea(Vector3Int location, Vector3Int size, Block block){
        Vector3Int tmpLocation = new Vector3Int();
        for(int x=0;x<size.getX();x++){
            for(int y=0;y<size.getY();y++){
                for(int z=0;z<size.getZ();z++){
                    tmpLocation.set(location.getX() + x, location.getY() + y, location.getZ() + z);
                    setBlock(tmpLocation, block);
                }
            }
        }
    }

    // set a block using global cordinates
    public void setBlock(int x, int y, int z, Block block){
        setBlock(new Vector3Int(x, y, z), block);
    }
    
    // set a block using global cordinates
    public void setBlock(Vector3Int location, Block block){
        chunkAccessMutex.lock();
        try {
            this.initializeChunk(this.getChunkLocation(location));
            BlockTerrain_LocalBlockState localBlockState = getLocalBlockState(location);
            if(localBlockState != null){
                localBlockState.setBlock(block);
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }
    
    // Helper method to clear a cubic area to a block
    public void removeBlockArea(Vector3Int location, Vector3Int size){
        Vector3Int tmpLocation = new Vector3Int();
        for(int x=0;x<size.getX();x++){
            for(int y=0;y<size.getY();y++){
                for(int z=0;z<size.getZ();z++){
                    tmpLocation.set(location.getX() + x, location.getY() + y, location.getZ() + z);
                    removeBlock(tmpLocation);
                }
            }
        }
    }

    // Set a block as air in global cordinate
    public void removeBlock(int x, int y, int z){
        removeBlock(new Vector3Int(x, y, z));
    }
    
    // Set a block as air in global cordinate
    public void removeBlock(Vector3Int location){
        BlockTerrain_LocalBlockState localBlockState = getLocalBlockState(location);
        if(localBlockState != null){
            localBlockState.removeBlock();
        }
    }

    // TODO: Is blockState actually needed?
    private BlockTerrain_LocalBlockState getLocalBlockState(Vector3Int blockLocation){
        BlockChunkControl chunk = getChunkByBlockLocation(blockLocation);
        if(chunk != null){
            Vector3Int localBlockLocation = getLocalBlockLocation(blockLocation, chunk);
            return new BlockTerrain_LocalBlockState(chunk, localBlockLocation);
        }
        return null;
    }
    
    // INTERNAL USE ONLY
    // Removes a light and propigates darkness
    // TODO: Batch process these
    public void removeLightSource(HashMap<Vector3Int, LightQueueElement> lightsToRemove) {
        if (lightsToRemove.size() == 0) {
            return;
        }
        for (LightQueueElement element : lightsToRemove.values()) {
            removeLightSource(getGlobalBlockLocation(element.getLocation(), element.getChunk()));
        }
     }

    // INTERNAL USE ONLY
    // Add a batch of light sources
    public void addLightSource(HashMap<Vector3Int, LightQueueElement> lightsToAdd) {
        if (lightsToAdd.size() == 0) {
            return;
        }

        if (!getSettings().getLightsEnabled()) {
            return;
        }
        class propigateElement {
            public Vector3Int location;
            public byte brightness;
            public propigateElement(Vector3Int location, byte brightness) {
                this.location = location;
                this.brightness = brightness;
            }
        }
        
        Queue<propigateElement> locationsToPropigateTo = new LinkedList<propigateElement>();
        Queue<propigateElement> next = new LinkedList<propigateElement>();

        for (LightQueueElement element : lightsToAdd.values()) {
            byte brightness = element.getLevel();
            boolean placeLight = element.getPlaceLight();

            if (brightness <= 0) {
                continue;
            }

            BlockChunkControl chunk = element.getChunk();
            if (chunk == null) {
                continue;
            }
            Vector3Int localBlockLocation = element.getLocation();
            if (placeLight) {
                if (!chunk.addLightSource(localBlockLocation, brightness)) {
                    continue;
                }
            }
            // if the light source is brighter than the light currently at this spot
            if (chunk.propigateLight(localBlockLocation, brightness) || !placeLight) {
                if (brightness > 1) {
                    Vector3Int globalLocation = getGlobalBlockLocation(element.getLocation(), chunk);
                    for(int face = 0; face < Block.Face.values().length; face++){
                        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(globalLocation, Block.Face.values()[face]);
                        locationsToPropigateTo.add(new propigateElement(neighborLocation, (byte)(brightness-1)));
                    }
                }
            }
        }

         while (locationsToPropigateTo.size() > 0) {
            propigateElement p = locationsToPropigateTo.remove();
            Vector3Int location = p.location;
            byte brightness = p.brightness;
            BlockChunkControl chunk;
            chunk = getChunkByBlockLocation(location);
            Vector3Int localBlockLocation;
            if (chunk != null) {
                localBlockLocation = getLocalBlockLocation(location, chunk);
                if (chunk.propigateLight(localBlockLocation, brightness)) {
                    if (brightness > 1) {
                        for(int face = 0; face < Block.Face.values().length; face++){
                            Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, Block.Face.values()[face]);
                            locationsToPropigateTo.add(new propigateElement(neighborLocation, (byte)(brightness-1)));
                        }
                    }
                }
            }
            if (locationsToPropigateTo.size() == 0) {
                locationsToPropigateTo = next;
                next = new LinkedList<propigateElement>();
            }
        }
    }


    // TODO: Optimize this so it doesn't have to propigate as far
    // if it sees an incline in light level, it could stop propigatind darkness there
    // and propigate that light level instead.
    //public static boolean debugLogs = false;
    public void removeLightSource(Vector3Int globalLocation) {
        if (!getSettings().getLightsEnabled()) {
            return;
        }
        BlockChunkControl chunk = getChunkByBlockLocation(globalLocation);
        if (chunk == null) {
            return;
        }
        Vector3Int localBlockLocation = getLocalBlockLocation(globalLocation, chunk);
        byte oldLight = (byte)Math.max(chunk.getLightSourceAt(localBlockLocation), chunk.getLightAt(localBlockLocation));
        chunk.addLightSource(localBlockLocation, (byte)0);

        HashMap<Vector3Int, LightQueueElement> lightsToReplace = new HashMap<Vector3Int, LightQueueElement>();
        chunk.propigateDark(localBlockLocation, oldLight);//) {
        Queue<Vector3Int> locationsToPropigateTo = new LinkedList<Vector3Int>();
        Queue<Vector3Int> next = new LinkedList<Vector3Int>();
        if (oldLight >= 0) {
            for(int face = 0; face < Block.Face.values().length; face++){
                Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(globalLocation, Block.Face.values()[face]);
                locationsToPropigateTo.add(neighborLocation);
            }
        }
        oldLight--;
        while (locationsToPropigateTo.size() > 0 && oldLight >= -1) {
            Vector3Int location = locationsToPropigateTo.remove();
            chunk = getChunkByBlockLocation(location);
            if (chunk != null) {
                localBlockLocation = getLocalBlockLocation(location, chunk);
                if (chunk.getLightSourceAt(localBlockLocation) > 0) {
                    lightsToReplace.put(location, new LightQueueElement(localBlockLocation,chunk,chunk.getLightSourceAt(localBlockLocation),false));
                } else if (chunk.propigateDark(localBlockLocation, oldLight)) {
                    for(int face = 0; face < Block.Face.values().length; face++){
                        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, Block.Face.values()[face]);
                        next.add(neighborLocation);
                    }
                } else {
                    byte light = chunk.getLightAt(localBlockLocation);
                    if(light > 0) {
                        lightsToReplace.put(location, new LightQueueElement(localBlockLocation,chunk,light,false));
                    }
                }
                if (locationsToPropigateTo.size() == 0) {
                    locationsToPropigateTo = next;
                    next = new LinkedList<Vector3Int>();
                    oldLight--;
                }
            }
        }
        addLightSource(lightsToReplace);
    }
    
    // using global block location, return the chunk it is in
    public BlockChunkControl getChunkByBlockLocation(Vector3Int blockLocation){
        Vector3Int chunkLocation = getChunkLocation(blockLocation);
        return getChunkByChunkLocation(chunkLocation);
    }

    // using chunk location, get chunk
    // TODO: move responsability to lock/unlock to caller
    public BlockChunkControl getChunkByChunkLocation(Vector3Int chunkLocation){
        if(isValidChunkLocation(chunkLocation)){
            BlockChunkControl chunk = null;
            chunkAccessMutex.lock();
            try {
                chunk = chunks.get(chunkLocation);
            } finally {
                chunkAccessMutex.unlock();
            }
            return chunk;
        }
        return null;
    }

    // Check if chunk exists.
    public boolean isValidChunkLocation(Vector3Int location){
        boolean returnValue = false;
        chunkAccessMutex.lock();
        try {
            returnValue = chunks.containsKey(location);
        } finally {
            chunkAccessMutex.unlock();
        }
        return returnValue;
    }
    
    // Return true if the provided global location is exposed to sunlight
    public boolean getIsGlobalLocationAboveSurface(Vector3Int blockLocation) {
        boolean returnValue = false;
        chunkAccessMutex.lock();
        try {
            Vector3Int chunkLoc = getChunkLocation(blockLocation);
            BlockChunkControl chunk = chunks.get(chunkLoc);
            if (chunk == null) {
                returnValue = true;
            } else {
                returnValue = chunk.isBlockAboveSurface(getLocalBlockLocation(blockLocation, chunk));
            }
        } finally {
            chunkAccessMutex.unlock();
        }
        return returnValue;
    }
    
    /** Get chunk location from block location */
    // TODO: Move to blockNavigator
    public Vector3Int getChunkLocation(Vector3Int blockLocation){
        Vector3Int chunkLocation = new Vector3Int();
        Vector3Int copy = blockLocation.clone();
        // Negitive coordinates are 1 based.
        // Positive coordinates are 0 based.
        // Because who ever heard of negitive 0?
        if (copy.getX() < 0) {
            copy.setX(copy.getX() + 1);
        }
        if (copy.getY() < 0) {
            copy.setY(copy.getY() + 1);
        }
        if (copy.getZ() < 0) {
            copy.setZ(copy.getZ() + 1);
        }
        int chunkX = (copy.getX() / settings.getChunkSizeX());
        int chunkY = (copy.getY() / settings.getChunkSizeY());
        int chunkZ = (copy.getZ() / settings.getChunkSizeZ());
        if(blockLocation.getX() < 0)
            --chunkX;
        if(blockLocation.getY() < 0)
            --chunkY;
        if(blockLocation.getZ() < 0)
            --chunkZ;
        chunkLocation.set(chunkX, chunkY, chunkZ);
        //if (blockLocation.getX() < 0 || blockLocation.getY() < 0 || blockLocation.getZ() < 0) {
        //    System.out.println("getChunkLocation(" + keyify(blockLocation) + ") is " + keyify(chunkLocation));
        //}
        return chunkLocation;
    }

    // TODO: Move to blockNavigation
    // Get global location of block within chunk
    public static Vector3Int getGlobalBlockLocation(Vector3Int localLocation, BlockChunkControl chunk) {
        Vector3Int globalLocation = new Vector3Int();
        if (localLocation.getZ() < 0) {
            localLocation = localLocation;
        }
        int localX = (localLocation.getX() + chunk.getBlockLocation().getX());
        int localY = (localLocation.getY() + chunk.getBlockLocation().getY());
        int localZ = (localLocation.getZ() + chunk.getBlockLocation().getZ());
        globalLocation.set(localX, localY, localZ);
        return globalLocation;
    }
    
    
    // TODO: Move to blockNavigation
    // get local location of global block relative to given chunk
    public static Vector3Int getLocalBlockLocation(Vector3Int globalBlockLocation, BlockChunkControl chunk){
        Vector3Int localLocation = new Vector3Int();
        int localX = (globalBlockLocation.getX() - chunk.getBlockLocation().getX());
        int localY = (globalBlockLocation.getY() - chunk.getBlockLocation().getY());
        int localZ = (globalBlockLocation.getZ() - chunk.getBlockLocation().getZ());
        localLocation.set(localX, localY, localZ);
        return localLocation;
    }
    
    public boolean updateSpatial(){

        boolean wasUpdateNeeded = false;
        boolean hasLock = false;
        try {
            // This is called by render thread, so if lock can't be obtained within 10ms, then skip and try agian next frame.
            hasLock = chunkAccessMutex.tryLock(10L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            System.err.println("updateSpatial failed to tryLock");
        }
        if (hasLock) {
            try {
                int chunksUpdated = 0;
                long startTime = Calendar.getInstance().getTimeInMillis();
                while (!chunksThatNeedUpdate.isEmpty() && (Calendar.getInstance().getTimeInMillis() - startTime) < 16 ) {
                    BlockChunkControl chunk = chunksThatNeedUpdate.get(0);
                    chunksThatNeedUpdate.remove(0);
                    chunk.updateLights();
                    wasUpdateNeeded = chunk.updateSpatial();
                    if (wasUpdateNeeded) {
                        for(int i=0;i<chunkListeners.size();i++){
                            BlockChunkListener blockTerrainListener = chunkListeners.get(i);
                            blockTerrainListener.onSpatialUpdated(chunk);
                        }
                    }
                    chunksUpdated++;
                    if (chunksUpdated >= chunksThatNeedUpdate.size()) {
                        // Process at least half the remaining list so we don't get too far behind.
                        break;
                    }
                }
            } finally {
                chunkAccessMutex.unlock();
            }
        }
        return wasUpdateNeeded;
    }
    
    // TODO: Understand this
    public void updateBlockMaterial(){
        chunkAccessMutex.lock();
        try {       
            for (Vector3Int chunkLocation :  chunks.keySet()) {
                BlockChunkControl chunk = chunks.get(chunkLocation);
                     chunk.updateBlockMaterial();
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }
    
    // Add listener for new/modified/removed chunks
    public void addChunkListener(BlockChunkListener blockChunkListener){
        chunkListeners.add(blockChunkListener);
    }
    
    // Remove listener for new/modified/removed chunks
    public void removeChunkListener(BlockChunkListener blockChunkListener){
        chunkListeners.remove(blockChunkListener);
    }
    
    // Accessor for settings
    public CubesSettings getSettings(){
        return settings;
    }

    // Chunks accessor
    // TODO: Document or enforce caller locking mutex before calling
    public HashMap<Vector3Int, BlockChunkControl> getChunks() {
        return chunks;
    }

    
    //Tools
    //Create terrain from height map image.  One block per pixel
    public void setBlocksFromHeightmap(Vector3Int location, String heightmapPath, int maximumHeight, Block block){
        try{
            Texture heightmapTexture = settings.getAssetManager().loadTexture(heightmapPath);
            ImageBasedHeightMap heightmap = new ImageBasedHeightMap(heightmapTexture.getImage(), 1f);
            heightmap.load();
            heightmap.setHeightScale(maximumHeight / 255f);
            setBlocksFromHeightmap(location, getHeightmapBlockData(heightmap.getScaledHeightMap(), heightmap.getSize()), block);
        }catch(Exception ex){
            System.out.println("Error while loading heightmap '" + heightmapPath + "'.");
        }
    }

    // translate pixel value into elevation
    private static int[][] getHeightmapBlockData(float[] heightmapData, int length){
        int[][] data = new int[heightmapData.length / length][length];
        int x = 0;
        int z = 0;
        for(int i=0;i<heightmapData.length;i++){
            data[x][z] = (int) Math.round(heightmapData[i]);
            x++;
            if((x != 0) && ((x % length) == 0)){
                x = 0;
                z++;
            }
        }
        return data;
    }

    //Create terrain from height map image.  One block per pixel
    public void setBlocksFromHeightmap(Vector3Int location, int[][] heightmap, Block block){
        Vector3Int tmpLocation = new Vector3Int();
        Vector3Int tmpSize = new Vector3Int();
        for(int x=0;x<heightmap.length;x++){
            for(int z=0;z<heightmap[0].length;z++){
                tmpLocation.set(location.getX() + x, location.getY(), location.getZ() + z);
                tmpSize.set(1, heightmap[x][z], 1);
                setBlockArea(tmpLocation, tmpSize, block);
            }
        }
    }
    
    // Generate a section of terrain using a noise algoritm
    public void setBlocksFromNoise(Vector3Int location, Vector3Int size, float roughness, Block block){
        Noise noise = new Noise(null, roughness, size.getX(), size.getZ());
        noise.initialise();
        float gridMinimum = noise.getMinimum();
        float gridLargestDifference = (noise.getMaximum() - gridMinimum);
        float[][] grid = noise.getGrid();
        for(int x=0;x<grid.length;x++){
            float[] row = grid[x];
            for(int z=0;z<row.length;z++){
                /*---Calculation of block height has been summarized to minimize the java heap---
                float gridGroundHeight = (row[z] - gridMinimum);
                float blockHeightInPercents = ((gridGroundHeight * 100) / gridLargestDifference);
                int blockHeight = ((int) ((blockHeightInPercents / 100) * size.getY())) + 1;
                ---*/
                int blockHeight = (((int) (((((row[z] - gridMinimum) * 100) / gridLargestDifference) / 100) * size.getY())) + 1);
                Vector3Int tmpLocation = new Vector3Int();
                this.initializeChunk(this.getChunkLocation(tmpLocation));
                for(int y=0;y<blockHeight;y++){
                    tmpLocation.set(location.getX() + x, location.getY() + y, location.getZ() + z);
                    setBlock(tmpLocation, block);
                }
            }
        }
    }
    
    // Set every other block for worst case performance.
    // (This probobly breaks with round blocks right now, needs more testing)
    public void setBlocksForMaximumFaces(Vector3Int location, Vector3Int size, Block block){
        Vector3Int tmpLocation = new Vector3Int();
        for(int x=0;x<size.getX();x++){
            for(int y=0;y<size.getY();y++){
                for(int z=0;z<size.getZ();z++){
                    if(((x ^ y ^ z) & 1) == 1){
                        tmpLocation.set(location.getX() + x, location.getY() + y, location.getZ() + z);
                        setBlock(tmpLocation, block);
                    }
                }
            }
        }
    }

    // Create a copy of the terrain
    @Override
    public BlockTerrainControl clone(){
        BlockTerrainControl blockTerrain = new BlockTerrainControl(settings);
        blockTerrain.setBlocksFromTerrain(this);
        return blockTerrain;
    }
    
    // Serialize one terrain and deserialize another
    // TODO: This probobly doesn't clear out the old terrain state very well
    // TODO: this probobly doesn't call chunk update listeners
    public void setBlocksFromTerrain(BlockTerrainControl blockTerrain){
        CubesSerializer.readFromBytes(this, CubesSerializer.writeToBytes(blockTerrain));
    }

    // Write entire terrain to provided outputStream
    // TODO move to CubesSerializer
    @Override
    public void write(BitOutputStream outputStream){
        chunkAccessMutex.lock();
        try {       
            outputStream.writeInteger(chunks.keySet().size());
            for (Vector3Int chunkLocation :  chunks.keySet()) {
                BlockChunkControl chunk = chunks.get(chunkLocation);
                outputStream.writeInteger(chunkLocation.getX());
                outputStream.writeInteger(chunkLocation.getY());
                outputStream.writeInteger(chunkLocation.getZ());
                chunk.write(outputStream);
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }

    // Generate slice messages for chunk at given location
    // TODO move to CubesSerializer
    public ArrayList<byte[]> writeChunkPartials(Vector3Int chunkLoc) {
        ArrayList<byte[]> returnValue = new ArrayList<byte[]>();
        chunkAccessMutex.lock();
        try {       
            BlockChunkControl chunk = chunks.get(chunkLoc);
            for(int i = 0; i < settings.getChunkSizeY(); i++) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                BitOutputStream bitOutputStream = new BitOutputStream(byteArrayOutputStream);            
                bitOutputStream.writeInteger(chunkLoc.getX());
                bitOutputStream.writeInteger(chunkLoc.getY());
                bitOutputStream.writeInteger(chunkLoc.getZ()); // is this always 0?
                bitOutputStream.writeInteger(i); // Horizontal slice of chunk
                bitOutputStream.writeBoolean(i == 0);
                chunk.write(i, bitOutputStream);
                bitOutputStream.close();
                byte[] chunkBytes = byteArrayOutputStream.toByteArray();
                returnValue.add(chunkBytes);
            }
        } finally {
            chunkAccessMutex.unlock();
        }
        return returnValue;
    }

    // Read chunk from byte stream
    // TODO move to CubesSerializer
    @Override
    public void read(BitInputStream inputStream) throws IOException{
        chunkAccessMutex.lock();
        try {       
            int chunkCount = inputStream.readInteger();
            while (chunkCount > 0) {
                int chunkX = inputStream.readInteger();
                int chunkY = inputStream.readInteger();
                int chunkZ = inputStream.readInteger();
                Vector3Int chunkLocation;
                chunkLocation = new Vector3Int(chunkX, chunkY, chunkZ);
                initializeChunk(chunkLocation);
                BlockChunkControl chunk = chunks.get(chunkLocation);
                chunk.read(inputStream);
                --chunkCount;
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }
    
    // Mark a chunk to be updated on next updateSpatial cycle
    private void markChunkNeedsUpdate(Vector3Int chunkLocation, boolean needsPhysicsUpdate) {
        BlockChunkControl chunk = this.getChunkByChunkLocation(chunkLocation);
        if (chunk != null) {
            chunk.markNeedUpdate(needsPhysicsUpdate);
        }
    }

    // INTERNAL USE
    // Add chunk to update queue for next updateSpatial cycle
    public void addChunkToNeedsUpdateList(BlockChunkControl chunk) {
        if (!chunksThatNeedUpdate.contains(chunk)) {
            chunksThatNeedUpdate.add(chunk);
        }
    }
    
    // True if a sliced chunk is in process of being constructed
    // This can be used to throttle chunk updates
    // TODO move to CubesSerializer
    public boolean getChunksInProgress() {
        return !chunksInProgres.isEmpty();
    }
    
    // track chunks current in progress of being built from slices
    private HashMap<Vector3Int, BlockChunkControl> chunksInProgres = new HashMap<Vector3Int, BlockChunkControl>();
    
    // Read chunk slice from input stream.
    // Returns true if chunk is finished.
    // Assumes chunks slices are read in order.
    // TODO move to CubesSerializer
    public boolean readChunkPartial(BitInputStream inputStream) throws IOException{
        int chunkX = inputStream.readInteger();
        int chunkY = inputStream.readInteger();
        int chunkZ = inputStream.readInteger();
        int chunkSlice = inputStream.readInteger();
        boolean lastSlice = inputStream.readBoolean();

        Vector3Int chunkLocation;
        chunkLocation = new Vector3Int(chunkX, chunkY, chunkZ);
        if (!chunksInProgres.containsKey(chunkLocation)) {
            setChunkStarted(chunkLocation);
        }
        
        BlockChunkControl chunk = chunksInProgres.get(chunkLocation);
        chunk.read(chunkSlice, inputStream);
        if (lastSlice) {
            chunkAccessMutex.lock();
            try {
                chunk.setLocation(this, chunkLocation);
                chunk.updateSpatial();
                for (int x = -1; x < 2; ++x) {
                    for (int y = -1; y < 2; ++y) {
                        for (int z = -1; z < 2; ++z) {
                            markChunkNeedsUpdate(chunkLocation.add(x,y,z), true);
                        }
                    }
                }
                Vector3Int sunlightKey = new Vector3Int(chunkLocation.getX(), 0, chunkLocation.getZ());
                if (!sunlight.containsKey(sunlightKey)) {
                    SunlightChunk sl = new SunlightChunk(this, settings, chunkLocation);
                    sunlight.put(sunlightKey, sl);
                }
                chunk.setSunlight(sunlight.get(sunlightKey));
                finishedChunks.add(chunkLocation);
            } finally {
                chunkAccessMutex.unlock();
            }
        }
        return lastSlice;
    }

    // Add blocks in progress that have been marked completed to the terrain
    // This is seperate from chunksInProgress so chunks can be built on other threads
    // and then this method called on main render thread.
    private ArrayList<Vector3Int> finishedChunks = new ArrayList<Vector3Int>();
    public void finishChunks() {
        if(this.finishedChunks.size() > 0) {
            chunkAccessMutex.lock();
            try {
                if(this.finishedChunks.size() > 0) {
                    Vector3Int chunkKey = finishedChunks.get(0);
                    
                    finishedChunks.remove(0);
                    BlockChunkControl chunk = chunksInProgres.get(chunkKey);
                    if (chunk != null) {
                        this.chunks.put(chunkKey, chunk);
                        this.addChunkToSpatial(chunk);
                        chunk.markNeedUpdate(true);
                        chunksInProgres.remove(chunkKey);
                    }
                }
            } finally {
                chunkAccessMutex.unlock();
            }
        }
    }
    
    // Helper method to accept byte array over stream
    public void readChunkPartial(byte data[]) {
         BitInputStream bitInputStream = new BitInputStream(new ByteArrayInputStream(data));
         try {
             this.readChunkPartial(bitInputStream);
         } catch(IOException ex){
             ex.printStackTrace();
         }
    }

    // Get light level of a block using global coordinates
    byte getLightLevelOfBlock(Vector3Int globalLocation) {
        BlockChunkControl chunk = getChunkByBlockLocation(globalLocation);
        if (chunk == null) {
            return 1;
        }
        Vector3Int localBlockLocation = getLocalBlockLocation(globalLocation, chunk);
        return chunk.getLightAt(localBlockLocation);
    }
    
    // Get light source strength at block location
    byte getLightSourceOfBlock(Vector3Int globalLocation) {
        BlockChunkControl chunk = getChunkByBlockLocation(globalLocation);
        if (chunk == null) {
            return 1;
        }
        Vector3Int localBlockLocation = getLocalBlockLocation(globalLocation, chunk);
        return chunk.getLightSourceAt(localBlockLocation);
    }

    // Attach chunk to terrain
    public void setChunk(Vector3Int chunkLocation, BlockChunkControl chunk) {
        chunkAccessMutex.lock();
        try {       
            if (!chunks.containsKey(chunkLocation)) {
                chunk.setLocation(this, chunkLocation);
                chunks.put(chunkLocation, chunk);
            } else {
                throw new UnsupportedOperationException("clearing old chunk not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }
    
    // Remove all chunks maxDistance or more chunks away from chunkCenter
    // This is a square search.
    public void cullChunks(Vector3Int chunkCenter, int maxDistance) {
        chunkAccessMutex.lock();
        try {
            Set<Vector3Int> keys = new HashSet<Vector3Int>();
            keys.addAll(chunks.keySet());
            for (Vector3Int key : keys) {
                if ((Math.abs(key.getX() - chunkCenter.getX()) > maxDistance) || 
                    (Math.abs(key.getY() - chunkCenter.getY()) > maxDistance) || 
                    (Math.abs(key.getZ() - chunkCenter.getZ()) > maxDistance)) {
                    this.removeChunk(key);
                }
                        
            }
        } finally {
            chunkAccessMutex.unlock();
        }
    }
    
    // Remove a given chunk from terrain
    private void removeChunk(Vector3Int chunkToRemove) {
        BlockChunkControl chunk = this.chunks.get(chunkToRemove);
        if (chunk != null) {
            if (this.spatial != null) {
                this.spatial.removeControl(chunk);
            }
            for(int i=0;i<chunkListeners.size();i++){
                BlockChunkListener blockTerrainListener = chunkListeners.get(i);
                blockTerrainListener.onSpatialRemoved(chunk);
            }
            this.chunks.remove(chunkToRemove);
        }
    }

    // Get chunk location of cunck that contains block at provided location
    // TODO: move to blockNavigation
    public Vector3Int worldLocationToChunkLocation(Vector3f location) {
        // TODO: Assume terrain is at 0,0,0 ok?
        Vector3Int blockLocation = new Vector3Int((int)(location.x / settings.getBlockSize()), (int)(location.y / settings.getBlockSize()), (int)(location.z / settings.getBlockSize()));
        //Vector3Int chunkLocation = new Vector3Int(blockLocation.getX() / settings.getChunkSizeX(), blockLocation.getY() / settings.getChunkSizeY(), blockLocation.getZ() / settings.getChunkSizeZ() );
         if(location.getX() < 0)
            blockLocation.setX(blockLocation.getX() - 1);
        if(location.getY() < 0)
            blockLocation.setY(blockLocation.getY() - 1);
        if(location.getZ() < 0)
            blockLocation.setZ(blockLocation.getZ() - 1);
        Vector3Int chunkLocation = getChunkLocation(blockLocation);
        return chunkLocation;
    }

    // When a block is cleared, the next block down may not be right below it.  This searches downward for it.
    // Used to update where sunlight can reach.
    int findNextBlockDown(int chunkX, int chunkZ, int localBlockX , int localBlockZ, int globalStartingY) {
        int missingChunkCount = 0;
        int globalBoxX = chunkX * settings.getChunkSizeX() + localBlockX;
        int chunkY = globalStartingY / settings.getChunkSizeY();
        //System.out.println("findNextBlockDown " + globalStartingY);
        Vector3Int chunkLocation = new Vector3Int(chunkX, chunkY, chunkZ);
        Vector3Int blockLocalLocation = new Vector3Int(localBlockX, globalStartingY % settings.getChunkSizeY(), localBlockZ);
        if (globalStartingY < 0) {
            chunkY--;
        }
        // sanity check math.
        while (missingChunkCount < 5) {
            if (chunks.containsKey(chunkLocation)) {
                BlockChunkControl chunk = chunks.get(chunkLocation);
                while (blockLocalLocation.getY() > 0) {
                    if (chunk.getBlock(blockLocalLocation) != null) {
                        //System.out.println("returning " + chunk.getBlockLocation().add(blockLocalLocation).getY());
                        return chunk.getBlockLocation().add(blockLocalLocation).getY();
                    }
                    blockLocalLocation.setY(blockLocalLocation.getY() - 1);
                }
            } else {
                missingChunkCount++;
            }
            chunkY--;
            chunkLocation.setY(chunkY);
            blockLocalLocation.setY(settings.getChunkSizeY() - 1);
        }
        //System.out.println("returning MAX");
        return Integer.MAX_VALUE;
    }

    // Update light across virtical block range
    void updateSunlightFromTo(int globalX, int globalZ, int startGlobalY, int endGlobalY) {
        Vector3Int blockLoc = new Vector3Int(globalX, startGlobalY, globalZ);
        int missingChunkCount = 0;
        for(int i = startGlobalY; missingChunkCount < 5 && i <= endGlobalY; ++i) {
            blockLoc.setY(i);
            BlockChunkControl chunk = getChunkByBlockLocation(blockLoc);
            if (chunk != null) {
                Vector3Int localBlock = getLocalBlockLocation(blockLoc, chunk);
                chunk.updateSunlight(localBlock.getX(), localBlock.getY(), localBlock.getZ());
            } else {
                missingChunkCount++;
            }
        }
    }

    // True if the given chunk location is in the chunkInProgress queue
    public boolean isPendingChunkLocation(Vector3Int location){
        boolean returnValue = false;
        chunkAccessMutex.lock();
        try {
            returnValue = chunksInProgres.containsKey(location);
        } finally {
            chunkAccessMutex.unlock();
        }
        return returnValue;
    }

    // Put chunk in 'inprogress' state
    public void setChunkStarted(Vector3Int chunkLocation) {
        boolean returnValue = false;
        chunkAccessMutex.lock();
        try {
            if (!chunksInProgres.containsKey(chunkLocation)) {
                chunksInProgres.put(chunkLocation, new BlockChunkControl(this, chunkLocation.getX(), chunkLocation.getY(), chunkLocation.getZ()));
            }
            System.out.println("Chunks in progress " + chunksInProgres.keySet().size());
        } finally {
            chunkAccessMutex.unlock();
        }

    }

}
