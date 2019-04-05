package com.cubes.test;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.cubes.*;

public class TestTutorial extends SimpleApplication{

    public static void main(String[] args){
        Logger.getLogger("").setLevel(Level.SEVERE);
        TestTutorial app = new TestTutorial();
        app.setShowSettings(false);
        app.start();
    }

    public TestTutorial(){
        settings = new AppSettings(true);
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setTitle("Cubes Demo - Tutorial");
    }

    @Override
    public void simpleInitApp(){
        CubesTestAssets.registerBlocks();
        
        //This is your terrain, it contains the whole
        //block world and offers methods to modify it
        BlockTerrainControl blockTerrain = new BlockTerrainControl(CubesTestAssets.getSettings(this));

        //To set a block, just specify the location and the block object
        //(Existing blocks will be replaced)
        boolean testTextureAssignment = false;
        if (testTextureAssignment) {
            blockTerrain.setBlock(Vector3Int.create(1, 3, 1), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(1, 2, 1), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(1, 1, 1), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(1, 0, 1), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(0, 0, 1), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(1, 0, 0), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(1, 0, 2), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(2, 0, 1), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(1, 0, 3), CubesTestAssets.BLOCK_COLOR);
            blockTerrain.setBlock(Vector3Int.create(3, 0, 1), CubesTestAssets.BLOCK_COLOR);
        }  else {
            blockTerrain.setBlock(Vector3Int.create(0, 0, 0), CubesTestAssets.BLOCK_WOOD);
            blockTerrain.setBlock(Vector3Int.create(0, 0, 1), CubesTestAssets.BLOCK_WOOD);
            blockTerrain.setBlock(Vector3Int.create(1, 0, 0), CubesTestAssets.BLOCK_WOOD);
            blockTerrain.setBlock(Vector3Int.create(1, 0, 1), CubesTestAssets.BLOCK_STONE);
            blockTerrain.setBlock(0, 0, 0, CubesTestAssets.BLOCK_GRASS); //For the lazy users :P 
        }
        //You can place whole areas of blocks too: setBlockArea(location, size, block)
        //(The specified block will be cloned each time)
        //The following line will set 3 blocks on top of each other
        //({1,1,1}, {1,2,3} and {1,3,1})
        blockTerrain.setBlockArea(Vector3Int.create(1, 1, 1), Vector3Int.create(1, 3, 1), CubesTestAssets.BLOCK_STONE);

        //Removing a block works in a similar way
        blockTerrain.removeBlock(Vector3Int.create(1, 2, 1));
        blockTerrain.removeBlock(Vector3Int.create(1, 3, 1));

        //The terrain is a jME-Control, you can add it
        //to a node of the scenegraph to display it
        Node terrainNode = new Node();
        terrainNode.addControl(blockTerrain);
        rootNode.attachChild(terrainNode);
        
        cam.setLocation(new Vector3f(-10, 10, 16));
        cam.lookAtDirection(new Vector3f(1, -0.56f, -1), Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(50);
    }
}
