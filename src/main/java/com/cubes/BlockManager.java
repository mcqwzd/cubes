/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import java.util.HashMap;

/**
 *
 * @author Carl
 */
public class BlockManager{
    
    private static HashMap<Block, Byte> BLOCK_TYPES = new HashMap<Block, Byte>();
    private static Block[] TYPES_BLOCKS = new Block[256];
    private static byte nextBlockType = 1;
    
    // Register a block type (and thus give it a enum value)
    public static void register(Block block){
        BLOCK_TYPES.put(block, nextBlockType);
        TYPES_BLOCKS[nextBlockType] = block;
        nextBlockType++;
    }
    
    // Get enume value for given type
    public static byte getType(Block block){
        return BLOCK_TYPES.get(block);
    }
    
    // Get block type for given enum value
    public static Block getBlock(byte type){
        return TYPES_BLOCKS[type];
    }
}
