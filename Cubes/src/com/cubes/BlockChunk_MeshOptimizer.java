/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

/**
 *
 * @author Carl
 */
public class BlockChunk_MeshOptimizer{

    private static Vector3f[] positions;
    private static short[] indices;
    private static Vector2f[] textureCoordinates;
    private static float[] normals;
    private static float[] lightColors;

    //LinkedList<Vector3f> positionsList = new LinkedList<Vector3f>();
    private static LowAllocArray.Vector3fArray positionsList = new LowAllocArray.Vector3fArray();
    //LinkedList<Short> indicesList = new LinkedList<Short>();
    private static LowAllocArray.ShortArray indicesList = new LowAllocArray.ShortArray(Short.MAX_VALUE * 3);
    //LinkedList<Float> normalsList = new LinkedList<Float>();
    private static LowAllocArray.FloatArray normalsList = new LowAllocArray.FloatArray(Short.MAX_VALUE * 3);
    //LinkedList<Vector2f> textureCoordinatesList = new LinkedList<Vector2f>();
    private static LowAllocArray.Vector2fArray textureCoordinatesList = new LowAllocArray.Vector2fArray();
    //LinkedList<Float> lightColorsList = new LinkedList<Float>();
    private static LowAllocArray.FloatArray lightColorsList = new LowAllocArray.FloatArray(Short.MAX_VALUE * 4);

    // Generate a single mesh of every block in a given chunk
    // Using neighbor relations to remove faces that could never be seen
    // the 'lightColors' is used to give the impression of light using shades of grey to imply darkness
    // 
    public static Mesh generateOptimizedMesh(BlockChunkControl blockChunk, boolean isTransparent){
        reset();
        BlockTerrainControl blockTerrain = blockChunk.getTerrain();
        Vector3Int tmpLocation = Vector3Int.create();
        for(int x=0;x<blockTerrain.getSettings().getChunkSizeX();x++){
            for(int y=0;y<blockTerrain.getSettings().getChunkSizeY();y++){
                for(int z=0;z<blockTerrain.getSettings().getChunkSizeZ();z++){
                    tmpLocation.set(x, y, z);
                    Block block = blockChunk.getBlock(tmpLocation);
                    if(block != null){
                        BlockShape blockShape = block.getShape(blockChunk, tmpLocation);
                        blockShape.prepare(isTransparent, positionsList, indicesList, normalsList, textureCoordinatesList, lightColorsList);
                        blockShape.addTo(blockChunk, tmpLocation);
                        blockShape.reset();
                    }
                }
            }
        }
        positions = positionsList.toArray();
        for(int i=0; i < positions.length ;i++){
            // TODO: addLocal the block position here, and that will save more allocs.
            positions[i] = positions[i].mult(blockTerrain.getSettings().getBlockSize());
            //positions[i].multLocal(blockTerrain.getSettings().getBlockSize());
        }
        indices = indicesList.toArray();
        textureCoordinates = textureCoordinatesList.toArray();
        normals = normalsList.toArray();
        lightColors = lightColorsList.toArray();

        Mesh result = generateMesh();
        reset();
        return result;
    }
    
    private static void reset() {
            positions = null;
            indices = null;
            textureCoordinates = null;
            normals = null;
            lightColors = null;
            positionsList.reset();
            indicesList.reset();
            textureCoordinatesList.reset();
            normalsList.reset();
            lightColorsList.reset();

    }

    private static Mesh generateMesh(){
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(Type.Index, 1, BufferUtils.createShortBuffer(indices));
        mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(textureCoordinates));
        mesh.setBuffer(Type.Color, 4, lightColors);
        mesh.updateBound();
        return mesh;
    }
}
