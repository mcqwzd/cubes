/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes.shapes;

import java.util.List;
import com.cubes.*;
import static com.cubes.Block.Face.Back;
import static com.cubes.Block.Face.Bottom;
import static com.cubes.Block.Face.Front;
import static com.cubes.Block.Face.Left;
import static com.cubes.Block.Face.Right;
import static com.cubes.Block.Face.Top;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

    

/**
 *
 * @author Carl
 */
public class BlockShape_Rhombicuboctahedron extends BlockShape{
    
    public BlockShape_Rhombicuboctahedron(){
        this.extents = new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
        this.inset = new float[]{0.25f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f};
        this.initExtents();
    }


    public BlockShape_Rhombicuboctahedron(float[] extents){
        this.extents = extents;
        this.inset = new float[extents.length];
        for (int i = 0; i < extents.length; ++i) {
            this.inset[i] = extents[i] / 2;        
        }
        this.initExtents();
    }

    public BlockShape_Rhombicuboctahedron(float[] extents, float[] inset){
        this.extents = extents;
        this.inset = inset;
        this.initExtents();
    }

    //{top, bottom, left, right, front, back}
    private float[] extents;
    // inset for rounding
    private float[] inset;
    private float[] xLevels;
    private float[] yLevels;
    private float[] zLevels;

    private void initExtents() {
            // TODO: for a given extent and inset, cache these values
        // Top = +Y
        float topY = 0.5f + extents[0];
        float innerTopY = topY - inset[0];
        // Bottom = -Y
        float bottomY = 0.5f - extents[1];
        float innerBottomY = bottomY + inset[1];
        // Left = -X
        float leftX = 0.5f - extents[2];
        float innerLeftX = leftX + inset[2];
        // Right = +X
        float rightX = 0.5f + extents[3];
        float innerRightX = rightX - inset[3];
        // Front = +Z
        float frontZ = 0.5f - extents[4];
        float innerFrontZ = frontZ + inset[4];
        // Back = -Z
        float backZ = 0.5f + extents[5];
        float innerBackZ = backZ - inset[5];
        
        this.xLevels = new float[]{leftX, innerLeftX, innerRightX, rightX};
        this.yLevels = new float[]{bottomY, innerBottomY, innerTopY, topY};
        this.zLevels = new float[]{backZ, innerBackZ, innerFrontZ, frontZ};

    }
    
