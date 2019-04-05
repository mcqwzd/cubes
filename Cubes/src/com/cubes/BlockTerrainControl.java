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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
/**
 *
 * @author Carl
 */

/**
 * 
 * TODO:
 * Proformance:
 * Delay rendering cube until neighboring cubes on all side are loaded.
 * Count blocks durring generation and send a smaller 'all the blocks are X' message for blocks that only contain one type of block. (including air)
 * when a block gets unloaded,  make sure its not sitting in any of the render queues
 * when rendering blocks, create two mesh optimizers so we can have two threads rendering them.  One can be generating mesh while the other gens lights without conflicting on mutexes.
 * if all the blocks are of one type and that one type is air, or is solid, short cut the mesh generation by skipping interior blocks.
 * 
 * Playability:
 * freeze player physics if the player is in an unloaded chunk.
 * don't let player 'fall' if they get stuck inside solid chunks.  
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
        return Vector3Int.create(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
    }
    private ExecutorService renderPool;
    
    // Constructor
    public BlockTerrainControl(CubesSettings settings){
        this.settings = settings;
        chunks = new HashMap<Vector3Int, BlockChunkControl>();
        chunksThatNeedUpdate = new ConcurrentLinkedQueue<BlockChunkControl>();
        chunksReadyToRender = new ConcurrentLinkedQueue<BlockChunkControl>();
        // This needs to remain a single executor until generate mesh static methods are made thread safe.
        renderPool = Executors.newSingleThreadExecutor(); //newFixedThreadPool(4);
    }
    
    // Reference to settings
    private CubesSettings settings;
    
    // Lock for multi threading support
    // TODO: Read/Write lock this sucker
    // TODO: find a thread safe way to subdivide some write operations like adding chunks vs updating chunks
    //private Lock chunkAccessMutex = new ReentrantLock(true);  
    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(/*fairness*/false);
    private HashMap<Vector3Int, BlockChunkControl> chunks;
    private Queue<BlockChunkControl> chunksThatNeedUpdate;
    private Queue<BlockChunkControl> chunksReadyToRender;
    //private List<BlockChunkControl> chunksReadyToRender;
    private HashMap<Vector3Int, SunlightChunk> sunlight = new HashMap<Vector3Int, SunlightChunk>();
    private ArrayList<BlockChunkListener> chunkListeners = new ArrayList<BlockChunkListener>();
    private HashMap<String, Long> lockTimes;
    public boolean readLockTry(String reason, long limitMS) {
        //System.out.println("try locking " + reason + " at " + System.currentTimeMillis() % 10000);
        try {
            if (readWriteLock.readLock().tryLock(limitMS, TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException ex) {
        }
        System.out.println("Failed to lock for " + reason);
        return false;
    }
    public void readLock(String reason) {
        //lastReadLock = reason;
        //System.out.println("locking " + reason + " at " + System.currentTimeMillis() % 10000);
        long lockStart = System.currentTimeMillis();
        readWriteLock.readLock().lock(); 
        long lockFinish = System.currentTimeMillis();
        if ((lockFinish - lockStart) > 32) {
            System.err.println("read lock took " + (lockFinish - lockStart) + "ms for " + reason);
        }
    }
    public void readUnlock(String reason) {
        //System.out.println("unlocking " + reason + " at " + System.currentTimeMillis() % 10000);
        readWriteLock.readLock().unlock();
    }
    private long lockStartTime = 0;
    public void writeLock(String reason) {
        if (readWriteLock.getReadHoldCount() > 0) {
            System.err.println("FAILING to lock " + reason + "because current thread also read locked");
        }
        //System.err.println("Write locking " + reason + " at " + System.currentTimeMillis() % 10000);
        lockStartTime = System.currentTimeMillis();
        long lockStart = System.currentTimeMillis();
        readWriteLock.writeLock().lock(); 
        long lockFinish = System.currentTimeMillis();
        if ((lockFinish - lockStart) > 32) {
            System.err.println("write lock took " + (lockFinish - lockStart) + "ms to lock for " + reason);
        }
    }
    public void writeUnlock(String reason) {
        long endLockTime = System.currentTimeMillis();
        if (endLockTime - lockStartTime > 32) {
            System.err.println("Write lock consumed " + (endLockTime - lockStartTime) + "ms too " + reason );
        }
        //System.err.println("Write lock " + reason + " took " + (endLockTime - lockStartTime));
        readWriteLock.writeLock().unlock();
    }
    
    // If there isn't a chunk at a given location, create a new blank one
    private void initializeChunk(Vector3Int location) {
        writeLock("initializeChunk");
        try {
            if (!chunks.containsKey(location)) {
                BlockChunkControl chunk = new BlockChunkControl(this, location.getX(), location.getY(), location.getZ());
                chunks.put(location, chunk);
                Vector3Int sunlightKey = Vector3Int.create(location.getX(), 0, location.getZ());
                if (!sunlight.containsKey(sunlightKey)) {
                    SunlightChunk sl = new SunlightChunk(this, settings, location);
                    sunlight.put(sunlightKey, sl);
                }
                chunk.setSunlight(sunlight.get(sunlightKey));
            }
        } finally {
            writeUnlock("initializeChunk");
        }
    }

    // WARNING: Slow (see addChunkToSpatial)
    // Reinitialize spatials of every chnk
    @Override
    public void setSpatial(Spatial spatial){
        Spatial oldSpatial = this.spatial;
        super.setSpatial(spatial);
        readLock("setSpatial");
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
            readUnlock("setSpatial");
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
        //System.gc();
        updateSpatial();
        Thread.yield();
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
        return getBlock(Vector3Int.create(x, y, z)); // TODO: Can this creation be avoided?
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
        Vector3Int tmpLocation = Vector3Int.create();
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
        setBlock(Vector3Int.create(x, y, z), block);
    }
    
    // set a block using global cordinates
    public void setBlock(Vector3Int location, Block block){
        writeLock("setBlock");
        try {
            this.initializeChunk(this.getChunkLocation(location));
            BlockTerrain_LocalBlockState localBlockState = getLocalBlockState(location);
            if(localBlockState != null){
                localBlockState.setBlock(block);
            }
        } finally {
            writeUnlock("setBlock");
        }
    }
    
    // Helper method to clear a cubic area to a block
    public void removeBlockArea(Vector3Int location, Vector3Int size){
        Vector3Int tmpLocation = Vector3Int.create();
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
        removeBlock(Vector3Int.create(x, y, z));
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
    public void removeLightSource(Collection<LightQueueElement> lightsToRemove) {
        if (lightsToRemove.size() == 0) {
            return;
        }
        for (LightQueueElement element : lightsToRemove) {
            removeLightSource(getGlobalBlockLocation(element.getLocation(), element.getChunk()));            
        }
     }

    // INTERNAL USE ONLY
    // Add a batch of light sources
    public void addLightSource(Collection<LightQueueElement> lightsToAdd) {
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

        for (LightQueueElement element : lightsToAdd) {
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
                Vector3Int.dispose(localBlockLocation);
            }
            Vector3Int.dispose(location);
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

        List<LightQueueElement> lightsToReplace = new ArrayList<LightQueueElement>();
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
                    lightsToReplace.add(new LightQueueElement(localBlockLocation,chunk,chunk.getLightSourceAt(localBlockLocation),false));
                } else if (chunk.propigateDark(localBlockLocation, oldLight)) {
                    for(int face = 0; face < Block.Face.values().length; face++){
                        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, Block.Face.values()[face]);
                        next.add(neighborLocation);
                    }
                } else {
                    byte light = chunk.getLightAt(localBlockLocation);
                    if(light > 0) {
                        lightsToReplace.add(new LightQueueElement(localBlockLocation,chunk,light,false));
                    }
                }
                if (locationsToPropigateTo.size() == 0) {
                    locationsToPropigateTo = next;
                    next = new LinkedList<Vector3Int>();
                    oldLight--;
                }
            }
            Vector3Int.dispose(location);
        }
        addLightSource(lightsToReplace);
        for (LightQueueElement e : lightsToReplace) {
            Vector3Int.dispose(e.getLocation());
        }

    }
    
    // using global block location, return the chunk it is in
    public BlockChunkControl getChunkByBlockLocation(Vector3Int blockLocation){
        Vector3Int chunkLocation = getChunkLocation(blockLocation);
        BlockChunkControl returnVal = getChunkByChunkLocation(chunkLocation);
        Vector3Int.dispose(chunkLocation);
        return returnVal;
    }

    // using chunk location, get chunk
    // TODO: move responsability to lock/unlock to caller
    public BlockChunkControl getChunkByChunkLocation(Vector3Int chunkLocation){
        BlockChunkControl chunk = null;
        readLock("getChunkByChunkLocation");
        try {
            chunk = chunks.get(chunkLocation);
        } finally {
            readUnlock("getChunkByChunkLocation");
        }
        return chunk;
    }

    // Check if chunk exists.
    public boolean isValidChunkLocation(Vector3Int location){
        boolean returnValue = false;
        if (readWriteLock.getReadHoldCount() < 1) {
            System.err.println("isValidChunkLocation called without locking");
        }
        try {
            returnValue = chunks.containsKey(location);
        } finally {
        }
        return returnValue;
    }
    
    // Return true if the provided global location is exposed to sunlight
    public boolean getIsGlobalLocationAboveSurface(Vector3Int blockLocation) {
        boolean returnValue = false;
        readLock("getIsGlobalLocationAboveSurface");
        try {
            Vector3Int chunkLoc = getChunkLocation(blockLocation);
            BlockChunkControl chunk = chunks.get(chunkLoc);
            if (chunk == null) {
                returnValue = true;
            } else {
                returnValue = chunk.isBlockAboveSurface(getLocalBlockLocation(blockLocation, chunk));
            }
        } finally {
            readUnlock("getIsGlobalLocationAboveSurface");
        }
        return returnValue;
    }
    
    /** Get chunk location from block location */
    // TODO: Move to blockNavigator
    public Vector3Int getChunkLocation(Vector3Int blockLocation){
        Vector3Int chunkLocation = Vector3Int.create();
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
        Vector3Int globalLocation = Vector3Int.create();
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
        Vector3Int localLocation = Vector3Int.create();
        int localX = (globalBlockLocation.getX() - chunk.getBlockLocation().getX());
        int localY = (globalBlockLocation.getY() - chunk.getBlockLocation().getY());
        int localZ = (globalBlockLocation.getZ() - chunk.getBlockLocation().getZ());
        localLocation.set(localX, localY, localZ);
        return localLocation;
    }
    
    private boolean execThreadRunning = false;
    public boolean updateSpatial(){

        boolean wasUpdateNeeded = false;
        boolean hasLock = false;
        List<String> debugLog = new ArrayList<String>();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        if (startTime - lastSpatialUpdate > 30) {
            System.out.println("Long time between frames " + (startTime - lastSpatialUpdate));
        }
        hasLock = readLockTry("updateSpatial", 16);
        if (hasLock) {
            int loopCount = 0;
            try {
                int chunksUpdated = 0;
                if ( !execThreadRunning && !chunksThatNeedUpdate.isEmpty()) {
                    this.renderPool.execute(new Runnable() {
                        public void run() {
                            execThreadRunning = true;
                            while (!chunksThatNeedUpdate.isEmpty()) {
                                long innerStartTime = System.currentTimeMillis();
                                final BlockChunkControl chunkToRender = chunksThatNeedUpdate.poll();
                                long startTime = System.currentTimeMillis();
                                // locks inside prepareMesh
                                chunkToRender.prepareMeshs();
                                // thread safe queue
                                chunksReadyToRender.add(chunkToRender);
                                long endTime = System.currentTimeMillis();
                                if (endTime - innerStartTime > 32) {
                                    System.err.println("background render took " + (endTime - startTime) + " to render ");
                                }
                                //Thread.yield();
                            }
                            execThreadRunning = false;
                        }
                    });
                }
                while (!chunksReadyToRender.isEmpty() && (System.currentTimeMillis() - startTime) < 16 ) {
                    debugLog.add("" + System.currentTimeMillis() + " while start ");
                    loopCount++;
                    BlockChunkControl chunk = chunksReadyToRender.remove();
                    if (chunk != null) {
                        if (chunk.readyToRender()) {
                            // TODO: find out why this rarely happens.
                            // how is get(0) ever returning null?
                            debugLog.add("" + System.currentTimeMillis() + " starting chunk " + chunk.lightsToAdd.size() + "." + chunk.lightsToRemove.size());
                            //chunk.updateLights();
                            //debugLog.add("" + System.currentTimeMillis() + " updated Lights remaining: " + chunk.lightsToAdd.size() + "." + chunk.lightsToRemove.size());
                            wasUpdateNeeded = chunk.updateSpatial(debugLog);
                            debugLog.add("" + System.currentTimeMillis() + " updated Spatial");
                            if (wasUpdateNeeded) {
                                debugLog.add("" + System.currentTimeMillis() + " update needed");
                                for(int i=0;i<chunkListeners.size();i++){
                                    BlockChunkListener blockTerrainListener = chunkListeners.get(i);
                                    blockTerrainListener.onSpatialUpdated(chunk);
                                    debugLog.add("" + System.currentTimeMillis() + " on spatial updated");
                                }                            
                            }
                            chunksUpdated++;
                            //if (chunksUpdated >= chunksReadyToRender.size()) {
                                // Process at least half the remaining list so we don't get too far behind.
                            //    break;
                            //}
                        }
                    }
                }
            } finally {
                readUnlock("updateSpatial");
                endTime = System.currentTimeMillis();
                if (endTime - startTime > 32) {
                    for(String s : debugLog) {
                        System.out.println(s);
                    }
                    System.out.println("terrain render took " + (endTime - startTime) + " to render " + loopCount + " iterations ");
                }
            }
        }
        this.lastSpatialUpdate = endTime;
        return wasUpdateNeeded;
    }
    private long lastSpatialUpdate = 0;
    // TODO: Understand this
    public void updateBlockMaterial(){
        writeLock("updateBlockMaterial");
        try {       
            for (Vector3Int chunkLocation :  chunks.keySet()) {
                BlockChunkControl chunk = chunks.get(chunkLocation);
                     chunk.updateBlockMaterial();
            }
        } finally {
            writeUnlock("updateBlockMaterial");
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
        Vector3Int tmpLocation = Vector3Int.create();
        Vector3Int tmpSize = Vector3Int.create();
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
                Vector3Int tmpLocation = Vector3Int.create();
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
        Vector3Int tmpLocation = Vector3Int.create();
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
        readLock("Write Terrain");
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
            readUnlock("Write Terrain");
        }
    }

    // Generate slice messages for chunk at given location
    // TODO move to CubesSerializer
    public ArrayList<byte[]> writeChunkPartials(Vector3Int chunkLoc) {
        ArrayList<byte[]> returnValue = new ArrayList<byte[]>();
        readLock("Write Chunk Partials");
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
            readUnlock("Write Chunk Partials");
        }
        return returnValue;
    }

    // Read chunk from byte stream
    // TODO move to CubesSerializer
    @Override
    public void read(BitInputStream inputStream) throws IOException{
        writeLock("read terrain");
        try {       
            int chunkCount = inputStream.readInteger();
            while (chunkCount > 0) {
                int chunkX = inputStream.readInteger();
                int chunkY = inputStream.readInteger();
                int chunkZ = inputStream.readInteger();
                Vector3Int chunkLocation;
                chunkLocation = Vector3Int.create(chunkX, chunkY, chunkZ);
                initializeChunk(chunkLocation);
                BlockChunkControl chunk = chunks.get(chunkLocation);
                chunk.read(inputStream);
                --chunkCount;
            }
        } finally {
            writeUnlock("read terrain");
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
        chunkLocation = Vector3Int.create(chunkX, chunkY, chunkZ);
        if (!chunksInProgres.containsKey(chunkLocation)) {
            setChunkStarted(chunkLocation);
        }
        
        BlockChunkControl chunk = chunksInProgres.get(chunkLocation);
        chunk.read(chunkSlice, inputStream);
        if (lastSlice) {
            writeLock("ReadChunkPartial Last slice");
            long startTime = System.currentTimeMillis();
            ArrayList<String> debugLog = new ArrayList<String>();
            try {
                chunk.setLocation(this, chunkLocation);
                debugLog.add("" + System.currentTimeMillis() + " setLocation");
                //chunk.updateSpatial();
                for (int x = -1; x < 2; ++x) {
                    for (int y = -1; y < 2; ++y) {
                        for (int z = -1; z < 2; ++z) {
                            markChunkNeedsUpdate(chunkLocation.add(x,y,z), true);
                            debugLog.add("" + System.currentTimeMillis() + " mark Chunk");
                        }
                    }
                }
                Vector3Int sunlightKey = Vector3Int.create(chunkLocation.getX(), 0, chunkLocation.getZ());
                if (!sunlight.containsKey(sunlightKey)) {
                    debugLog.add("" + System.currentTimeMillis() + " need to create sunlight");
                    SunlightChunk sl = new SunlightChunk(this, settings, chunkLocation);
                    debugLog.add("" + System.currentTimeMillis() + " sunlight created");
                    sunlight.put(sunlightKey, sl);
                }
                chunk.setSunlight(sunlight.get(sunlightKey));
                debugLog.add("" + System.currentTimeMillis() + " sunlight set");
                finishedChunks.add(chunkLocation);
                debugLog.add("" + System.currentTimeMillis() + " finish chunk added");
            } finally {
                if ((System.currentTimeMillis() - startTime) > 100) {
                    for(String s : debugLog) {
                        System.err.println("" + s);
                    }
                    System.err.println("That took too long");
                }
                writeUnlock("ReadChunkPartial Last slice");
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
            writeLock("finishChunks");
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
                writeUnlock("finishChunks");
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
        writeLock("setChunk");
        try {       
            if (!chunks.containsKey(chunkLocation)) {
                chunk.setLocation(this, chunkLocation);
                chunks.put(chunkLocation, chunk);
            } else {
                throw new UnsupportedOperationException("clearing old chunk not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        } finally {
            writeUnlock("setChunk");
        }
    }
    
    // Remove all chunks maxDistance or more chunks away from chunkCenter
    // This is a square search.
    public void cullChunks(Vector3Int chunkCenter, int maxDistance) {
        readLock("cullChunks");
        try {
            Set<Vector3Int> keys = new HashSet<Vector3Int>();
            Set<Vector3Int> chunksToRemove = new HashSet<Vector3Int>();
            keys.addAll(chunks.keySet());
            for (Vector3Int key : keys) {
                if ((Math.abs(key.getX() - chunkCenter.getX()) > maxDistance) || 
                    (Math.abs(key.getY() - chunkCenter.getY()) > maxDistance) || 
                    (Math.abs(key.getZ() - chunkCenter.getZ()) > maxDistance)) {
                    chunksToRemove.add(key);
                }
                        
            }
            if (chunksToRemove.size() > 0) {
               readUnlock("cullChunks");
               writeLock("cullChunks Remove");
               try {
                    for (Vector3Int key : chunksToRemove) {
                         this.removeChunk(key);
                    }
               } finally {
                   writeUnlock("cullChunks Remove");
                   readLock("cullChunks");
               }
            }
            
        } finally {
            readUnlock("cullChunks");
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
        Vector3Int blockLocation = Vector3Int.create((int)(location.x / settings.getBlockSize()), (int)(location.y / settings.getBlockSize()), (int)(location.z / settings.getBlockSize()));
        //Vector3Int chunkLocation = Vector3Int.create(blockLocation.getX() / settings.getChunkSizeX(), blockLocation.getY() / settings.getChunkSizeY(), blockLocation.getZ() / settings.getChunkSizeZ() );
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
        Vector3Int chunkLocation = Vector3Int.create(chunkX, chunkY, chunkZ);
        Vector3Int blockLocalLocation = Vector3Int.create(localBlockX, globalStartingY % settings.getChunkSizeY(), localBlockZ);
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
        if (startGlobalY <= Integer.MIN_VALUE + 10) {
            Vector3Int blockLoc = Vector3Int.create(globalX, startGlobalY, globalZ);
            int missingChunkCount = 0;
            for(int i = endGlobalY; missingChunkCount < 5 ; --i) {
                blockLoc.setY(i);
                BlockChunkControl chunk = getChunkByBlockLocation(blockLoc);
                if (chunk != null) {
                    Vector3Int localBlock = getLocalBlockLocation(blockLoc, chunk);
                    chunk.updateSunlight(localBlock.getX(), localBlock.getY(), localBlock.getZ());
                    Vector3Int.dispose(localBlock);
                } else {
                    missingChunkCount++;
                    if ((blockLoc.getY() + 1) % settings.getChunkSizeY() == 0) {
                        i -= (settings.getChunkSizeY() - 1);
                    }
                }
            }
        } else {
            Vector3Int blockLoc = Vector3Int.create(globalX, startGlobalY, globalZ);
            int missingChunkCount = 0;
            for(int i = startGlobalY; missingChunkCount < 5 && i <= endGlobalY; ++i) {
                blockLoc.setY(i);
                BlockChunkControl chunk = getChunkByBlockLocation(blockLoc);
                if (chunk != null) {
                    Vector3Int localBlock = getLocalBlockLocation(blockLoc, chunk);
                    chunk.updateSunlight(localBlock.getX(), localBlock.getY(), localBlock.getZ());
                    Vector3Int.dispose(localBlock);
                } else {
                    missingChunkCount++;
                    if (blockLoc.getY() % settings.getChunkSizeY() == 0) {
                        i += (settings.getChunkSizeY() - 1);
                    }
                }
            }
        }
    }

    // True if the given chunk location is in the chunkInProgress queue
    public boolean isPendingChunkLocation(Vector3Int location){
        boolean returnValue = false;
        if (readWriteLock.getReadHoldCount() < 1) {
            System.err.println("isPendingChunkLocation called without locking");
        }
        try {
            returnValue = chunksInProgres.containsKey(location);
        } finally {
        }
        return returnValue;
    }
    public int pendingChunkCount() {
        int returnValue = 0;
        if (readWriteLock.getReadHoldCount() < 1) {
            System.err.println("isPendingChunkLocation called without locking");
        }
        try {
            returnValue = chunksInProgres.keySet().size();
        } finally {
        }
        return returnValue;
    }

    // Put chunk in 'inprogress' state
    public void setChunkStarted(Vector3Int chunkLocation) {
        boolean returnValue = false;
        writeLock("setChunkStarted");
        try {
            if (!chunksInProgres.containsKey(chunkLocation)) {
                chunksInProgres.put(chunkLocation, new BlockChunkControl(this, chunkLocation.getX(), chunkLocation.getY(), chunkLocation.getZ()));
            }
        } finally {
            writeUnlock("setChunkStarted");
        }

    }
}
