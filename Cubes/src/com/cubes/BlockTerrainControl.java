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

    // TODO now that vector3Int should support hash sets, get rid of this.
    public static String keyify(Vector3Int key) {
        return "" + key.getX() + "." + key.getY() + '.' + key.getZ();
    }
    public static Vector3Int vectorify(String key) {
        String split[] = key.split("\\.");
        if (split.length != 3) {
            return null;
        }
        return new Vector3Int(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
    }
    
    public BlockTerrainControl(CubesSettings settings, Vector3Int chunksCount){
        this.settings = settings;
        chunks = new HashMap<Vector3Int, BlockChunkControl>();
        chunksThatNeedUpdate = new ArrayList<BlockChunkControl> ();
    }
    private CubesSettings settings;
    private Lock chunkAccessMutex = new ReentrantLock(true);    
    private HashMap<Vector3Int, BlockChunkControl> chunks;
    private List<BlockChunkControl> chunksThatNeedUpdate;
    private HashMap<Vector3Int, SunlightChunk> sunlight = new HashMap<Vector3Int, SunlightChunk>();
    private ArrayList<BlockChunkListener> chunkListeners = new ArrayList<BlockChunkListener>();
    
    private void initializeChunk(Vector3Int location) {
        chunkAccessMutex.lock();
        try {
            if (!chunks.containsKey(location)) {
                BlockChunkControl chunk = new BlockChunkControl(this, location.getX(), location.getY(), location.getZ());
                chunks.put(location, chunk);
                Vector3Int sunlightKey = new Vector3Int(location.getX(), 0, location.getY());
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
    
    public Block getBlock(int x, int y, int z){
        return getBlock(new Vector3Int(x, y, z));
    }
    
    public Block getBlock(Vector3Int location){
        BlockTerrain_LocalBlockState localBlockState = getLocalBlockState(location);
        if(localBlockState != null){
            return localBlockState.getBlock();
        }
        return null;
    }
    
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
    
    public void setBlock(int x, int y, int z, Block block){
        setBlock(new Vector3Int(x, y, z), block);
    }
    
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
    
    public void removeBlock(int x, int y, int z){
        removeBlock(new Vector3Int(x, y, z));
    }
    
    public void removeBlock(Vector3Int location){
        BlockTerrain_LocalBlockState localBlockState = getLocalBlockState(location);
        if(localBlockState != null){
            localBlockState.removeBlock();
        }
    }
    
    private BlockTerrain_LocalBlockState getLocalBlockState(Vector3Int blockLocation){
        BlockChunkControl chunk = getChunkByBlockLocation(blockLocation);
        if(chunk != null){
            Vector3Int localBlockLocation = getLocalBlockLocation(blockLocation, chunk);
            return new BlockTerrain_LocalBlockState(chunk, localBlockLocation);
        }
        return null;
    }
    
    public void removeLightSource(HashMap<Vector3Int, LightQueueElement> lightsToRemove) {
        if (lightsToRemove.size() == 0) {
            return;
        }
        for (LightQueueElement element : lightsToRemove.values()) {
            removeLightSource(getGlobalBlockLocation(element.getLocation(), element.getChunk()));
        }
     }

    public void addLightSource(HashMap<Vector3Int, LightQueueElement> lightsToAdd) {
        if (lightsToAdd.size() == 0) {
            return;
        }
        //System.err.println("addLightSource " + lightsToAdd.size());
        if (!getSettings().getLightsEnabled()) {
            System.out.println("AddLightSource called with lights disabled");
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
        //long startTime = Calendar.getInstance().getTimeInMillis();
        //long endTime;
        //System.out.println("There are " + lightsToAdd.values().size() + " lights to add");
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
        //endTime = Calendar.getInstance().getTimeInMillis();
        //System.out.println(" part 1 took " + (endTime - startTime));
        //startTime = endTime;

       //System.out.println("There are " + locationsToPropigateTo.size() + " locationsToPropigate");
         while (locationsToPropigateTo.size() > 0) {
            propigateElement p = locationsToPropigateTo.remove();
            Vector3Int location = p.location;
            byte brightness = p.brightness;
            //if (debugLogs) {
            //    System.out.println("addLightSource  locationsToPropigateTo " + location + " level " + brightness + " place ");
            //}

            
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
               //System.out.println("There are " + locationsToPropigateTo.size() + " locationsToPropigate");
                next = new LinkedList<propigateElement>();
            }
        }
        //endTime = Calendar.getInstance().getTimeInMillis();
        //System.out.println(" part 2 took " + (endTime - startTime));
    }

    public void addLightSource(Vector3Int globalLocation, byte brightness) {
        addLightSource(globalLocation, brightness, true);
        if (getLightLevelOfBlock(globalLocation) != getLightSourceOfBlock(globalLocation)) {
            addLightSource(globalLocation, brightness, true);
        }
    }

    
    public void addLightSource(Vector3Int globalLocation, byte brightness, boolean placeLight) {
        if (!getSettings().getLightsEnabled()) {
            System.out.println("AddLightSource called with lights disabled");
            return;
        }
        if (brightness <= 0) {
            return;
        }

        BlockChunkControl chunk = getChunkByBlockLocation(globalLocation);
        if (chunk == null) {
            return;
        }
        Vector3Int localBlockLocation = getLocalBlockLocation(globalLocation, chunk);
        if (placeLight) {
            if (!chunk.addLightSource(localBlockLocation, brightness)) {
                return;
            }
        }

        // if the light source is brighter than the light currently at this spot
        if (chunk.propigateLight(localBlockLocation, brightness) || !placeLight) {
            brightness--;
            Queue<Vector3Int> locationsToPropigateTo = new LinkedList<Vector3Int>();
            Queue<Vector3Int> next = new LinkedList<Vector3Int>();
            for(int face = 0; face < Block.Face.values().length; face++){
                Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(globalLocation, Block.Face.values()[face]);
                locationsToPropigateTo.add(neighborLocation);
            }
            while (locationsToPropigateTo.size() > 0 && brightness > 0) {
                Vector3Int location = locationsToPropigateTo.remove();
                chunk = getChunkByBlockLocation(location);
                if (chunk != null) {
                    localBlockLocation = getLocalBlockLocation(location, chunk);
                    if (chunk.propigateLight(localBlockLocation, brightness)) {
                        for(int face = 0; face < Block.Face.values().length; face++){
                            Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, Block.Face.values()[face]);
                            next.add(neighborLocation);
                        }
                    }
                }
                if (locationsToPropigateTo.size() == 0) {
                    locationsToPropigateTo = next;
                    next = new LinkedList<Vector3Int>();
                    brightness--;
                }
            }
        }
    }


    // TODO: Optimize this so it doesn't have to propigate as far
    // if it sees an incline in light level, it could stop propigatind darkness there
    // and propigate that light level instead.
    //public static boolean debugLogs = false;
    public void removeLightSource(Vector3Int globalLocation) {
        if (!getSettings().getLightsEnabled()) {
            System.out.println("AddLightSource called with lights disabled");
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
        //boolean debugLogs = this.debugLogs;
        while (locationsToPropigateTo.size() > 0 && oldLight >= -1) {
            Vector3Int location = locationsToPropigateTo.remove();
            chunk = getChunkByBlockLocation(location);
            if (chunk != null) {
                //if (debugLogs) {
                //    debugLogs = true;
                //    System.out.println("removeLightSource while loop on " + location.toString());
                //}
                localBlockLocation = getLocalBlockLocation(location, chunk);
                if (chunk.getLightSourceAt(localBlockLocation) > 0) {
                    //if (debugLogs) {
                    //    System.out.println("source light > 0 " + location.toString());
                    //    System.out.println("add to lights to replace with " + location.toString() + " light:" + chunk.getLightSourceAt(localBlockLocation));
                    //}                        
                    lightsToReplace.put(location, new LightQueueElement(localBlockLocation,chunk,chunk.getLightSourceAt(localBlockLocation),false));
                } else if (chunk.propigateDark(localBlockLocation, oldLight)) {
                    //if (debugLogs) {
                    //    System.out.println("adding faces > 0 " + location.toString());
                    //}
                    for(int face = 0; face < Block.Face.values().length; face++){
                        Vector3Int neighborLocation = BlockNavigator.getNeighborBlockLocalLocation(location, Block.Face.values()[face]);
                        next.add(neighborLocation);
                    }
                } else {
                    //if (debugLogs) {
                    //    System.out.println("else " + location.toString());
                    //}
                    byte light = chunk.getLightAt(localBlockLocation);
                    if(light > 0) {
                        //if (debugLogs) {
                        //    System.out.println("light at > 0 " + location.toString());
                        //    System.out.println("add to lights to replace with " + location.toString() + " light:" + light);
                        //}
                        lightsToReplace.put(location, new LightQueueElement(localBlockLocation,chunk,light,false));
                    }
                }
                if (locationsToPropigateTo.size() == 0) {
                    //if (debugLogs) {
                    //    System.out.println("next cycle");
                    //}
                    locationsToPropigateTo = next;
                    next = new LinkedList<Vector3Int>();
                    oldLight--;
                }
            }
        }
        addLightSource(lightsToReplace);
    }
    
    public BlockChunkControl getChunkByBlockLocation(Vector3Int blockLocation){
        Vector3Int chunkLocation = getChunkLocation(blockLocation);
        return getChunkByChunkLocation(chunkLocation);
    }

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

    /*
    public Vector3Int getLocalBlockLocation(Vector3Int blockLocation){
        Vector3Int localLocation = new Vector3Int();
        int localX = (blockLocation.getX() % settings.getChunkSizeX());
        int localY = (blockLocation.getY() % settings.getChunkSizeY());
        int localZ = (blockLocation.getZ() % settings.getChunkSizeZ());
        localLocation.set(localX, localY, localZ);
        return localLocation;
    }
    */
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
    public static Vector3Int getLocalBlockLocation(Vector3Int globalBlockLocation, BlockChunkControl chunk){
        Vector3Int localLocation = new Vector3Int();
        int localX = (globalBlockLocation.getX() - chunk.getBlockLocation().getX());
        int localY = (globalBlockLocation.getY() - chunk.getBlockLocation().getY());
        int localZ = (globalBlockLocation.getZ() - chunk.getBlockLocation().getZ());
        /*if (chunk.getBlockLocation().getX() < 0) {
            localX += chunk.getTerrain().getSettings().getChunkSizeX();
        }
        if (chunk.getBlockLocation().getY() < 0) {
            localY += chunk.getTerrain().getSettings().getChunkSizeY();
        }
        if (chunk.getBlockLocation().getZ() < 0) {
            localZ += chunk.getTerrain().getSettings().getChunkSizeZ();
        }*/
        localLocation.set(localX, localY, localZ);
        return localLocation;
    }
    
    public boolean updateSpatial(){

        boolean wasUpdateNeeded = false;
        boolean hasLock = false;
        try {
            hasLock = chunkAccessMutex.tryLock(10L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            System.err.println("updateSpatial failed to tryLock");
        }
        if (hasLock) {
            try {
                // TODO someday, not all updates will require spatial chanes
                // like a texture changing or light propigating.
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
                /*for (String chunkLocation :  chunks.keySet()) {
                    BlockChunkControl chunk = chunks.get(chunkLocation);
                    if(chunk.updateSpatial()){
                        wasUpdateNeeded = true;
                        for(int i=0;i<chunkListeners.size();i++){
                            BlockChunkListener blockTerrainListener = chunkListeners.get(i);
                            blockTerrainListener.onSpatialUpdated(chunk);
                        }
                        // Only update one spatial per frame.
                        break;
                    }

                }*/
            } finally {
                chunkAccessMutex.unlock();
            }
        }
        return wasUpdateNeeded;
    }
    
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
    
    public void addChunkListener(BlockChunkListener blockChunkListener){
        chunkListeners.add(blockChunkListener);
    }
    
    public void removeChunkListener(BlockChunkListener blockChunkListener){
        chunkListeners.remove(blockChunkListener);
    }
    
    public CubesSettings getSettings(){
        return settings;
    }

    public HashMap<Vector3Int, BlockChunkControl> getChunks(){
        return chunks;
    }
    
    //Tools
    
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

    @Override
    public BlockTerrainControl clone(){
        BlockTerrainControl blockTerrain = new BlockTerrainControl(settings, new Vector3Int());
        blockTerrain.setBlocksFromTerrain(this);
        return blockTerrain;
    }
    
    public void setBlocksFromTerrain(BlockTerrainControl blockTerrain){
        CubesSerializer.readFromBytes(this, CubesSerializer.writeToBytes(blockTerrain));
    }

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
                bitOutputStream.writeInteger(i); // Virticle slice of chunk
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
    private void markChunkNeedsUpdate(Vector3Int chunkLocation, boolean needsPhysicsUpdate) {
        BlockChunkControl chunk = this.getChunkByChunkLocation(chunkLocation);
        if (chunk != null) {
            chunk.markNeedUpdate(needsPhysicsUpdate);
        }
    }
    public void addChunkToNeedsUpdateList(BlockChunkControl chunk) {
        if (!chunksThatNeedUpdate.contains(chunk)) {
            chunksThatNeedUpdate.add(chunk);
        }
    }
    public boolean getChunksInProgress() {
        return !chunksInProgres.isEmpty();
    }
    private HashMap<Vector3Int, BlockChunkControl> chunksInProgres = new HashMap<Vector3Int, BlockChunkControl>();
    public boolean readChunkPartial(BitInputStream inputStream) throws IOException{
        int chunkX = inputStream.readInteger();
        int chunkY = inputStream.readInteger();
        int chunkZ = inputStream.readInteger();
        int chunkSlice = inputStream.readInteger();
        boolean lastSlice = inputStream.readBoolean();
        //System.out.println("ReadChunkPartial " + chunkX + "," + chunkY + "," + chunkZ + ",s" + chunkSlice);
        Vector3Int chunkLocation;
        chunkLocation = new Vector3Int(chunkX, chunkY, chunkZ);
        if (!chunksInProgres.containsKey(chunkLocation)) {
            setChunkStarted(chunkLocation);
        }
        
        //initializeChunk(chunkLocation);
        BlockChunkControl chunk = chunksInProgres.get(chunkLocation);
        //long startTime = Calendar.getInstance().getTimeInMillis();
        //long endTime;
        chunk.read(chunkSlice, inputStream);
        if (lastSlice) {
            chunkAccessMutex.lock();
            try {
                chunk.setLocation(this, chunkLocation);
                //chunk.updateLights();
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
                    //System.out.println("Creating sunlight chunk " + sunlightKey);
                    SunlightChunk sl = new SunlightChunk(this, settings, chunkLocation);
                    sunlight.put(sunlightKey, sl);
                } else {
                    //System.out.println("Reusing sunlight chunk " + sunlightKey);
                }
                chunk.setSunlight(sunlight.get(sunlightKey));
                chunk.addSunlights();
                //System.out.println("adding to finished " + chunkKey);
                finishedChunks.add(chunkLocation);
            } finally {
                chunkAccessMutex.unlock();
            }
            //chunksInProgres.remove(chunkKey);
        }
        //endTime = Calendar.getInstance().getTimeInMillis();
        //if (endTime - startTime > 2) {
        //    System.err.println("chunk read took " + (endTime - startTime) + "ms");
        //}
        return lastSlice;
    }
    private ArrayList<Vector3Int> finishedChunks = new ArrayList<Vector3Int>();
    public void finishChunks() {
        if(this.finishedChunks.size() > 0) {
            chunkAccessMutex.lock();
            try {
                if(this.finishedChunks.size() > 0) {
                    Vector3Int chunkKey = finishedChunks.get(0);
                    //System.out.println("Finishing chunk " + chunkKey);
                    finishedChunks.remove(0);
                    BlockChunkControl chunk = chunksInProgres.get(chunkKey);
                    if (chunk != null) {
                        this.chunks.put(chunkKey, chunk);
                        this.addChunkToSpatial(chunk);
                        chunksInProgres.remove(chunkKey);
                    } else {
                        //System.out.println("Chunk missing " + chunkKey);
                    }
                }

                } finally {
                chunkAccessMutex.unlock();
            }
        }
    }
    public void readChunkPartial(byte data[]) {
         BitInputStream bitInputStream = new BitInputStream(new ByteArrayInputStream(data));
         try {
             this.readChunkPartial(bitInputStream);
         } catch(IOException ex){
             ex.printStackTrace();
         }
    }

    byte getLightLevelOfBlock(Vector3Int globalLocation) {
        BlockChunkControl chunk = getChunkByBlockLocation(globalLocation);
        if (chunk == null) {
            return 1;
        }
        Vector3Int localBlockLocation = getLocalBlockLocation(globalLocation, chunk);
        return chunk.getLightAt(localBlockLocation);
    }
    
    byte getLightSourceOfBlock(Vector3Int globalLocation) {
        BlockChunkControl chunk = getChunkByBlockLocation(globalLocation);
        if (chunk == null) {
            return 1;
        }
        Vector3Int localBlockLocation = getLocalBlockLocation(globalLocation, chunk);
        return chunk.getLightSourceAt(localBlockLocation);
    }

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