    public void cubeAdd(BlockChunkControl chunk, Vector3Int blockLocation, NeighborRelation rTop, NeighborRelation rBottom, NeighborRelation rLeft, NeighborRelation rRight, NeighborRelation rFront, NeighborRelation rBack){
        Block block = chunk.getBlock(blockLocation);
        Vector3f blockLocation3f = new Vector3f(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());
        Vector3f faceLoc_Bottom_TopLeft = blockLocation3f.add((0.5f - extents[2]), (0.5f - extents[1]), (0.5f - extents[5]));
        Vector3f faceLoc_Bottom_TopRight = blockLocation3f.add((0.5f + extents[3]), (0.5f - extents[1]), (0.5f - extents[5]));
        Vector3f faceLoc_Bottom_BottomLeft = blockLocation3f.add((0.5f - extents[2]), (0.5f - extents[1]), (0.5f + extents[4]));
        Vector3f faceLoc_Bottom_BottomRight = blockLocation3f.add((0.5f + extents[3]), (0.5f - extents[1]), (0.5f + extents[4]));
        Vector3f faceLoc_Top_TopLeft = blockLocation3f.add((0.5f - extents[2]), (0.5f + extents[0]), (0.5f - extents[5]));
        Vector3f faceLoc_Top_TopRight = blockLocation3f.add((0.5f + extents[3]), (0.5f + extents[0]), (0.5f - extents[5]));
        Vector3f faceLoc_Top_BottomLeft = blockLocation3f.add((0.5f - extents[2]), (0.5f + extents[0]), (0.5f + extents[4]));
        Vector3f faceLoc_Top_BottomRight = blockLocation3f.add((0.5f + extents[3]), (0.5f + extents[0]), (0.5f + extents[4]));
        float lightColor = 0f;
        
        if(rTop == NeighborRelation.empty){
            lightColor = getLightLevelOfFace(chunk, blockLocation, Block.Face.Top);
            addFaceIndices(indices, positions.size(), lightColor, lightColor, lightColor);
            positions.add(faceLoc_Top_BottomLeft);
            positions.add(faceLoc_Top_BottomRight);
            positions.add(faceLoc_Top_TopLeft);
            positions.add(faceLoc_Top_TopRight);
            addSquareNormals(normals, 0, 1, 0);            
            addTextureCoordinates(chunk, textureCoordinates, block.getSkin(chunk, blockLocation, Block.Face.Top).getTextureLocation());
        }
        if(rBottom == NeighborRelation.empty){
            lightColor = getLightLevelOfFace(chunk, blockLocation, Block.Face.Bottom);
            addFaceIndices(indices, positions.size(), lightColor, lightColor, lightColor);
            positions.add(faceLoc_Bottom_BottomRight);
            positions.add(faceLoc_Bottom_BottomLeft);
            positions.add(faceLoc_Bottom_TopRight);
            positions.add(faceLoc_Bottom_TopLeft);
            addSquareNormals(normals, 0, -1, 0);
            addTextureCoordinates(chunk, textureCoordinates, block.getSkin(chunk, blockLocation, Block.Face.Bottom).getTextureLocation());
        }
        if(rLeft == NeighborRelation.empty){
            lightColor = getLightLevelOfFace(chunk, blockLocation, Block.Face.Left);
            addFaceIndices(indices, positions.size(),  lightColor, lightColor, lightColor);
            positions.add(faceLoc_Bottom_TopLeft);
            positions.add(faceLoc_Bottom_BottomLeft);
            positions.add(faceLoc_Top_TopLeft);
            positions.add(faceLoc_Top_BottomLeft);
            addSquareNormals(normals, -1, 0, 0);
            addTextureCoordinates(chunk, textureCoordinates, block.getSkin(chunk, blockLocation, Block.Face.Left).getTextureLocation());
        }
        if(rRight == NeighborRelation.empty){
            lightColor = getLightLevelOfFace(chunk, blockLocation, Block.Face.Right);
            addFaceIndices(indices, positions.size(),  lightColor, lightColor, lightColor);
            positions.add(faceLoc_Bottom_BottomRight);
            positions.add(faceLoc_Bottom_TopRight);
            positions.add(faceLoc_Top_BottomRight);
            positions.add(faceLoc_Top_TopRight);
            addSquareNormals(normals, 1, 0, 0);
            addTextureCoordinates(chunk, textureCoordinates, block.getSkin(chunk, blockLocation, Block.Face.Right).getTextureLocation());
        }
        if(rFront == NeighborRelation.empty){
            lightColor = getLightLevelOfFace(chunk, blockLocation, Block.Face.Front);
            addFaceIndices(indices, positions.size(),  lightColor, lightColor, lightColor);
            positions.add(faceLoc_Bottom_BottomLeft);
            positions.add(faceLoc_Bottom_BottomRight);
            positions.add(faceLoc_Top_BottomLeft);
            positions.add(faceLoc_Top_BottomRight);
            addSquareNormals(normals, 0, 0, 1);
            addTextureCoordinates(chunk, textureCoordinates,block.getSkin(chunk, blockLocation, Block.Face.Front).getTextureLocation());
        }
        if(rBack == NeighborRelation.empty){
            lightColor = getLightLevelOfFace(chunk, blockLocation, Block.Face.Back);
            addFaceIndices(indices, positions.size(),  lightColor, lightColor, lightColor);
            positions.add(faceLoc_Bottom_TopRight);
            positions.add(faceLoc_Bottom_TopLeft);
            positions.add(faceLoc_Top_TopRight);
            positions.add(faceLoc_Top_TopLeft);
            addSquareNormals(normals, 0, 0, -1);
            addTextureCoordinates(chunk, textureCoordinates, block.getSkin(chunk, blockLocation, Block.Face.Back).getTextureLocation());
        }
    }
    @Override
    public void addTo(BlockChunkControl chunk, Vector3Int blockLocation){
            //Block block = chunk.getBlock(blockLocation);
        //Vector3f blockLocation3f = new Vector3f(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());
        

        NeighborRelation rTop = getNeighborRelation(chunk, blockLocation, Block.Face.Top);
        NeighborRelation rBottom = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom);
        NeighborRelation rLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Left);
        NeighborRelation rRight = getNeighborRelation(chunk, blockLocation, Block.Face.Right);
        NeighborRelation rFront = getNeighborRelation(chunk, blockLocation, Block.Face.Front);
        NeighborRelation rBack = getNeighborRelation(chunk, blockLocation, Block.Face.Back);

        int emptyNeighborCount = 0;
        if (rTop == NeighborRelation.empty) {
            ++emptyNeighborCount;
        }
        if (rBottom == NeighborRelation.empty) {
            ++emptyNeighborCount;
        }
        if (rLeft == NeighborRelation.empty) {
            ++emptyNeighborCount;
        }
        if (rRight == NeighborRelation.empty) {
            ++emptyNeighborCount;
        }
        if (rFront == NeighborRelation.empty) {
            ++emptyNeighborCount;
        }
        if (rBack == NeighborRelation.empty) {
            ++emptyNeighborCount;
        }
        if (emptyNeighborCount == 0) {
            return;
        }
        if (emptyNeighborCount == 1) {
            this.cubeAdd(chunk, blockLocation, rTop, rBottom, rLeft, rRight, rFront, rBack);
            return;
        }
        
      
        int topBackEdge = 0;
        int topFrontEdge = 0;
        int topLeftEdge = 0;
        int topRightEdge = 0;
        int bottomBackEdge = 0;
        int bottomFrontEdge = 0;
        int bottomLeftEdge = 0;
        int bottomRightEdge = 0;
        int frontLeftEdge = 0;
        int frontRightEdge = 0;
        int backLeftEdge = 0;
        int backRightEdge = 0;
        int topBackLeftCorner = 0;
        int topBackRightCorner = 0;
        int topFrontLeftCorner = 0;
        int topFrontRightCorner = 0;
        int bottomBackLeftCorner = 0;
        int bottomBackRightCorner = 0;
        int bottomFrontLeftCorner = 0;
        int bottomFrontRightCorner = 0;
        int value = 0;

        if (rTop != NeighborRelation.empty) {
            value = (rTop == NeighborRelation.different) ? 2 : 1; 
            topBackEdge += value;
            topFrontEdge += value;
            topLeftEdge += value;
            topRightEdge += value;
            topBackLeftCorner += value;
            topBackRightCorner += value;
            topFrontLeftCorner += value;
            topFrontRightCorner += value;
       }
        
        if (rBottom != NeighborRelation.empty) {
            value = (rBottom == NeighborRelation.different) ? 2 : 1; 
            bottomBackEdge += value;
            bottomFrontEdge += value;
            bottomLeftEdge += value;
            bottomRightEdge += value;
            bottomBackLeftCorner += value;
            bottomBackRightCorner += value;
            bottomFrontLeftCorner += value;
            bottomFrontRightCorner += value;
        }
        
        if (rLeft != NeighborRelation.empty) {
            value = (rLeft == NeighborRelation.different) ? 2 : 1; 
            topLeftEdge += value;
            bottomLeftEdge += value;
            frontLeftEdge += value;
            backLeftEdge += value;            
            topBackLeftCorner += value;
            topFrontLeftCorner += value;
            bottomBackLeftCorner += value;
            bottomFrontLeftCorner += value;
        }

        if (rRight != NeighborRelation.empty) {
            value = (rRight == NeighborRelation.different) ? 2 : 1; 
            topRightEdge += value;
            bottomRightEdge += value;
            frontRightEdge += value;
            backRightEdge += value;            
            topBackRightCorner += value;
            topFrontRightCorner += value;
            bottomBackRightCorner += value;
            bottomFrontRightCorner += value;
        }

        if (rFront != NeighborRelation.empty) {
            value = (rFront == NeighborRelation.different) ? 2 : 1; 
            topFrontEdge += value;
            bottomFrontEdge += value;
            frontRightEdge += value;
            frontLeftEdge += value;            
            topFrontLeftCorner += value;
            topFrontRightCorner += value;
            bottomFrontLeftCorner += value;
            bottomFrontRightCorner += value;
        }

        if (rBack != NeighborRelation.empty) {
            value = (rBack == NeighborRelation.different) ? 2 : 1; 
            topBackEdge += value;
            bottomBackEdge += value;
            backRightEdge += value;
            backLeftEdge += value;            
            topBackLeftCorner += value;
            topBackRightCorner += value;
            bottomBackLeftCorner += value;
            bottomBackRightCorner += value;
        }
        
        NeighborRelation rTopBack = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Back);
        NeighborRelation rTopFront = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Front);
        NeighborRelation rTopLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Left);
        NeighborRelation rTopRight = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Right);
        NeighborRelation rBottomBack = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Back);
        NeighborRelation rBottomFront = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Front);
        NeighborRelation rBottomLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Left);
        NeighborRelation rBottomRight = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Right);
        NeighborRelation rFrontLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Front, Block.Face.Left);
        NeighborRelation rFrontRight = getNeighborRelation(chunk, blockLocation, Block.Face.Front, Block.Face.Right);
        NeighborRelation rBackLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Back, Block.Face.Left);
        NeighborRelation rBackRight = getNeighborRelation(chunk, blockLocation, Block.Face.Back, Block.Face.Right);
        
        if (rTopBack != NeighborRelation.empty) {
            value = (rTopBack == NeighborRelation.different) ? 2 : 1; 
            topBackEdge += value;
            topBackLeftCorner += value;
            topBackRightCorner += value;
        }
        if (rTopFront != NeighborRelation.empty) {
            value = (rTopFront == NeighborRelation.different) ? 2 : 1; 
            topFrontEdge += value;
            topFrontLeftCorner += value;
            topFrontRightCorner += value;
        }
        if (rTopLeft != NeighborRelation.empty) {
            value = (rTopLeft == NeighborRelation.different) ? 2 : 1; 
            topLeftEdge += value;
            topBackLeftCorner += value;
            topFrontLeftCorner += value;
        }
        if (rTopRight != NeighborRelation.empty) {
            value = (rTopRight == NeighborRelation.different) ? 2 : 1; 
            topRightEdge += value;
            topBackRightCorner += value;
            topFrontRightCorner += value;
        }
        if (rBottomBack != NeighborRelation.empty) {
            value = (rBottomBack == NeighborRelation.different) ? 2 : 1; 
            bottomBackEdge += value;
            bottomBackLeftCorner += value;
            bottomBackRightCorner += value;
        }
        if (rBottomFront != NeighborRelation.empty) {
            value = (rBottomFront == NeighborRelation.different) ? 2 : 1; 
            bottomFrontEdge += value;
            bottomFrontLeftCorner += value;
            bottomFrontRightCorner += value;
        }
        if (rBottomLeft != NeighborRelation.empty) {
            value = (rBottomLeft == NeighborRelation.different) ? 2 : 1; 
            bottomLeftEdge += value;
            bottomBackLeftCorner += value;
            bottomFrontLeftCorner += value;
        }
        if (rBottomRight != NeighborRelation.empty) {
            value = (rBottomRight == NeighborRelation.different) ? 2 : 1; 
            bottomRightEdge += value;
            bottomBackRightCorner += value;
            bottomFrontRightCorner += value;
        }
        if (rFrontLeft != NeighborRelation.empty) {
            value = (rFrontLeft == NeighborRelation.different) ? 2 : 1; 
            frontLeftEdge += value;
            topFrontLeftCorner += value;
            bottomFrontLeftCorner += value;
        }
        if (rFrontRight != NeighborRelation.empty) {
            value = (rFrontRight == NeighborRelation.different) ? 2 : 1; 
            frontRightEdge += value;
            topFrontRightCorner += value;
            bottomFrontRightCorner += value;
        }
        if (rBackLeft != NeighborRelation.empty) {
            value = (rBackLeft == NeighborRelation.different) ? 2 : 1; 
            backLeftEdge += value;
            topBackLeftCorner += value;
            bottomBackLeftCorner += value;
        }
        if (rBackRight != NeighborRelation.empty) {
            value = (rBackRight == NeighborRelation.different) ? 2 : 1; 
            backRightEdge += value;
            topBackRightCorner += value;
            bottomBackRightCorner += value;
        }

        NeighborRelation rTopBackLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Back, Block.Face.Left);
        NeighborRelation rTopBackRight = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Back, Block.Face.Right);
        NeighborRelation rTopFrontLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Front, Block.Face.Left);
        NeighborRelation rTopFrontRight = getNeighborRelation(chunk, blockLocation, Block.Face.Top, Block.Face.Front, Block.Face.Right);
        NeighborRelation rBottomBackLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Back, Block.Face.Left);
        NeighborRelation rBottomBackRight = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Back, Block.Face.Right);
        NeighborRelation rBottomFrontLeft = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Front, Block.Face.Left);
        NeighborRelation rBottomFrontRight = getNeighborRelation(chunk, blockLocation, Block.Face.Bottom, Block.Face.Front, Block.Face.Right);
        
        if (rTopBackLeft != NeighborRelation.empty) {
            value = (rTopBackLeft == NeighborRelation.different) ? 2 : 1; 
            topBackLeftCorner += value;
        }
        if (rTopBackRight != NeighborRelation.empty) {
            value = (rTopBackRight == NeighborRelation.different) ? 2 : 1; 
            topBackRightCorner += value;
        }
        if (rTopFrontLeft != NeighborRelation.empty) {
            value = (rTopFrontLeft == NeighborRelation.different) ? 2 : 1; 
            topFrontLeftCorner += value;
        }
        if (rTopFrontRight != NeighborRelation.empty) {
            value = (rTopFrontRight == NeighborRelation.different) ? 2 : 1; 
            topFrontRightCorner += value;
        }
        if (rBottomBackLeft != NeighborRelation.empty) {
            value = (rBottomBackLeft == NeighborRelation.different) ? 2 : 1; 
            bottomBackLeftCorner += value;
        }
        if (rBottomBackRight != NeighborRelation.empty) {
            value = (rBottomBackRight == NeighborRelation.different) ? 2 : 1; 
            bottomBackRightCorner += value;
        }
        if (rBottomFrontLeft != NeighborRelation.empty) {
            value = (rBottomFrontLeft == NeighborRelation.different) ? 2 : 1; 
            bottomFrontLeftCorner += value;
        }
        if (rBottomFrontRight != NeighborRelation.empty) {
            value = (rBottomFrontRight == NeighborRelation.different) ? 2 : 1; 
            bottomFrontRightCorner += value;
        }


        Block block = chunk.getBlock(blockLocation);
        Vector3f blockLocation3f = new Vector3f(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());
        
        
        Vector3f[][][] pointGrid = new Vector3f[4][4][4];
        for(int xL = 0; xL < 4; ++xL) {
            for(int yL = 0; yL < 4; ++yL) {
                for(int zL = 0; zL < 4; ++zL)  {
                    pointGrid[xL][yL][zL] = blockLocation3f.add(new Vector3f(xLevels[xL], yLevels[yL], zLevels[zL]));
                }
            }
        }
        
        int front = 0, midFront = 1, midBack = 2, back = 3;
        int left = 0, midLeft = 1, midRight = 2, right = 3;
        int bottom = 0, midBottom = 1, midTop = 2, top = 3;
        Vector3f normalTop = new Vector3f(0, 1, 0);
        Vector3f normalBottom = new Vector3f(0,-1,0);
        Vector3f normalBack = new Vector3f(0,0,1);
        Vector3f normalFront = new Vector3f(0,0,-1);
        Vector3f normalLeft = new Vector3f(-1, 0, 0);
        Vector3f normalRight = new Vector3f(1,0,0);

        if (rTop == NeighborRelation.empty) {
            this.addFace(pointGrid[midLeft][top][midFront], pointGrid[midRight][top][midFront],
                         pointGrid[midLeft][top][midBack], pointGrid[midRight][top][midBack],
                         normalTop, Block.Face.Top,chunk, block,blockLocation);
        }

        if(rBottom == NeighborRelation.empty){
            this.addFace(pointGrid[midRight][bottom][midFront], pointGrid[midLeft][bottom][midFront],
                         pointGrid[midRight][bottom][midBack], pointGrid[midLeft][bottom][midBack],
                         normalBottom, Block.Face.Bottom,chunk, block,blockLocation);
        }
        
        if(rLeft == NeighborRelation.empty){
            this.addFace(pointGrid[left][midBottom][midBack], pointGrid[left][midBottom][midFront],
                         pointGrid[left][midTop][midBack], pointGrid[left][midTop][midFront],
                         normalLeft, Block.Face.Left,chunk, block,blockLocation);
        }
        if(rRight == NeighborRelation.empty){
            this.addFace(pointGrid[right][midBottom][midFront], pointGrid[right][midBottom][midBack],
                         pointGrid[right][midTop][midFront], pointGrid[right][midTop][midBack],
                         normalRight, Block.Face.Right,chunk, block,blockLocation);
        }
        if(rFront == NeighborRelation.empty){
                 this.addFace(pointGrid[midLeft][midBottom][front], pointGrid[midRight][midBottom][front],
                           pointGrid[midLeft][midTop][front], pointGrid[midRight][midTop][front],
                         normalFront, Block.Face.Front,chunk, block,blockLocation);
        }
        if(rBack == NeighborRelation.empty){
            this.addFace(pointGrid[midRight][midBottom][back], pointGrid[midLeft][midBottom][back],
                         pointGrid[midRight][midTop][back], pointGrid[midLeft][midTop][back],
                         normalBack, Block.Face.Back,chunk, block,blockLocation);
        }
        
        // Top Edges
        if (topBackEdge == 0) {
            this.addFace(pointGrid[midRight][midTop][back], pointGrid[midLeft][midTop][back],
                         pointGrid[midRight][top][midBack], pointGrid[midLeft][top][midBack],
                         normalTop, Block.Face.Top,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midRight][midTop][back], pointGrid[midLeft][midTop][back], 
                         pointGrid[midRight][top][back], pointGrid[midLeft][top][back],
                         pointGrid[midRight][top][midBack], pointGrid[midLeft][top][midBack], 
                         rBack, normalBack, Block.Face.Back, 
                         rTop, normalTop, Block.Face.Top, chunk, block, blockLocation);
        }
        
        if (topFrontEdge == 0) {
            this.addFace(pointGrid[midRight][top][midFront], pointGrid[midLeft][top][midFront],
                         pointGrid[midRight][midTop][front], pointGrid[midLeft][midTop][front],
                         normalTop, Block.Face.Top,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midLeft][midTop][front], pointGrid[midRight][midTop][front],
                         pointGrid[midLeft][top][front], pointGrid[midRight][top][front],
                         pointGrid[midLeft][top][midFront], pointGrid[midRight][top][midFront],
                         rFront, normalFront, Block.Face.Front, 
                         rTop, normalTop, Block.Face.Top, chunk, block, blockLocation);
        }
        if (topLeftEdge == 0) {
            this.addFace(pointGrid[left][midTop][midFront], pointGrid[midLeft][top][midFront],
                         pointGrid[left][midTop][midBack], pointGrid[midLeft][top][midBack],
                         normalTop, Block.Face.Top,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[left][midTop][midBack], pointGrid[left][midTop][midFront], 
                         pointGrid[left][top][midBack], pointGrid[left][top][midFront],
                         pointGrid[midLeft][top][midBack], pointGrid[midLeft][top][midFront],
                         rLeft, normalLeft, Block.Face.Left, 
                         rTop, normalTop, Block.Face.Top, chunk, block, blockLocation);
        }
        if (topRightEdge == 0) {
            this.addFace( pointGrid[midRight][top][midBack], pointGrid[midRight][top][midFront],
                         pointGrid[right][midTop][midBack], pointGrid[right][midTop][midFront],
                         normalTop, Block.Face.Top,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[right][midTop][midFront], pointGrid[right][midTop][midBack], 
                         pointGrid[right][top][midFront], pointGrid[right][top][midBack],
                         pointGrid[midRight][top][midFront], pointGrid[midRight][top][midBack], 
                         rRight, normalRight, Block.Face.Right, 
                         rTop, normalTop, Block.Face.Top, chunk, block, blockLocation);
        }
        // Bottom Edges
        if (bottomBackEdge == 0) {
            this.addFace(pointGrid[midRight][bottom][midBack], pointGrid[midLeft][bottom][midBack],
                         pointGrid[midRight][midBottom][back], pointGrid[midLeft][midBottom][back],
                         normalBottom, Block.Face.Bottom,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midLeft][midBottom][back], pointGrid[midRight][midBottom][back],
                         pointGrid[midLeft][bottom][back], pointGrid[midRight][bottom][back],
                         pointGrid[midLeft][bottom][midBack], pointGrid[midRight][bottom][midBack], 
                         rBack, normalBack, Block.Face.Back, 
                         rBottom, normalBottom, Block.Face.Bottom, chunk, block, blockLocation);
        }
        if (bottomFrontEdge == 0) {
            this.addFace(pointGrid[midRight][midBottom][front], pointGrid[midLeft][midBottom][front],
                         pointGrid[midRight][bottom][midFront], pointGrid[midLeft][bottom][midFront],
                         normalBottom, Block.Face.Bottom,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midRight][midBottom][front], pointGrid[midLeft][midBottom][front], 
                         pointGrid[midRight][bottom][front], pointGrid[midLeft][bottom][front],
                         pointGrid[midRight][bottom][midFront], pointGrid[midLeft][bottom][midFront], 
                         rFront, normalFront, Block.Face.Front, 
                         rBottom, normalBottom, Block.Face.Bottom, chunk, block, blockLocation);
        }
        if (bottomLeftEdge == 0) {
            this.addFace(pointGrid[midLeft][bottom][midBack], pointGrid[midLeft][bottom][midFront],
                         pointGrid[left][midBottom][midBack], pointGrid[left][midBottom][midFront],
                         normalBottom, Block.Face.Bottom,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[left][midBottom][midFront], pointGrid[left][midBottom][midBack],  
                         pointGrid[left][bottom][midFront],  pointGrid[left][bottom][midBack], 
                         pointGrid[midLeft][bottom][midFront], pointGrid[midLeft][bottom][midBack], 
                         rLeft, normalLeft, Block.Face.Left, 
                         rBottom, normalBottom, Block.Face.Bottom, chunk, block, blockLocation);
        }
        if (bottomRightEdge == 0) {
            this.addFace(pointGrid[right][midBottom][midBack], pointGrid[right][midBottom][midFront],
                         pointGrid[midRight][bottom][midBack], pointGrid[midRight][bottom][midFront],
                         normalBottom, Block.Face.Bottom,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[right][midBottom][midBack], pointGrid[right][midBottom][midFront],
                         pointGrid[right][bottom][midBack],pointGrid[right][bottom][midFront], 
                         pointGrid[midRight][bottom][midBack], pointGrid[midRight][bottom][midFront], 
                         rRight, normalRight, Block.Face.Right, 
                         rBottom, normalBottom, Block.Face.Bottom, chunk, block, blockLocation);
        }
        //Around Edges
        if (frontLeftEdge == 0) {
            this.addFace(pointGrid[midLeft][midBottom][front], pointGrid[midLeft][midTop][front],
                         pointGrid[left][midBottom][midFront], pointGrid[left][midTop][midFront],
                         normalBottom, Block.Face.Left,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midLeft][midBottom][front], pointGrid[midLeft][midTop][front],
                         pointGrid[left][midBottom][front],pointGrid[left][midTop][front], 
                         pointGrid[left][midBottom][midFront],pointGrid[left][midTop][midFront], 
                         rFront, normalFront, Block.Face.Front, 
                         rLeft, normalLeft, Block.Face.Left, chunk, block, blockLocation);
        }
        
        if (frontRightEdge == 0) {
            this.addFace(pointGrid[right][midBottom][midFront], pointGrid[right][midTop][midFront],
                         pointGrid[midRight][midBottom][front], pointGrid[midRight][midTop][front],
                         normalBottom, Block.Face.Front,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midRight][midTop][front], pointGrid[midRight][midBottom][front], 
                         pointGrid[right][midTop][front], pointGrid[right][midBottom][front],
                         pointGrid[right][midTop][midFront], pointGrid[right][midBottom][midFront],
                         rFront, normalFront, Block.Face.Front, 
                         rRight, normalRight, Block.Face.Right, chunk, block, blockLocation);
        }
        if (backLeftEdge == 0) {
            this.addFace(pointGrid[left][midBottom][midBack], pointGrid[left][midTop][midBack],
                         pointGrid[midLeft][midBottom][back], pointGrid[midLeft][midTop][back],
                         normalBottom, Block.Face.Back,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midLeft][midTop][back],pointGrid[midLeft][midBottom][back], 
                         pointGrid[left][midTop][back], pointGrid[left][midBottom][back],
                         pointGrid[left][midTop][midBack], pointGrid[left][midBottom][midBack],
                         rBack, normalBack, Block.Face.Back, 
                         rLeft, normalLeft, Block.Face.Left, chunk, block, blockLocation);
        }
        if (backRightEdge == 0) {
            this.addFace(pointGrid[midRight][midBottom][back], pointGrid[midRight][midTop][back],
                         pointGrid[right][midBottom][midBack], pointGrid[right][midTop][midBack],
                         normalBottom, Block.Face.Right,chunk, block,blockLocation);
        } else {
            addSharpEdge(pointGrid[midRight][midBottom][back], pointGrid[midRight][midTop][back], 
                          pointGrid[right][midBottom][back], pointGrid[right][midTop][back],
                          pointGrid[right][midBottom][midBack],pointGrid[right][midTop][midBack],
                         rBack, normalBack, Block.Face.Back, 
                         rRight, normalRight, Block.Face.Right, chunk, block, blockLocation);
        }
        
        // Corners
        this.addCorner(bottomBackLeftCorner, pointGrid[left][bottom][back],
                  rBack, rLeft, backLeftEdge, normalBack, normalLeft, Block.Face.Back,Block.Face.Left,
                  pointGrid[midLeft][midBottom][back], pointGrid[left][midBottom][back],pointGrid[left][midBottom][midBack], 
                  rBottom, rBack, bottomBackEdge, normalBottom, normalBack,Block.Face.Bottom,Block.Face.Back,
                  pointGrid[midLeft][bottom][midBack], pointGrid[midLeft][bottom][back], pointGrid[midLeft][midBottom][back],
                  rLeft, rBottom,  bottomLeftEdge, normalLeft, normalBottom, Block.Face.Left, Block.Face.Bottom,
                  pointGrid[left][midBottom][midBack], pointGrid[left][bottom][midBack], pointGrid[midLeft][bottom][midBack],
                  chunk, block, blockLocation, Block.Face.Bottom);
