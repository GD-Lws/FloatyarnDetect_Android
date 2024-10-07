package com.example.myapplication;

import android.util.Log;

public class CameraParameters {
    private long mExposureTime = 7104250L;
    private int mIso = 2200;
    private float mFocusDistance = 4.12f, mZoomRatio = 1.0F;

    private static final long MIN_EXPOSURE_TIME = 100000L;
    private static final long MAX_EXPOSURE_TIME = 32000000000L;
    private static final int MIN_ISO = 100;
    private static final int MAX_ISO = 3200;
    private static final float MIN_FOCUS_DISTANCE = 0.2f;
    private static final float MAX_FOCUS_DISTANCE = 10.0f;

    private static final float MIN_ZOOM_RATIO = 1.0f;
    private static final float MAX_ZOOM_RATIO = 10.0f;
    private static final String TAG = "CAMERAPARMATERS";

    public long[] getExposureTimeRange() {
        return new long[]{MIN_EXPOSURE_TIME, MAX_EXPOSURE_TIME, MAX_EXPOSURE_TIME-MIN_EXPOSURE_TIME};
    }

    public int[] getIsoRange(){
        return new int[]{MIN_ISO, MAX_ISO, MAX_ISO-MIN_ISO};
    }

    public float[] getFocusRange(){
        return new float[]{MIN_FOCUS_DISTANCE, MAX_FOCUS_DISTANCE, MAX_FOCUS_DISTANCE-MIN_FOCUS_DISTANCE};
    }

    public float[] getZoomRatioRange(){
        return new float[]{MIN_ZOOM_RATIO, MAX_ZOOM_RATIO, MAX_ZOOM_RATIO-MIN_ZOOM_RATIO};
    }



    boolean checkCameraParametersValid(long exposureTime, int iso, float focusDistance, float zoomRatio) {
        // 检查曝光时间是否在合法范围内
        if (exposureTime < MIN_EXPOSURE_TIME || exposureTime > MAX_EXPOSURE_TIME) {
            Log.e(TAG,"Input exposureTime Error!" + exposureTime);
            return false;
        }
        // 检查ISO是否在合法范围内
        if (iso < MIN_ISO || iso > MAX_ISO) {
            Log.e(TAG,"Input ISO Error!" + iso);
            return false;
        }
        // 检查焦距是否在合法范围内
        if (focusDistance < MIN_FOCUS_DISTANCE || focusDistance > MAX_FOCUS_DISTANCE) {
            Log.e(TAG,"Input focusDistance Error!" + focusDistance);
            return false;
        }
        // 检查缩放比例是否在合法范围内
        if (zoomRatio < MIN_ZOOM_RATIO || zoomRatio > MAX_ZOOM_RATIO) {
            Log.e(TAG,"Input zoomRatio Error!" + zoomRatio);
            return false;
        }
        // 所有参数都在合法范围内
        return true;
    }

    public boolean assignedCameraParameters(long exposureTime, int iso, float focusDistance, float zoomRatio){
        if (checkCameraParametersValid(exposureTime, iso, focusDistance, zoomRatio)){
            mExposureTime = exposureTime;
            mIso = iso;
            mFocusDistance = focusDistance;
            mZoomRatio = zoomRatio;
            return true;

        }else {
            return false;
        }
    }

    public String getCameraParametersStr(){
        String str = String.valueOf(mExposureTime) + "," + String.valueOf(mIso) + "," + mFocusDistance + "," + mIso;
        return str;
    }

    public long getExposureTime(){
        return mExposureTime;
    }
    public int getIso(){
        return mIso;
    }
    public float getFocusDistance(){
        return mFocusDistance;
    }
    public float getZoomRatio(){
        return mZoomRatio;
    }

    public boolean setExposureTime(long ExposureTime){
        if (checkCameraParametersValid(ExposureTime, mIso, mFocusDistance, mZoomRatio)){
            mExposureTime = ExposureTime;
            return true;
        }else {
            return false;
        }
    }
    public boolean setIso(int Iso){
        if (checkCameraParametersValid(mExposureTime, Iso, mFocusDistance, mZoomRatio)){
            mIso = Iso;
            return true;
        }else {
            return false;
        }
    }
    public boolean setFocusDistance(float focusDistance){
        if (checkCameraParametersValid(mExposureTime, mIso, focusDistance, mZoomRatio)){
            mFocusDistance = focusDistance;
            return true;
        }else {
            return false;
        }
    }
    public boolean setZoomRatio(float ZoomRatio){
        if (checkCameraParametersValid(mExposureTime, mIso, mFocusDistance, ZoomRatio)){
            mZoomRatio = ZoomRatio;
            return true;
        }else {
            return false;
        }
    }
}
