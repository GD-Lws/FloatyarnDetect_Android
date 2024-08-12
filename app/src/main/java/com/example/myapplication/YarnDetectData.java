package com.example.myapplication;

import android.content.ContentValues;

public class YarnDetectData {
    private String key;
    private String value;
    private int lum;
    private int region;

    public YarnDetectData(String key, String value, int lum, int region) {
        this.key = key;
        this.value = value;
        this.lum = lum;
        this.region = region;
    }
    public ContentValues getContentValues(){
        ContentValues values = new ContentValues();
        values.put("KEY", this.key);
        values.put("VALUE", this.value);
        values.put("LUM", this.lum);
        values.put("REGION", this.region);
        return values;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public int getLum() { return lum; }
    public int getRegion() { return region; }

    @Override
    public String toString() {
        return "Key: " + key + ", Value: " + value + ", Lum: " + lum + ", Region: " + region;
    }
}