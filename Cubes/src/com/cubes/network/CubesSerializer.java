/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.cubes.*;
import java.util.HashMap;

/**
 *
 * @author Carl
 */
public class CubesSerializer{
    
    public static HashMap<Vector3Int, byte[]> writeChunksToBytes(BlockTerrainControl blockTerrain){
        HashMap<Vector3Int, BlockChunkControl> chunks = blockTerrain.getChunks();
        HashMap<Vector3Int, byte[]> bytes = new HashMap<Vector3Int, byte[]>();
        for (Vector3Int chunkLocation :  chunks.keySet()) {
            BlockChunkControl chunk = chunks.get(chunkLocation);
            bytes.put(chunkLocation, writeToBytes(chunk));
        }
        return bytes;
    }
    
    public static byte[] writeToBytes(BitSerializable bitSerializable){
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        BitOutputStream bitOutputStream = new BitOutputStream(byteArrayOutputStream);
        bitSerializable.write(bitOutputStream);
        bitOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }
    
    public static void readFromBytes(BitSerializable bitSerializable, byte[] bytes){
        BitInputStream bitInputStream = new BitInputStream(new ByteArrayInputStream(bytes));
        try{
            bitSerializable.read(bitInputStream);
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }
}
