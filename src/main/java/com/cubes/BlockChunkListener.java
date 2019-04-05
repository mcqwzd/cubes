/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

/**
 *
 * @author Carl
 */
public interface BlockChunkListener{
    // The shape of a chunk has changed (including from nothing to something)
    public abstract void onSpatialUpdated(BlockChunkControl blockChunk);

    // A chunk has been removed from the terrain
    public abstract void onSpatialRemoved(BlockChunkControl blockChunk);
}
