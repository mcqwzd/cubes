/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cubes;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import java.lang.reflect.Array;

/**
 *
 * @author funin_000
 */
public class LowAllocArray {
    public static class ShortArray {
        private final short data[];
        private int length = 0;
        public ShortArray () {
            data = new short[Short.MAX_VALUE];
        }
        public ShortArray (int size) {
            data = new short[size];
        }
         public void add(short i) {
            data[length++] = i;
        }
        public void reset() {
            length = 0;
        }
        public int size() {
            return length;
        }
        public short[] toArray() {
            short returnData[] = new short[length];
            System.arraycopy(data,0,returnData,0,length);
            return returnData;
        }
    }
    public static class FloatArray {
        private final float data[];
        private int length = 0;
        public FloatArray () {
            data = new float[Short.MAX_VALUE];
        }
        public FloatArray (int size) {
            data = new float[size];
        }
        public void add(float i) {
            data[length++] = i;
        }
        public void reset() {
            length = 0;
        }
        public int size() {
            return length;
        }
        public float[] toArray() {
            float returnData[] = new float[length];
            System.arraycopy(data,0,returnData,0,length);
            return returnData;
        }
    }
    public static class Vector3fArray {
        private final Vector3f data[];
        private int length = 0;
        public Vector3fArray () {
            data = new Vector3f[Short.MAX_VALUE];
        }
        public void add(Vector3f i) {
            data[length++] = i;
        }
        public void reset() {
            length = 0;
        }
        public int size() {
            return length;
        }
        public Vector3f[] toArray() {
            Vector3f returnData[] = new Vector3f[length];
            System.arraycopy(data,0,returnData,0,length);
            return returnData;
        }
    }
    public static class Vector2fArray {
        private final Vector2f data[];
        private int length = 0;
        public Vector2fArray () {
            data = new Vector2f[Short.MAX_VALUE];
        }
        public void add(Vector2f i) {
            data[length++] = i;
        }
        public void reset() {
            length = 0;
        }
        public int size() {
            return length;
        }
        public Vector2f[] toArray() {
            Vector2f returnData[] = new Vector2f[length];
            System.arraycopy(data,0,returnData,0,length);
            return returnData;
        }
    }
    public static class LightQueueElementArray {
        private final LightQueueElement data[];
        private int length = 0;
        public LightQueueElementArray (int maxSize) {
            data = new LightQueueElement[maxSize * 2];
        }
        public void add(LightQueueElement i) {
            data[length++] = i;
        }
        public void reset() {
            length = 0;
        }
        public LightQueueElement get(int i) {
            return data[i];
        }
        public int size() {
            return length;
        }
    }
    
}
