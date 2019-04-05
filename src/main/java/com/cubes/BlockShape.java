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

    // Enum to relate block to neighboring blocks
    protected enum NeighborRelation {
        empty,
        different,
        identical
    }
    // Compare block in one direction
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face1) {
        // TODO: dispose temp block location created here
        return getNeighborRelation(chunk, BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face1));
    }
    // Compare block in two directions (diagnol line edge)
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face1, Block.Face face2) {
        // TODO: dispose temp block location created here
        return getNeighborRelation(chunk, BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face2), face1);
    }
    // Compare block in three directions (corner point edge)
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face1, Block.Face face2, Block.Face face3) {
        // TODO: dispose temp block location created here
       return getNeighborRelation(chunk, BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face3), face1, face2);
    }
    // Compare block by location
    protected NeighborRelation getNeighborRelation(BlockChunkControl chunk, Vector3Int blockLocation) {
         Block neighborBlock = chunk.getBlock(blockLocation);
        if (neighborBlock == null) {
            Vector3Int neighborGlobalBlock = chunk.getBlockGlobalLocation(blockLocation);
            neighborBlock = chunk.getTerrain().getBlock(neighborGlobalBlock);
        }
        if(neighborBlock == null) {
            return NeighborRelation.empty;
        }
        if (neighborBlock.getShape(chunk, blockLocation).getTypeName().equals(this.getTypeName())) {
            return NeighborRelation.identical;
        }
        return NeighborRelation.different;
    }

    // TODO: is this related to letting light pass or is this related to letting the player pass through?
    private boolean isTransparent;
    
    // Mesh generation
    protected LowAllocArray.Vector3fArray positions;
    // Mesh generation
    protected LowAllocArray.ShortArray indices;
    // Mesh generation
    protected LowAllocArray.FloatArray normals;
    // Mesh generation
    protected LowAllocArray.Vector2fArray textureCoordinates;
    // Mesh generation
    protected LowAllocArray.FloatArray lightColors;
    
    // Initialize for generating mesh
    public void prepare(boolean isTransparent, 
            LowAllocArray.Vector3fArray positions,  
            LowAllocArray.ShortArray  indices, 
            LowAllocArray.FloatArray normals, 
            LowAllocArray.Vector2fArray textureCoordinates, 
            LowAllocArray.FloatArray lightColors){
        this.positions = positions;
        this.indices = indices;
        this.normals = normals;
        this.textureCoordinates = textureCoordinates;
        this.lightColors = lightColors;
        this.isTransparent = isTransparent;
    }
    void reset() {
        this.positions = null;
        this.indices = null;
        this.normals = null;
        this.textureCoordinates = null;
        this.lightColors = null;
    }

    
    // Used to identify this shape for comparing to other shapes
    public abstract String getTypeName();
    
    // Add shape to chunk mesh
    public abstract void addTo(BlockChunkControl chunk, Vector3Int blockLocation);
    
    // Combined logic for neighboring block covering face and transparency.
    // TODO: understand this better.
    protected boolean shouldFaceBeAdded(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face){
        Block block = chunk.getBlock(blockLocation);
        BlockSkin blockSkin = block.getSkin(chunk, blockLocation, face);
        if(blockSkin.isTransparent() == isTransparent){
            Vector3Int neighborBlockLocation = BlockNavigator.getNeighborBlockLocalLocation(blockLocation, face);
            Block neighborBlock = chunk.getBlock(neighborBlockLocation);
            if (neighborBlock == null) {
                Vector3Int neighborGlobalBlock = chunk.getNeighborBlockGlobalLocation(blockLocation, face);
                neighborBlock = chunk.getTerrain().getBlock(neighborGlobalBlock);
                Vector3Int.dispose(neighborGlobalBlock);
            }
            if(neighborBlock != null){
                BlockSkin neighborBlockSkin = neighborBlock.getSkin(chunk, blockLocation, face);
                if(blockSkin.isTransparent() != neighborBlockSkin.isTransparent()){
                    return true;
                }
                BlockShape neighborShape = neighborBlock.getShape(chunk, neighborBlockLocation);
                Vector3Int.dispose(neighborBlockLocation);
                return (!(canBeMerged(face) && neighborShape.canBeMerged(BlockNavigator.getOppositeFace(face))));
            }
            Vector3Int.dispose(neighborBlockLocation);
            return true;
        }
        return false;
    }
    
    // Return true if the face is exposed to the sky
    // TODO: does this only apply to face up? if so it could be more efficent
    protected boolean isFaceAboveSurface(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face) {
        Vector3Int neighborBlock = chunk.getNeighborBlockGlobalLocation(blockLocation, face);
        BlockTerrainControl terrain = chunk.getTerrain();
        return terrain.getIsGlobalLocationAboveSurface(neighborBlock);
    }
    
    // Get the brightness of a face direction by getting the light level of the neighboring block
    protected float getLightLevelOfFace(BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face) {
        BlockTerrainControl terrain = chunk.getTerrain();
        boolean lightEnabled = terrain.getSettings().getLightsEnabled();
        // If lights are not enabled, just light everything to max
        if (!lightEnabled) {
            return 1.0f;
        }
        
        // Get block in direction of face
        Vector3Int neighborBlock = chunk.getNeighborBlockGlobalLocation(blockLocation, face);
        // scale light depending on how strong the sun is configured to be.  This is 'max' light.
        byte sunlight = terrain.getSettings().getSunlightLevel();

        // Get how bright a given block is.
        byte lightLevel = terrain.getLightLevelOfBlock(neighborBlock);
        
        // Negitive lights are black.  This is never done intentionally.
        if (lightLevel < 0) {
            return 0;
        }
        
        // Some pre-defined light scales
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
                 case 0: return 0.24f;
                 case 1: return 0.36f;
                 case 2: return 0.44f;
                 case 3: return 0.52f;
                 case 4: return 0.60f;
                 case 5: return 0.68f;
                 case 6: return 0.76f;
                 case 7: return 0.84f;
                 case 8: return 0.92f;
                 case 9: return 1f;
                 default: return 1f;
             }
        } else {
            // If not a pre-defined light, then make an aproximiation.
            // TODO: Find a better algorithm that doesn't need the predefined sets.
            float lightFactor = ((float)lightLevel+1) / ((float)sunlight + 1);
            return lightFactor;
        }
    }

    // Does the neighboring block cover the entire face of its square in the oposite direction?
    // If this returns true, the current block may choose not to render the surface in this direction.
    protected abstract boolean canBeMerged(Block.Face face);
    
    // Translate texture cordinates to be used on mesh
    protected Vector2f getTextureCoordinates(BlockChunkControl chunk, BlockSkin_TextureLocation textureLocation, float xUnitsToAdd, float yUnitsToAdd){
        float textureUnitX = (1f / chunk.getTerrain().getSettings().getTexturesCountX());
        float textureUnitY = (1f / chunk.getTerrain().getSettings().getTexturesCountY());
        float x = (((textureLocation.getColumn() + xUnitsToAdd) * textureUnitX));
        float y = ((((-1 * textureLocation.getRow()) + (yUnitsToAdd - 1)) * textureUnitY) + 1);
        return new Vector2f(x, y);
    }
}
