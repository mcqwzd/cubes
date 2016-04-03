/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import com.jme3.math.Vector3f;

/**
 *
 * @author Carl
 */
public class BlockNavigator{
    
    // Get neighbor block location using coordinates local to the chunk.
    // WARNING: May return a block location outside the chunk.
    public static Vector3Int getNeighborBlockLocalLocation(Vector3Int location, Block.Face face){
        Vector3Int neighborLocation = getNeighborBlockLocation_Relative(face);
        neighborLocation.addLocal(location);
        return neighborLocation;
    }
    
    // Get location delta by direction
    public static Vector3Int getNeighborBlockLocation_Relative(Block.Face face){
        Vector3Int neighborLocation = new Vector3Int();
        switch(face){
            case Top:
                neighborLocation.set(0, 1, 0);
                break;
            
            case Bottom:
                neighborLocation.set(0, -1, 0);
                break;
            
            case Left:
                neighborLocation.set(-1, 0, 0);
                break;
            
            case Right:
                neighborLocation.set(1, 0, 0);
                break;
            
            case Front:
                neighborLocation.set(0, 0, 1);
                break;
            
            case Back:
                neighborLocation.set(0, 0, -1);
                break;
        }
        return neighborLocation;
    }
    
    // Reverse a direction
    public static Block.Face getOppositeFace(Block.Face face){
        switch(face){
            case Top:       return Block.Face.Bottom;
            case Bottom:    return Block.Face.Top;
            case Left:      return Block.Face.Right;
            case Right:     return Block.Face.Left;
            case Front:     return Block.Face.Back;
            case Back:      return Block.Face.Front;
        }
        return null;
    }
    
    // Get block at collision point, or neighboring block at collision point
    // Used to determine which block to remove when picking
    // Or where to place a block
    // Does not check block state empty/clear.
    // TODO test with more block types
    public static Vector3Int getPointedBlockLocation(BlockTerrainControl blockTerrain, Vector3f collisionContactPoint, boolean getNeighborLocation, Vector3f collisionNorm){
        Vector3f collisionLocation = Util.compensateFloatRoundingErrors(collisionContactPoint);
        double blockSize = (double)blockTerrain.getSettings().getBlockSize();
        Vector3Int blockLocation = new Vector3Int(
                (int) (collisionLocation.getX() / blockSize),
                (int) (collisionLocation.getY() / blockSize),
                (int) (collisionLocation.getZ() / blockSize));
        int xAdjust = 1;
        int yAdjust = 1;
        int zAdjust = 1;
        // Adjust for negitive cordinates
        if (collisionLocation.getX() < 0) {
            blockLocation.setX(blockLocation.getX() - 1);
            collisionNorm.x *= -1;
            xAdjust = -1;
        }
        if (collisionLocation.getY() < 0) {
            blockLocation.setY(blockLocation.getY() - 1);
            collisionNorm.y *= -1;
            yAdjust = -1;
        }
        if (collisionLocation.getZ() < 0) {
            blockLocation.setZ(blockLocation.getZ() - 1);
            collisionNorm.z *= -1;
            zAdjust = -1;
        }

        // if a collisionNorm is provided, and it only has 1 vector
        // then use that vector to find neighbor. (works for 'square' blocks)
        if (collisionNorm != null) {
            int nonZeroCount = 0;
            if (collisionNorm.x != 0) nonZeroCount++;
            if (collisionNorm.y != 0) nonZeroCount++;
            if (collisionNorm.z != 0) nonZeroCount++;
            if (nonZeroCount == 1) {
                if (getNeighborLocation) {
                     if (collisionNorm.x < 0) {
                        return blockLocation.subtract(xAdjust,0,0);
                    }
                    if (collisionNorm.y < 0) {
                        return blockLocation.subtract(0,yAdjust,0);
                    }
                    if (collisionNorm.z < 0) {
                        return blockLocation.subtract(0,0,zAdjust);
                    }
                    return blockLocation;
                } else {
                    if (collisionNorm.x > 0) {
                        return blockLocation.subtract(xAdjust,0,0);
                    }
                    if (collisionNorm.y > 0) {
                        return blockLocation.subtract(0,yAdjust,0);
                    }
                    if (collisionNorm.z > 0) {
                        return blockLocation.subtract(0,0,zAdjust);
                    }
                    return blockLocation;
                }
            }
        }
        // else, find the closest edge.
        if((blockTerrain.getBlock(blockLocation) != null) == getNeighborLocation){
            float modX = Math.abs((float)(collisionLocation.getX() % blockSize));
            float modY = Math.abs((float)(collisionLocation.getY() % blockSize));
            float modZ = Math.abs((float)(collisionLocation.getZ() % blockSize));
            float mmodX = Math.min(modX, (float)(blockSize - modX));
            float mmodY = Math.min(modY,(float)( blockSize - modY));
            float mmodZ = Math.min(modZ, (float)(blockSize - modZ));
            float minMod = Math.min(mmodX, Math.min(mmodY, mmodZ));
            if( mmodX == minMod) {
                if(mmodX == modX) {
                    blockLocation.subtractLocal(xAdjust, 0, 0);
                } else {
                    blockLocation.subtractLocal(-1 * xAdjust, 0, 0);
                }
            }
            else if( mmodY == minMod) {
                if(mmodY == modY) {
                    blockLocation.subtractLocal(0, yAdjust, 0);
                } else {
                    blockLocation.subtractLocal(0, -1 * yAdjust, 0);
                }
            }
            else if( mmodZ == minMod) {
                if(mmodZ == modZ) {
                    blockLocation.subtractLocal(0, 0, zAdjust);
                } else {
                    blockLocation.subtractLocal(0, 0, -1 * zAdjust);
                }
            }
        }
        return blockLocation;
    }
}
