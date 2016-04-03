/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

/**
 *
 * @author BigScorch (github/funinvegas)
 * Class for queueing light updates so they can be calculated at a more convienient time.
 */
public class LightQueueElement {
    private Vector3Int location;
    private byte level;
    private BlockChunkControl chunk;
    private boolean placeLight;
    public LightQueueElement(Vector3Int lightLocation, BlockChunkControl chunkControl, byte lightLevel, boolean putLight) {
        this.location = lightLocation;
        this.level = lightLevel;
        this.chunk = chunkControl;
        this.placeLight = putLight;
    }
    public LightQueueElement(Vector3Int lightLocation, BlockChunkControl chunkControl, byte lightLevel) {
        this(lightLocation, chunkControl, lightLevel, true);
    }
    public LightQueueElement(Vector3Int lightLocation, BlockChunkControl chunkControl) {
        this(lightLocation, chunkControl, (byte)0, true);
    }
    public Vector3Int getLocation() {
        return this.location;
    }
    public byte getLevel() {
        return this.level;
    }
    public BlockChunkControl getChunk() {
        return this.chunk;
    }
    public boolean getPlaceLight() {
        return this.placeLight;
    }
}
