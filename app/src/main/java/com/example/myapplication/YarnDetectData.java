package com.example.myapplication;

import android.content.ContentValues;

public class YarnDetectData {
    private String key;
    private String value;
    private float velocity;
    private int lum;
    private int region;

    public YarnDetectData(String key, String value,float velocity, int lum, int region) {
        this.key = key;
        this.value = value;
        this.velocity = velocity;
        this.lum = lum;
        this.region = region;
    }
    public ContentValues getContentValues(){
        ContentValues values = new ContentValues();
        values.put("KEY", this.key);
        values.put("VALUE", this.value);
        values.put("Velocity", this.velocity);
        values.put("LUM", this.lum);
        values.put("REGION", this.region);
        return values;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public float getVelocity() {return velocity;}
    public int getLum() { return lum; }
    public int getRegion() { return region; }

    public void setVelocityAndRow(String setRow, float setVelocity){
        key = setRow;
        velocity = setVelocity;
    }

    @Override
    public String toString() {
        return "Key\r\n" + key + "\r\nValue\r\n" + value + "\r\nVel\r\n" + velocity + "\r\nLum\r\n" + lum + "\r\nReg\r\n" + region;
    }
    public byte[] toByteArr() {
        String resString = toString();
        byte[] resByte = resString.getBytes();

        int length = resByte.length;
        int padding = 8 - (length % 8);

        if (padding < 8) { // Only pad if the length is not already a multiple of 8
            byte[] paddedByte = new byte[length + padding];
            System.arraycopy(resByte, 0, paddedByte, 0, length);
            return paddedByte; // The new array is returned with padding
        } else {
            return resByte; // No padding needed, return the original array
        }
    }
}