package com.example.myapplication;

public class CameraParameters {
    private long mExposureTime = new Long(7104250);
    private int mIso = 1200;
    private float mFocusDistance = 4.12f, mZoomRatio = 5.0F;

    private static final long MIN_EXPOSURE_TIME = 100000L;
    private static final long MAX_EXPOSURE_TIME = 32000000000L;
    private static final int MIN_ISO = 100;
    private static final int MAX_ISO = 3200;
    private static final float MIN_FOCUS_DISTANCE = 0.2f;
    private static final float MAX_FOCUS_DISTANCE = 10.0f;

    private static final float MIN_ZOOM_RATIO = 1.0f;
    private static final float MAX_ZOOM_RATIO = 10.0f;

    boolean checkCameraParametersValid(long exposureTime, int iso, float focusDistance, float zoomRatio) {
        // 检查曝光时间是否在合法范围内
        if (exposureTime < MIN_EXPOSURE_TIME || exposureTime > MAX_EXPOSURE_TIME) {
            return false;
        }
        // 检查ISO是否在合法范围内
        if (iso < MIN_ISO || iso > MAX_ISO) {
            return false;
        }
        // 检查焦距是否在合法范围内
        if (focusDistance < MIN_FOCUS_DISTANCE || focusDistance > MAX_FOCUS_DISTANCE) {
            return false;
        }
        // 检查缩放比例是否在合法范围内
        if (zoomRatio < MIN_ZOOM_RATIO || zoomRatio > MAX_ZOOM_RATIO) {
            return false;
        }
        // 所有参数都在合法范围内
        return true;
    }

    public boolean assignedCameraParameters(long exposureTime, int iso, float focusDistance, float zoomRatio){
        if (!checkCameraParametersValid(exposureTime, iso, focusDistance, zoomRatio)){
            return false;
        }else {
            mExposureTime = exposureTime;
            mIso = iso;
            mFocusDistance = focusDistance;
            mZoomRatio = zoomRatio;
            return true;
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