/**/
        this.addCorner(bottomBackRightCorner, pointGrid[right][bottom][back],
                      rBottom, rRight, bottomRightEdge, normalBottom,  normalRight, Block.Face.Bottom, Block.Face.Right,
                      pointGrid[midRight][bottom][midBack], pointGrid[right][bottom][midBack], pointGrid[right][midBottom][midBack],
                      rBack, rBottom, bottomBackEdge, normalBack, normalBottom, Block.Face.Back,Block.Face.Bottom,
                      pointGrid[midRight][midBottom][back], pointGrid[midRight][bottom][back], pointGrid[midRight][bottom][midBack],  
                      rRight, rBack, backRightEdge, normalRight, normalBack, Block.Face.Right, Block.Face.Back,
                      pointGrid[right][midBottom][midBack], pointGrid[right][midBottom][back], pointGrid[midRight][midBottom][back],
                      chunk, block, blockLocation, Block.Face.Bottom);
        
        this.addCorner(bottomFrontLeftCorner, pointGrid[left][bottom][front],
                rBottom, rLeft, bottomLeftEdge, normalBottom,  normalLeft, Block.Face.Bottom, Block.Face.Left,
                pointGrid[midLeft][bottom][midFront], pointGrid[left][bottom][midFront], pointGrid[left][midBottom][midFront],
                rFront, rBottom, bottomFrontEdge, normalFront, normalBottom, Block.Face.Front,  Block.Face.Bottom,
                pointGrid[midLeft][midBottom][front], pointGrid[midLeft][bottom][front], pointGrid[midLeft][bottom][midFront],
                rLeft, rFront, frontLeftEdge, normalLeft, normalFront, Block.Face.Left, Block.Face.Front,
                pointGrid[left][midBottom][midFront], pointGrid[left][midBottom][front], pointGrid[midLeft][midBottom][front], 
                chunk, block, blockLocation, Block.Face.Bottom);
        
        this.addCorner(bottomFrontRightCorner, pointGrid[right][bottom][front],
                rFront, rRight, frontRightEdge, normalFront, normalRight, Block.Face.Front, Block.Face.Right,
                pointGrid[midRight][midBottom][front], pointGrid[right][midBottom][front], pointGrid[right][midBottom][midFront],
                rBottom, rFront, bottomFrontEdge, normalBottom, normalFront,Block.Face.Bottom,Block.Face.Front,
                pointGrid[midRight][bottom][midFront], pointGrid[midRight][bottom][front], pointGrid[midRight][midBottom][front],
                rRight, rBottom, bottomRightEdge, normalRight, normalBottom, Block.Face.Right,Block.Face.Bottom,
                pointGrid[right][midBottom][midFront], pointGrid[right][bottom][midFront], pointGrid[midRight][bottom][midFront],
                chunk, block, blockLocation, Block.Face.Bottom);
        
        this.addCorner(topBackLeftCorner, pointGrid[left][top][back],
                      rTop, rLeft, topLeftEdge, normalTop,  normalLeft, Block.Face.Top, Block.Face.Left,
                      pointGrid[midLeft][top][midBack], pointGrid[left][top][midBack], pointGrid[left][midTop][midBack],
                      rBack, rTop, topBackEdge, normalBack, normalTop, Block.Face.Back,  Block.Face.Top,
                      pointGrid[midLeft][midTop][back], pointGrid[midLeft][top][back], pointGrid[midLeft][top][midBack],
                      rLeft, rBack, backLeftEdge, normalLeft, normalBack, Block.Face.Left, Block.Face.Back,
                      pointGrid[left][midTop][midBack],pointGrid[left][midTop][back], pointGrid[midLeft][midTop][back], 
                      chunk, block, blockLocation, Block.Face.Top);
       
       this.addCorner(topBackRightCorner, pointGrid[right][top][back],
                      rBack, rRight, backRightEdge, normalBack, normalRight, Block.Face.Back, Block.Face.Right,
                      pointGrid[midRight][midTop][back], pointGrid[right][midTop][back], pointGrid[right][midTop][midBack],
                      rTop, rBack, topBackEdge, normalTop, normalBack,Block.Face.Top,  Block.Face.Back,
                      pointGrid[midRight][top][midBack], pointGrid[midRight][top][back], pointGrid[midRight][midTop][back],
                      rRight, rTop, topRightEdge, normalRight,  normalTop, Block.Face.Right,Block.Face.Top,
                      pointGrid[right][midTop][midBack], pointGrid[right][top][midBack], pointGrid[midRight][top][midBack],
                      chunk, block, blockLocation, Block.Face.Top);
       this.addCorner(topFrontLeftCorner, pointGrid[left][top][front],
                      rFront, rLeft, frontLeftEdge, normalFront, normalLeft, Block.Face.Front, Block.Face.Left,
                      pointGrid[midLeft][midTop][front], pointGrid[left][midTop][front], pointGrid[left][midTop][midFront], 
                      rTop, rFront,  topFrontEdge, normalTop, normalFront,Block.Face.Top, Block.Face.Front,
                      pointGrid[midLeft][top][midFront], pointGrid[midLeft][top][front], pointGrid[midLeft][midTop][front],
                      rLeft, rTop,  topLeftEdge, normalLeft, normalTop, Block.Face.Left, Block.Face.Top,
                      pointGrid[left][midTop][midFront], pointGrid[left][top][midFront], pointGrid[midLeft][top][midFront],
                      chunk, block, blockLocation, Block.Face.Top);

       this.addCorner(topFrontRightCorner, pointGrid[right][top][front],
                      rTop, rRight, topRightEdge, normalTop,  normalRight, Block.Face.Top,Block.Face.Right,
                      pointGrid[midRight][top][midFront], pointGrid[right][top][midFront], pointGrid[right][midTop][midFront], 
                      rFront, rTop, topFrontEdge, normalFront, normalTop, Block.Face.Front, Block.Face.Top,
                      pointGrid[midRight][midTop][front], pointGrid[midRight][top][front], pointGrid[midRight][top][midFront], 
                      rRight, rFront, frontRightEdge, normalRight, normalFront, Block.Face.Right,Block.Face.Front,
                       pointGrid[right][midTop][midFront],pointGrid[right][midTop][front], pointGrid[midRight][midTop][front], 
                      chunk, block, blockLocation, Block.Face.Top);
        
    } 
    private void addSharpCornerFaces(Vector3f edgeA, Vector3f edgePeek, Vector3f edgeC, Vector3f pointCorner,
                                     NeighborRelation rA, NeighborRelation rB, Vector3f normalA, Vector3f normalB, Block.Face faceA, Block.Face faceB,
                                     BlockChunkControl chunk, Block block, Vector3Int blockLocation) {
        if (rA == NeighborRelation.empty) {
            this.addTriangleFace(edgeA, pointCorner, edgePeek,
            normalA,faceA,chunk, block,blockLocation);
        }
        if (rB == NeighborRelation.empty) {
            this.addTriangleFace(edgePeek, pointCorner, edgeC,
            normalB,faceB,chunk, block,blockLocation);
        }
    }
    private void addCorner(int cornerValue, Vector3f pointCorner, 
                           NeighborRelation r1A, NeighborRelation r1B, int edge1Value, Vector3f normalEdge1SideA, Vector3f normalEdge1SideB, Block.Face edge1FaceA, Block.Face edge1FaceB, 
                           Vector3f edge1A, Vector3f edge1Peek, Vector3f edge1C, 
                           NeighborRelation r2A, NeighborRelation r2B,int edge2Value, Vector3f normalEdge2SideA, Vector3f normalEdge2SideB, Block.Face edge2FaceA, Block.Face edge2FaceB,
                           Vector3f edge2A, Vector3f edge2Peek, Vector3f edge2C, 
                           NeighborRelation r3A, NeighborRelation r3B,int edge3Value, Vector3f normalEdge3SideA, Vector3f normalEdge3SideB, Block.Face edge3FaceA, Block.Face edge3FaceB,
                           Vector3f edge3A, Vector3f edge3Peek, Vector3f edge3C, 
                          BlockChunkControl chunk, Block block, Vector3Int blockLocation,
                          Block.Face flatCornerFace) {
        if (cornerValue == 0 ) {
            // TODO: multiply the normals together?
            this.addTriangleFace(edge1A, edge2A, edge3A,
            normalEdge1SideB,flatCornerFace,chunk, block,blockLocation);
        } else {
            
            int sharpEdgeCount = 0;
            if (edge1Value > 0) {
                sharpEdgeCount++;
            }
            if (edge2Value > 0) {
                sharpEdgeCount++;
            }
            if (edge3Value > 0) {
                sharpEdgeCount++;
            }
            if (sharpEdgeCount == 2 && cornerValue < 2) {
              if (edge1Value == 0) {
                    this.addFace(edge2C, edge2Peek, edge3A, edge3Peek,
                    normalEdge1SideB,edge1FaceA,chunk, block,blockLocation);
              } else if (edge2Value == 0) {
                    this.addFace(edge3C, edge3Peek, edge1A, edge1Peek,
                    normalEdge2SideB,edge2FaceA,chunk, block,blockLocation);
              } else if (edge3Value == 0) {
                    this.addFace(edge1C, edge1Peek, edge2A, edge2Peek,
                    normalEdge3SideB,edge3FaceA,chunk, block,blockLocation);
              }
                
            } else {
                if (edge1Value > 0) {
                    addSharpCornerFaces(edge1A, edge1Peek, edge1C, pointCorner,
                                        r1A, r1B, normalEdge1SideA, normalEdge1SideB, edge1FaceA, edge1FaceB, chunk, block, blockLocation);
                } else {
                    this.addTriangleFace(edge1A, pointCorner, edge1C,
                    normalEdge1SideB,edge1FaceA,chunk, block,blockLocation);
                }

                if (edge2Value > 0) {
                    addSharpCornerFaces(edge2A, edge2Peek, edge2C, pointCorner,
                                       r2A, r2B, normalEdge2SideA, normalEdge2SideB, edge2FaceA, edge2FaceB, chunk, block, blockLocation);
                } else {
                    this.addTriangleFace(edge2A, pointCorner, edge2C,
                    normalEdge2SideB,edge2FaceA,chunk, block,blockLocation);
                }
            
                if (edge3Value > 0) {
                    addSharpCornerFaces(edge3A, edge3Peek, edge3C, pointCorner,
                                       r3A, r3B, normalEdge3SideA, normalEdge3SideB, edge3FaceA, edge3FaceB, chunk, block, blockLocation);
                } else {
                    this.addTriangleFace(edge3A, pointCorner, edge3C,
                    normalEdge3SideB,edge3FaceA,chunk, block,blockLocation);
                }
            }
        }
    }

    private void addSharpEdge(
            Vector3f pointA1, Vector3f pointA2,
            Vector3f pointAB1, Vector3f pointAB2, 
            Vector3f pointB1, Vector3f pointB2,
            NeighborRelation rA, Vector3f normalA,  Block.Face faceA,
            NeighborRelation rB, Vector3f normalB,  Block.Face faceB,
            BlockChunkControl chunk, Block block, Vector3Int blockLocation) {
        if (rA == NeighborRelation.empty) {
            addFace(pointA1, pointA2, pointAB1, pointAB2, normalA, faceA, chunk, block, blockLocation);
        }
        if (rB == NeighborRelation.empty) {
            addFace( pointAB1, pointAB2, pointB1, pointB2, normalB, faceB, chunk, block, blockLocation);
        }
    }

    private void addFace(Vector3f point1, Vector3f point2, Vector3f point3, Vector3f point4, Vector3f normal, Block.Face face, BlockChunkControl chunk, Block block, Vector3Int blockLocation) {
        float lightLevel = getLightLevelOfFace(chunk, blockLocation, face);
        addFaceIndices(indices, positions.size(), lightLevel, lightLevel, lightLevel);
        positions.add(point1);
        positions.add(point2);
        positions.add(point3);
        positions.add(point4);
        addSquareNormals(normals, normal);
        addTextureCoordinates(point1,point2,point3,point4, face, blockLocation, chunk, textureCoordinates, block.getSkin(chunk, blockLocation, face).getTextureLocation());
    }
    
    private void addTriangleFace(Vector3f point1, Vector3f point2, Vector3f point3, Vector3f normal, Block.Face face, BlockChunkControl chunk, Block block, Vector3Int blockLocation) {
        float lightLevel = getLightLevelOfFace(chunk, blockLocation, face);
        int indexOffset = positions.size();
        positions.add(point1);
        positions.add(point2);
        positions.add(point3);
        indices.add((short) (indexOffset + 0));
        indices.add((short) (indexOffset + 1));
        indices.add((short) (indexOffset + 2));
        for( int i = 0; i < 3; ++i) {
           lightColors.add(lightLevel);
           lightColors.add(lightLevel);
           lightColors.add(lightLevel);
           lightColors.add(lightLevel);
        }            
        addTriangleNormals(normal);
        addTextureCoordinates_Side(point1, point2, point3, block, chunk, blockLocation, face);
}
    
    @Override
    protected boolean canBeMerged(Block.Face face) {
        return true;
    }

    private void addFaceIndices(LowAllocArray.ShortArray indices, int offset, float lightColor1, float lightColor2, float lightColor3){
        indices.add((short) (offset + 2));
        indices.add((short) (offset + 0));
        indices.add((short) (offset + 1));
        indices.add((short) (offset + 1));
        indices.add((short) (offset + 3));
        indices.add((short) (offset + 2));
        for( int i = 0; i < 4; ++i) {
            lightColors.add(lightColor1);
            lightColors.add(lightColor2);
            lightColors.add(lightColor3);
            lightColors.add(1f);
        }
    }
  
    private void addSquareNormals(LowAllocArray.FloatArray normals, float x, float y, float z) {
        for(int i=0;i<4;i++){
            normals.add(x);
            normals.add(y);
            normals.add(z);
        }
    }
    
    private void addSquareNormals(LowAllocArray.FloatArray normals, Vector3f normal){
        addSquareNormals(normals, normal.getX(), normal.getY(), normal.getZ());
    }
    
    private void addTriangleNormals(Vector3f normal){
        for(int i=0;i<3;i++){
            normals.add(normal.getX());
            normals.add(normal.getY());
            normals.add(normal.getZ());
        }
    }
    
    private void addTextureCoordinates_Side(Vector3f point1, Vector3f point2, Vector3f point3, Block block, BlockChunkControl chunk, Vector3Int blockLocation, Block.Face face){
        BlockSkin_TextureLocation textureLocation = block.getSkin(chunk, blockLocation, face).getTextureLocation();
        //textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 0, 0));
        //textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 1, 0));
        //textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 0.5f, 1));
         switch (face) {
            case Top:
            case Bottom:
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point1.getX() - blockLocation.getX(), point1.getZ() - blockLocation.getZ()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point2.getX() - blockLocation.getX(), point2.getZ() - blockLocation.getZ()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point3.getX() - blockLocation.getX(), point3.getZ() - blockLocation.getZ()));
                break;
            case Front:
            case Back:
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point1.getX() - blockLocation.getX(), point1.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point2.getX() - blockLocation.getX(), point2.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point3.getX() - blockLocation.getX(), point3.getY() - blockLocation.getY()));
                break;
            case Left:
            case Right:
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point1.getZ() - blockLocation.getZ(), point1.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point2.getZ() - blockLocation.getZ(), point2.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point3.getZ() - blockLocation.getZ(), point3.getY() - blockLocation.getY()));
                break;
        }
    
    }
    private float smallest(float a, float b, float c, float d) {
        return Math.min(Math.min(a,b), Math.min(c,d));
    }
    private float largest(float a, float b, float c, float d) {
        return Math.max(Math.max(a,b), Math.max(c,d));
    }
    private void addTextureCoordinates(BlockChunkControl chunk, LowAllocArray.Vector2fArray textureCoordinates, BlockSkin_TextureLocation textureLocation){
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 0, 0));
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 1, 0));
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 0, 1));
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, 1, 1));
    }

    private void addTextureCoordinates(Vector3f point1, Vector3f point2, Vector3f point3, Vector3f point4, Block.Face face, Vector3Int blockLocation, BlockChunkControl chunk, LowAllocArray.Vector2fArray textureCoordinates, BlockSkin_TextureLocation textureLocation){
        switch (face) {
            case Top:
            case Bottom:
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point1.getX() - blockLocation.getX(), point1.getZ() - blockLocation.getZ()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point2.getX() - blockLocation.getX(), point2.getZ() - blockLocation.getZ()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point3.getX() - blockLocation.getX(), point3.getZ() - blockLocation.getZ()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point4.getX() - blockLocation.getX(), point4.getZ() - blockLocation.getZ()));
                break;
            case Front:
            case Back:
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point1.getX() - blockLocation.getX(), point1.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point2.getX() - blockLocation.getX(), point2.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point3.getX() - blockLocation.getX(), point3.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point4.getX() - blockLocation.getX(), point4.getY() - blockLocation.getY()));
                break;
            case Left:
            case Right:
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point1.getZ() - blockLocation.getZ(), point1.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point2.getZ() - blockLocation.getZ(), point2.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point3.getZ() - blockLocation.getZ(), point3.getY() - blockLocation.getY()));
                textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, point4.getZ() - blockLocation.getZ(), point4.getY() - blockLocation.getY()));
                break;
        }
        /*
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, left, top));
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, right, top));
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, left, bottom));
        textureCoordinates.add(getTextureCoordinates(chunk, textureLocation, right, bottom));
        */
    }

    @Override
    public String getTypeName() {
        return "BlockShape_Rhombicuboctahedron";
    }

}
