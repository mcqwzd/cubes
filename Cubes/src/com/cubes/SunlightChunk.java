/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import com.jme3.terrain.Terrain;

/**
 *
 * @author funin_000
 */
public class SunlightChunk {
    private int lowestBlockWithSunlight = Integer.MAX_VALUE;
    private int surfaceBlockElevation[][];
    private final CubesSettings settings;
    private final BlockTerrainControl terrain; 
    private Vector3Int chunkLocation;
    SunlightChunk(BlockTerrainControl terrain, CubesSettings settings, Vector3Int chunkLocation) {
        this.terrain = terrain;
        this.chunkLocation = chunkLocation;
        this.settings = settings;
        int cX = settings.getChunkSizeX();
        int cZ = settings.getChunkSizeZ();
        this.surfaceBlockElevation = new int[cX][cZ];
        for(int iX = 0; iX < cX; ++iX) {
            for(int iZ = 0; iZ < cZ; ++iZ) {
                surfaceBlockElevation[iX][iZ] = -1 * Integer.MAX_VALUE;
            }
        }
    }
    int getGlobalXFromLocalX(int chunkX, int localBlockX) {
        return (settings.getChunkSizeX() * chunkX) + localBlockX;
    }
    int getGlobalYFromLocalY(int chunkY, int localBlockY) {
        return (settings.getChunkSizeY() * chunkY) + localBlockY;
    }
    int getGlobalZFromLocalZ(int chunkZ, int localBlockZ) {
        return (settings.getChunkSizeZ() * chunkZ) + localBlockZ;
    }
    boolean isBlockAtOrAboveSurface(int chunkY, Vector3Int blockLocation) {
        return getGlobalYFromLocalY(chunkY, blockLocation.getY()) >= surfaceBlockElevation[blockLocation.getX()][blockLocation.getZ()]; 
    }

    int setSolidBlock(int chunkX, int chunkY, int chunkZ, int iX, int iY, int iZ, boolean updateDelta) {
        iY = getGlobalYFromLocalY(chunkY, iY);
        if (lowestBlockWithSunlight != Integer.MAX_VALUE && lowestBlockWithSunlight > iY) {
            return 0;
        }
        int oldElevation = surfaceBlockElevation[iX][iZ];
        if (iY > oldElevation || oldElevation == Integer.MAX_VALUE) {
            surfaceBlockElevation[iX][iZ] = iY; 
            lowestBlockWithSunlight = Integer.MAX_VALUE;
            if (updateDelta) {
                terrain.updateSunlightFromTo(getGlobalXFromLocalX(chunkX, iX), getGlobalZFromLocalZ(chunkZ, iZ), oldElevation, iY);
            }
            //if (lowestBlockWithSunlight == oldElevation) {
                // we no longer know what the lowest block is.
            //}
            return oldElevation;
        }
        return 0;
    }

    boolean isChunkUnderground(BlockChunkControl chunk) {
        int chunkBottom = chunk.getBlockLocation().getY();
        int chunkTop = chunkBottom + settings.getChunkSizeY();
        if (lowestBlockWithSunlight == Integer.MAX_VALUE) {
            for (int iX = 0; iX < surfaceBlockElevation.length; ++iX) {
                for (int iZ = 0; iZ < surfaceBlockElevation[0].length; ++iZ) {
                    if (surfaceBlockElevation[iX][iZ] < lowestBlockWithSunlight) {
                        lowestBlockWithSunlight = surfaceBlockElevation[iX][iZ];
                    }
                }
            }
        }
        return chunkTop < lowestBlockWithSunlight;
    }

    void setOpenBlock(int chunkX, int chunkY, int chunkZ, int x, int y, int z, boolean updateDelta) {
        int globalY = getGlobalYFromLocalY(chunkY, y);
        if (globalY == surfaceBlockElevation[x][z]) {
            surfaceBlockElevation[x][z] = terrain.findNextBlockDown(chunkLocation.getX(), chunkLocation.getZ(), x, z, globalY);
            if (updateDelta) {
                terrain.updateSunlightFromTo(getGlobalXFromLocalX(chunkX, x), getGlobalZFromLocalZ(chunkZ, z), surfaceBlockElevation[x][z], globalY);
            }
        }
    }

    boolean isBlockAtSurface(int chunkY, Vector3Int blockLocation) {
        return getGlobalYFromLocalY(chunkY, blockLocation.getY()) == surfaceBlockElevation[blockLocation.getX()][blockLocation.getZ()]; 
    }

    boolean isBlockAboveSurface(int chunkY, Vector3Int blockLocation) {
        return getGlobalYFromLocalY(chunkY, blockLocation.getY()) > surfaceBlockElevation[blockLocation.getX()][blockLocation.getZ()]; 
    }

}
