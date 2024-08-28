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
        return "Key: " + key + ", Value: " + value + ",Velocity" + velocity + ", Lum: " + lum + ", Region: " + region;
    }
}