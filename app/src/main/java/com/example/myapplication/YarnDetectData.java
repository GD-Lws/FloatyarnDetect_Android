package com.example.myapplication;

import android.content.ContentValues;

import java.util.Map;

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

    public static YarnDetectData fromMap(Map<String, String> map) {
        String key = getStringValue(map, "KEY");
        String value = getStringValue(map, "VALUE");
        String velocityStr = getStringValue(map, "Velocity");
        String lumStr = getStringValue(map, "LUM");
        String regionStr = getStringValue(map, "REGION");

        float velocity = parseFloat(velocityStr, 0.0f);
        int lum = parseInt(lumStr, 0);
        int region = parseInt(regionStr, 0);
        return new YarnDetectData(key, value, velocity, lum, region);
    }

    private static String getStringValue(Map<String, String> map, String key) {
        return map.getOrDefault(key, null);
    }

    private static float parseFloat(String value, float defaultValue) {
        if (value != null && !value.isEmpty()) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException e) {
                // 日志记录或错误处理（例如：使用日志框架记录错误）
                System.err.println("Invalid float value: " + value);
            }
        }
        return defaultValue;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // 日志记录或错误处理（例如：使用日志框架记录错误）
                System.err.println("Invalid integer value: " + value);
            }
        }
        return defaultValue;
    }


    public byte[] getByteArray(int index) {
        int mul = 3;
        byte[] index_byte = padWithSpaces(("I:" + String.valueOf(index)).getBytes());
        byte[] key_byte = padWithSpaces(key.getBytes());
        byte[] value_byte = padWithSpaces(value.getBytes());
        byte[] velocity_byte = padWithSpaces(String.valueOf(velocity).getBytes());
        byte[] lum_byte = padWithSpaces(String.valueOf(lum).getBytes());
        byte[] region_byte = padWithSpaces(String.valueOf(region).getBytes());

        // 直接计算总长度
        byte[] result = new byte[mul * 8]; // 每个部分长度为8，总共5个部分
        int offset = 0;
        System.arraycopy(key_byte, 0, result, offset, index_byte.length);
        offset += 8;
        System.arraycopy(key_byte, 0, result, offset, key_byte.length);
        offset += 8;
        System.arraycopy(value_byte, 0, result, offset, value_byte.length);
//        offset += 8;
//        System.arraycopy(velocity_byte, 0, result, offset, velocity_byte.length);
//        offset += 8;
//        System.arraycopy(lum_byte, 0, result, offset, lum_byte.length);
//        offset += 8;
//        System.arraycopy(region_byte, 0, result, offset, region_byte.length);
        return result;
    }
    private byte[] padWithSpaces(byte[] input) {
        // 创建一个新的字节数组，并填充后面的0x00
        byte[] padded = new byte[8];
        System.arraycopy(input, 0, padded, 0, Math.min(input.length, 8));
        return padded;
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