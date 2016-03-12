/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import static com.cubes.BlockTerrainControl.getLocalBlockLocation;
import java.util.List;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.Terrain;

/**
 *
 * @author Carl
 */
public abstract class BlockShape{
    protected enum NeighborRelation {
        empty,
        different,
        identical
    }
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face1) {
        return getNeighborRelation(chunk, BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face1));
    }
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face1, Block.Face face2) {
        return getNeighborRelation(chunk, BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face2), face1);
    }
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face1, Block.Face face2, Block.Face face3) {
       return getNeighborRelation(chunk, BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face3), face1, face2);
    }
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation) {
         Block neighborBlock = chunk.getBlock(blockLocation);
        if (neighborBlock == null) {
            Vector3Int neighborGlobalBlock = chunk.getBlockGlobalLocation(blockLocation);
            neighborBlock = chunk.getTerrain().getBlock(neighborGlobalBlock);
        }
        if(neighborBlock == null) {
            return NeighborRelation.empty;
        }
        if (neighborBlock.getClass().equals(this.getClass())) {
            return NeighborRelation.identical;
        }
        return NeighborRelation.different;
    }

    private boolean isTransparent;
    protected List<Vector3f> positions;
    protected List<Short> indices;
    protected List<Float> normals;
    protected List<Vector2f> textureCoordinates;
    protected List<Float> lightColors;
    
    public void prepare(boolean isTransparent, List<Vector3f> positions, List<Short> indices, List<Float> normals, List<Vector2f> textureCoordinates, List<Float> lightColors){
        this.positions = positions;
        this.indices = indices;
        this.normals = normals;
        this.textureCoordinates = textureCoordinates;
        this.lightColors = lightColors;
        this.isTransparent = isTransparent;
    }
    public abstract String getTypeName();
    public abstract void addTo(BlockChunkControl chunk, Vector3Int blockLocation);
    
    protected boolean shouldFaceBeAdded(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face){
        Block block = chunk.getBlock(blockLocation);
        BlockSkin blockSkin = block.getSkin(chunk, blockLocation, face);
        if(blockSkin.isTransparent() == isTransparent){
            Vector3Int neighborBlockLocation = BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face);
            Block neighborBlock = chunk.getBlock(neighborBlockLocation);
            if (neighborBlock == null) {
                Vector3Int neighborGlobalBlock = chunk.getNeighborBlockGlobalLocation(blockLocation, face);
                neighborBlock = chunk.getTerrain().getBlock(neighborGlobalBlock);
            }
            if(neighborBlock != null){
//                if (!neighborBlock.getClass().equals(this.getClass())) {
//                    return true;
//                }
                BlockSkin neighborBlockSkin = neighborBlock.getSkin(chunk, blockLocation, face);
                if(blockSkin.isTransparent() != neighborBlockSkin.isTransparent()){
                    return true;
                }
                BlockShape neighborShape = neighborBlock.getShape(chunk, neighborBlockLocation);
                return (!(canBeMerged(face) && neighborShape.canBeMerged(BlockNavigator.getOppositeFace(face))));
            }
            else {
            }
            return true;
        }
        return false;
    }
    
    protected boolean isFaceAboveSurface(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face) {
        Vector3Int neighborBlock = chunk.getNeighborBlockGlobalLocation(blockLocation, face);
        BlockTerrainControl terrain = chunk.getTerrain();
        return terrain.getIsGlobalLocationAboveSurface(neighborBlock);
    }
    
    protected float getLightLevelOfFace(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face) {
        BlockTerrainControl terrain = chunk.getTerrain();
        boolean lightEnabled = terrain.getSettings().getLightsEnabled();
        if (!lightEnabled) {
            return 1.0f;
        }
        Vector3Int neighborBlock = chunk.getNeighborBlockGlobalLocation(blockLocation, face);
        byte sunlight = terrain.getSettings().getSunlightLevel();
        
        byte lightLevel = terrain.getLightLevelOfBlock(neighborBlock);
        byte maxLight = terrain.getSettings().getSunlightLevel();
        if (lightLevel < 0) {
            return 0;
        }
        if (sunlight == 3) {
            switch (lightLevel) {
                case 0: return 0.25f;
                case 1: return 0.5f;
                case 2: return 0.75f;
                case 3: return 1f;
                default: return 1f;
            }
        } else if (sunlight == 9) {
            switch (lightLevel) {
                 case 0: return 0.1f;
                 case 1: return 0.15f;
                 case 2: return 0.2f;
                 case 3: return 0.25f;
                 case 4: return 0.3f;
                 case 5: return 0.4f;
                 case 6: return 0.5f;
                 case 7: return 0.6f;
                 case 8: return 0.8f;
                 case 9: return 1f;
                 default: return 1f;
             }
        } else {
            float lightFactor = ((float)lightLevel+1) / ((float)maxLight + 1);
            return lightFactor;
        }


    }

    
    protected abstract boolean canBeMerged(Block.Face face);
    
    protected Vector2f getTextureCoordinates(BlockChunkControl chunk, BlockSkin_TextureLocation textureLocation, float xUnitsToAdd, float yUnitsToAdd){
        float textureUnitX = (1f / chunk.getTerrain().getSettings().getTexturesCountX());
        float textureUnitY = (1f / chunk.getTerrain().getSettings().getTexturesCountY());
        float x = (((textureLocation.getColumn() + xUnitsToAdd) * textureUnitX));
        float y = ((((-1 * textureLocation.getRow()) + (yUnitsToAdd - 1)) * textureUnitY) + 1);
        return new Vector2f(x, y);
    }
}
