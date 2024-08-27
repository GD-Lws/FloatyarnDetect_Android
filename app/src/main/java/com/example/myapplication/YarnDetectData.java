package com.example.myapplication;

import android.content.ContentValues;

public class YarnDetectData {
    private String key;
    private String value;
    private float veloctiy;
    private int lum;
    private int region;

    public YarnDetectData(String key, String value,float velocity, int lum, int region) {
        this.key = key;
        this.value = value;
        this.veloctiy = velocity;
        this.lum = lum;
        this.region = region;
    }
    public ContentValues getContentValues(){
        ContentValues values = new ContentValues();
        values.put("KEY", this.key);
        values.put("VALUE", this.value);
        values.put("Velocity", this.veloctiy);
        values.put("LUM", this.lum);
        values.put("REGION", this.region);
        return values;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }

    public float getVeloctiy() {return veloctiy;}
    public int getLum() { return lum; }
    public int getRegion() { return region; }

    @Override
    public String toString() {
        return "Key: " + key + ", Value: " + value + ",Velocity" + veloctiy + ", Lum: " + lum + ", Region: " + region;
    }
}