/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Semaphore;

/**
 *
 * @author Carl
 */
public class Vector3Int{
    private static Semaphore sem = new Semaphore(1, false); 
    private static int MAX_POOL_SIZE = 100000;
    private static Vector3Int[] pool = new Vector3Int[MAX_POOL_SIZE];
    private static int length = 0;
    private static Vector3Int poll() {
        Vector3Int returnVal = null;
        try {
            sem.acquire();
            if (length > 0) {
                returnVal = pool[--length];
            }
            sem.release();
        } catch (InterruptedException ex) {            
            System.err.println("Failed to aquire Vector3Int semaphore");
        }
        return returnVal;
    }
    private static void offer(Vector3Int v) {
        try {
            sem.acquire();
            if (length < MAX_POOL_SIZE) {
               pool[length++] = v;
            }
            sem.release();
        } catch (InterruptedException ex) {            
            System.err.println("Failed to aquire Vector3Int semaphore");
        }
    }
    // For debugging double deallocations if needed.
    //private static ConcurrentLinkedQueue<Vector3Int> pool = new ConcurrentLinkedQueue<Vector3Int>();
    public static Vector3Int create() {
        return create(0,0,0);
    }
    public static Vector3Int create(int x, int y, int z) {
        Vector3Int reuse = /*pool.*/poll();
        if (reuse != null) {
            reuse.set(x,y,z);
            return reuse;
        } else {
            return new Vector3Int(x,y,z);
        }
    }
    public static void dispose(Vector3Int v) {
        //chunkAccessMutex.lock();
        //v.x = v.counter;
        //v.y = v.counter;
        //v.z = v.counter;
        //if (pool.contains(v)){
        //    System.err.println("DISPOSED TWICE");
        //}
        //chunkAccessMutex.unlock();
        /*pool.*/offer(v);
    }
    private Vector3Int(int x, int y, int z){
        this();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private Vector3Int(){
        //chunkAccessMutex.lock();
        //counter = ++gCounter;
        //chunkAccessMutex.unlock();
    }
    private int x;
    private int y;
    private int z;
    //static private int gCounter = 0;
    //private int counter;

    public int getX(){
        return x;
    }

    public Vector3Int setX(int x){
        this.x = x;
        return this;
    }

    public int getY(){
        return y;
    }

    public Vector3Int setY(int y){
        this.y = y;
        return this;
    }

    public int getZ(){
        return z;
    }

    public Vector3Int setZ(int z){
        this.z = z;
        return this;
    }
    
    public Vector3Int set(Vector3Int vector3Int){
        return set(vector3Int.getX(), vector3Int.getY(), vector3Int.getZ());
    }

    public Vector3Int set(int x, int y, int z){
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }
    
    public Vector3Int add(Vector3Int vector3Int){
        return add(vector3Int.getX(), vector3Int.getY(), vector3Int.getZ());
    }
    
    public Vector3Int add(int x, int y, int z){
        return create(this.x + x, this.y + y, this.z + z);
    }
    
    public Vector3Int addLocal(Vector3Int vector3Int){
       return addLocal(vector3Int.getX(), vector3Int.getY(), vector3Int.getZ());
    }
    
    public Vector3Int addLocal(int x, int y, int z){
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }
    
    public Vector3Int subtract(Vector3Int vector3Int){
        return subtract(vector3Int.getX(), vector3Int.getY(), vector3Int.getZ());
    }
    
    public Vector3Int subtract(int x, int y, int z){
        return create(this.x - x, this.y - y, this.z - z);
    }
    
    public Vector3Int subtractLocal(Vector3Int vector3Int){
        return subtractLocal(vector3Int.getX(), vector3Int.getY(), vector3Int.getZ());
    }
    
    public Vector3Int subtractLocal(int x, int y, int z){
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }
    
    public Vector3Int negate(){
        return mult(-1);
    }
    
    public Vector3Int mult(int factor){
        return mult(factor, factor, factor);
    }
    
    public Vector3Int mult(int x, int y, int z){
        return create(this.x * x, this.y * y, this.z * z);
    }

    public Vector3Int mult(Vector3Int right){
        return create(this.x * right.x, this.y * right.y, this.z * right.z);
    }
    
    public Vector3Int negateLocal(){
        return multLocal(-1);
    }
    
    public Vector3Int multLocal(int factor){
        return multLocal(factor, factor, factor);
    }
    
    public Vector3Int multLocal(int x, int y, int z){
        this.x *= x;
        this.y *= y;
        this.z *= z;
        return this;
    }

    @Override
    public int hashCode()
    {
        return (x << 8) ^ (z << 16) ^ (y << 24);
    }

    @Override
    public Vector3Int clone(){
        return create(x, y, z);
    }

    @Override
    public boolean equals(Object object){
        if(object instanceof Vector3Int){
            Vector3Int vector3Int = (Vector3Int) object;
            return ((x == vector3Int.getX()) && (y == vector3Int.getY()) && (z == vector3Int.getZ()));
        }
        return false;
    }

    @Override
    public String toString(){
        return "[Vector3Int x=" + x + " y=" + y + " z=" + z + "]";
    }
}
