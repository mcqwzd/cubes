package com.cubes.test;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.cubes.*;

public class TestShapes extends SimpleApplication{

    public static void main(String[] args){
        Logger.getLogger("").setLevel(Level.SEVERE);
        TestShapes app = new TestShapes();
        app.start();
    }

    public TestShapes(){
        settings = new AppSettings(true);
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setTitle("Cubes Demo - Shapes");
    }

    @Override
    public void simpleInitApp(){
        CubesTestAssets.registerBlocks();
        
        BlockTerrainControl blockTerrain = new BlockTerrainControl(CubesTestAssets.getSettings(this));
        blockTerrain.setBlockArea(Vector3Int.create(0, 0, 0), Vector3Int.create(3, 1, 1), CubesTestAssets.BLOCK_STONE);
        blockTerrain.setBlock(Vector3Int.create(0, 0, 2), CubesTestAssets.BLOCK_GRASS);
        blockTerrain.setBlock(Vector3Int.create(0, 0, 1), CubesTestAssets.BLOCK_GRASS);
        blockTerrain.setBlock(Vector3Int.create(1, 0, 1), CubesTestAssets.BLOCK_GRASS);
        blockTerrain.setBlock(Vector3Int.create(1, 0, 2), CubesTestAssets.BLOCK_GLASS);
        blockTerrain.setBlock(Vector3Int.create(0, 1, 1), CubesTestAssets.BLOCK_WOOD_FLAT);
        blockTerrain.setBlock(Vector3Int.create(2, 0, 1), CubesTestAssets.BLOCK_WOOD);
        blockTerrain.setBlock(Vector3Int.create(2, 0, 2), CubesTestAssets.BLOCK_STONE);
        blockTerrain.setBlock(Vector3Int.create(2, 1, 2), CubesTestAssets.BLOCK_STONE_PILLAR);
        blockTerrain.setBlock(Vector3Int.create(0, 1, 0), CubesTestAssets.BLOCK_BRICK);
        blockTerrain.setBlock(Vector3Int.create(0, 2, 0), CubesTestAssets.BLOCK_CONNECTOR_ROD);
        blockTerrain.setBlock(Vector3Int.create(0, 3, 0), CubesTestAssets.BLOCK_BRICK);
        blockTerrain.setBlock(Vector3Int.create(2, 1, 0), CubesTestAssets.BLOCK_BRICK);
        blockTerrain.setBlock(Vector3Int.create(2, 2, 0), CubesTestAssets.BLOCK_CONNECTOR_ROD);
        blockTerrain.setBlock(Vector3Int.create(2, 3, 0), CubesTestAssets.BLOCK_BRICK);
        blockTerrain.setBlock(Vector3Int.create(1, 1, 0), CubesTestAssets.BLOCK_CONNECTOR_ROD);
        blockTerrain.setBlock(Vector3Int.create(1, 3, 0), CubesTestAssets.BLOCK_CONNECTOR_ROD);
        Node terrainNode = new Node();
        terrainNode.addControl(blockTerrain);
        rootNode.attachChild(terrainNode);
        
        cam.setLocation(new Vector3f(-7, 10, 21));
        cam.lookAtDirection(new Vector3f(0.57f, -0.258f, -0.8f), Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(50);
    }
}
