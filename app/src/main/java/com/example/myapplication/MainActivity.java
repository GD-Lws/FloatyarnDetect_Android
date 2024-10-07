package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.util.Log;
import android.util.Range;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity implements SerialInputOutputManager.Listener, View.OnClickListener {

//    native-lib load
    static {
        System.loadLibrary("myapplication");
    }
    public native void matMerge(long matTarget, long matOut, long matInArray1, long matInArray2, int[] roiArray);
    public native YarnDetectData detectYarnInImage(long matIn, long matOut, int[] roi, float[] detectPar, String saveFilePath);
    private native void bitmapDrawRoiRange(Bitmap bitmapIn, Bitmap bitmapOut, int[] roi1, int[] roi2);

    /***************   Serial Value   *************************************/
    static class SerListItem {
        UsbDevice device;
        int port;
        UsbSerialDriver driver;

        SerListItem(UsbDevice device, int port, UsbSerialDriver driver) {
            this.device = device;
            this.port = port;
            this.driver = driver;
        }
    }

    private enum serialStatus {
        CLOSE,
        OPEN,
        READY,
        ACTIVE,
        EDIT,
        PIC,
        MSG_SEND,
        SQL_EDIT
    }

    private enum operateMode {
        Detect,
        Compare,
        Record
    }

    private final AtomicReference<serialStatus> serNowStatus = new AtomicReference<>(serialStatus.CLOSE);
    private operateMode detectMode = operateMode.Detect;

    private SerialInputOutputManager usbIoManager;
    private final ArrayList<SerListItem> serListItems = new ArrayList<>();
    private ArrayAdapter<SerListItem> listAdapter;
    private List<YarnDetectData> listYarnData;
    private UsbManager usbManager = null;
    private UsbSerialPort usbSerialPort = null;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private Button bt_ser_sqlite;
    private Button bt_ser_params;
    private TextView tv_ser_rec, tv_ser_state, tv_camera_state;
    private static boolean flag_serConnect = false;
    private static final int WRITE_WAIT_MILLIS = 2000;
    private int linesPerChunks = 30;
    private static final int CHUNK_SIZE = 1024; // 定义每个包的大小

/************************************************************************/

    /***************   Camera Value   *************************************/

    private CameraCaptureSession mCaptureSession;
    private CameraParameters mCameraParameters;
    private CameraCharacteristics cameraCharacteristics;
    private CameraManager cameraManager;
    private HandlerThread mCameraSessionThread, mImageThread, mCameraStateThread;
    private Handler mCameraSessionHandler, mImageHandler, mCameraStateHandler;
    private String cameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    //  用于控制多个线程对共享资源的访问，以确保同一时间只有一个线程可以访问相机设备
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private ImageReader mImageReader;
    private TextureView textureView_resultView;
    private boolean resultViewReadyFlag = false;

    private boolean flagDetect = false;
    private boolean flagGetImage = false;
    private boolean flagCameraOpen = false;
    private Button bt_ser_camera, bt_ser_roi;
//  用于获取数据库存有的表名
    private List<String> sendTableNameList;

    //  检测参数
    private int[] arrRoi1 = new int[]{512, 200, 722, 380};
    private int[] arrRoi2 = new int[]{512, 400, 662, 580};
    private int cameraViewWidth = 1920;
    private int cameraViewHeight = 1080;
    private float[] arrDetectPar = new float[]{40.0f, 255.0f, 0.4f};
    private int recKnitRow = 0;
    private float recKnitVelocity = 0.0f;
    /************************************************************************/

    // 工具类
    private UtilTool myUtil;
    // DriverDebug
    private static final String TAG = "ToolDebug";
    private static final String DAG = "DetectDebug";
    private static final String STG = "StateDebug";
    private static final String QTG = "SQLDebug";
    private static final String EDG = "ErrorDebug";

    private String saveFilePath;
    //    传输图标响应标志
    private volatile boolean ackReceived = false;
    private long recTimeOut = 1000;
    private String strParamsTableName = "CParam";
/************************************************************************/

    /***************   Transmission Identifier   *************************************/
    private Thread heartbeatThread;

    /***************** SQL ********************************************/

    private Button bt_sql_info;
    private SQLiteTool dbTool;
    private String knitTableName = "CParam";
    /************************************************************************/
    private  PowerManager.WakeLock wakeLock;
    private SoundStateMachine soundStateMachine;
    private byte[] jpgByteArray;
    private String imageLenStr;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MyApp::CameraWakeLock");
        setContentView(R.layout.activity_main);
        myUtil = new UtilTool();
        dbTool = new SQLiteTool(this);
        mCameraParameters = new CameraParameters();
        soundStateMachine = new SoundStateMachine(this);
        Intent serviceIntent = new Intent(this, MyForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        saveFilePath = getExternalCacheDir().getAbsolutePath() + "/";
        OpenCVLoader.initDebug(false);
        if (myUtil.checkPermissions(MainActivity.this)) {
            Toast.makeText(this, "浮纱检测程序", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "检测权限未授权", Toast.LENGTH_SHORT).show();
        }
        InitView();
        cameraManager = (CameraManager) getApplication().getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "YarnDetect Program Start");
        serRefresh();
        if(dbTool.isTableExists(strParamsTableName)){
            sqlGetCameraParameter(strParamsTableName);
        }else {
            sqlCreateTable(strParamsTableName);
        }
    }

    private void InitView() {
        listAdapter = new ArrayAdapter<SerListItem>(MainActivity.this, 0, serListItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                SerListItem item = serListItems.get(position);
                if (view == null)
                    view = MainActivity.this.getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                if (item.driver == null)
                    text1.setText("<no driver>");
                else if (item.driver.getPorts().size() == 1)
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver", ""));
                else
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver", "") + ", Port " + item.port);
                text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));
                return view;
            }
        };
        ListView lv_device = findViewById(R.id.lv_Ser_derive);
        lv_device.setAdapter(listAdapter);
        lv_device.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MainActivity.SerListItem item = (SerListItem) serListItems.get(position);
                if (item.driver == null) {
                    Toast.makeText(MainActivity.this, "no driver", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Select driverId" + item.device.getDeviceId() + "port" + item.port, Toast.LENGTH_SHORT).show();
                }
            }
        });
        Button bt_ser_refresh = findViewById(R.id.bt_Ser_refresh);
        Button bt_ser_detect = findViewById(R.id.bt_Ser_detect);
        Button bt_ser_ready = findViewById(R.id.bt_Ser_Ready);
        Button bt_ser_connect = findViewById(R.id.bt_Ser_open);
        Button bt_ser_disconnect = findViewById(R.id.bt_Ser_close);
        Button bt_ser_send = findViewById(R.id.bt_Ser_send);
        bt_ser_camera = findViewById(R.id.bt_Ser_camera);
        bt_ser_roi = findViewById(R.id.bt_Ser_roi);
        bt_sql_info = findViewById(R.id.bt_Ser_sql);
        bt_ser_params = findViewById(R.id.bt_Ser_params);

        tv_ser_rec = findViewById(R.id.tv_Ser_rec);
        tv_ser_state = findViewById(R.id.tv_ser_State);
        tv_camera_state = findViewById(R.id.tv_camera_State);
        textureView_resultView = findViewById(R.id.textureView_resultShow);

        bt_sql_info.setOnClickListener(this);
        bt_ser_refresh.setOnClickListener(this);
        bt_ser_connect.setOnClickListener(this);
        bt_ser_disconnect.setOnClickListener(this);
        bt_ser_camera.setOnClickListener(this);
        bt_ser_roi.setOnClickListener(this);
        bt_ser_params.setOnClickListener(this);
        bt_ser_ready.setOnClickListener(this);
        bt_ser_detect.setOnClickListener(this);

        textureView_resultView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                resultViewReadyFlag = true;
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                resultViewReadyFlag = false;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.bt_Ser_refresh) {
            serRefresh();
            Toast.makeText(MainActivity.this, "串口设备刷新", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.bt_Ser_open) {
            serConnect();
        } else if (id == R.id.bt_Ser_close) {
            serDisconnect();
        } else if (id == R.id.bt_Ser_send) {
            serStrSend("SerialTest");
        } else if (id == R.id.bt_Ser_camera) {
            transStatus(serialStatus.PIC);
            serOpenCamera();
        } else if (id == R.id.bt_Ser_roi) {
            flagGetImage = true;
        } else if (id == R.id.bt_Ser_sql) {
            listYarnData = dbTool.sqlGetTableData(knitTableName);
            for (int index = 0; index < listYarnData.size(); index++) {
                YarnDetectData data = listYarnData.get(index);
                byte[] dataByte = data.getByteArray(index);
                Log.d("SQLiteTool", "Index: " + index + " " + dataByte.toString());
            }
            serSendYarnData(listYarnData);
        } else if (id == R.id.bt_Ser_Ready) {
            resetFlag();
            transStatus(serialStatus.READY);
            serOpenCamera();
        } else if (id == R.id.bt_Ser_detect) {
            transStatus(serialStatus.ACTIVE);
            flagDetect = true;
        } else if (id == R.id.bt_Ser_params) {
            showPopupCameraParamsWindow();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (wakeLock != null) {
            wakeLock.acquire();
        }
        serRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 释放 WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    //  通过串口开启相机
    private void serOpenCamera() {
        if (flagCameraOpen) {
            Log.e(TAG, "摄像头已开启");
            return;
        }
        startBackgroundThread();
        mImageReader = ImageReader.newInstance(cameraViewWidth, cameraViewHeight, ImageFormat.YUV_420_888, 52);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mImageHandler);
        cameraOpen(cameraViewWidth, cameraViewHeight);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_camera_state.setText("Camera Open.");
            }
        });
        flagCameraOpen = true;
    }


    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image readerImage = reader.acquireLatestImage();
            if (readerImage != null) {
                Image.Plane[] planes = readerImage.getPlanes();
                // 获取 Y 分量的信息
                Image.Plane yPlane = planes[0];
                ByteBuffer yBuffer = yPlane.getBuffer();
//                int yPixelStride = yPlane.getPixelStride();
                int yRowStride = yPlane.getRowStride();
                int yWidth = readerImage.getWidth();
                int yHeight = readerImage.getHeight();

                // 创建字节数组来存储 Y 数据
                byte[] yData = new byte[yBuffer.remaining()];
                yBuffer.get(yData);

                // 创建 Mat 对象
                Mat yMat = new Mat(yHeight + yHeight / 2, yWidth, CvType.CV_8UC1);
                int offset = 0;
                for (int row = 0; row < yHeight; row++) {
                    yMat.put(row, 0, yData, offset, yWidth);
                    offset += yRowStride;
                }

                // 转换为灰度图
                Mat grayscaleMat = new Mat();
                Imgproc.cvtColor(yMat, grayscaleMat, Imgproc.COLOR_YUV2GRAY_NV21);
//                Mat roiMat = new Mat();
//                matDrawRoiRange(grayscaleMat.getNativeObjAddr(), roiMat.getNativeObjAddr(), arrRoi1, arrRoi2);
                Bitmap outputBitmap = Bitmap.createBitmap(yWidth, yHeight, Bitmap.Config.ARGB_8888);
                // 将灰度图像复制到 Bitmap 中
                Utils.matToBitmap(grayscaleMat, outputBitmap);
                Bitmap roiBitmap = Bitmap.createBitmap(yWidth, yHeight, Bitmap.Config.ARGB_8888);
                bitmapDrawRoiRange(outputBitmap, roiBitmap, arrRoi1, arrRoi2);
                Bitmap resultBitmap = Bitmap.createBitmap(yWidth, yHeight, Bitmap.Config.ARGB_8888);

                if (flagGetImage) {
                    jpgByteArray = myUtil.saveBitmapAsJpg(roiBitmap);
                    int byteSendLen = jpgByteArray.length + 16;
                    imageLenStr ="L"+ myUtil.paddingString(String.valueOf(byteSendLen), 7);
                    sendImageData(1);
                    flagGetImage = false;
                }
                if (flagDetect){
                    byte[] detectArr = myUtil.byteArrRES;
                    byte[] rowArr = myUtil.convertValue2ByteArr(recKnitRow + "",6);
                    System.arraycopy(rowArr, 0, detectArr, 1, 6);
                    Mat detectMat_1 = new Mat();
                    Mat detectMat_2 = new Mat();
                    Mat resultMat = new Mat();
                    int[] tempRoiArray = new int[8];
                    System.arraycopy(arrRoi1, 0, tempRoiArray, 0, 4);
                    System.arraycopy(arrRoi2, 0, tempRoiArray, 4, 4);
                    YarnDetectData DY1 = detectYarnInImage(grayscaleMat.getNativeObjAddr(), detectMat_1.getNativeObjAddr(), arrRoi1, arrDetectPar, saveFilePath + "\\" + knitTableName + "\\roi_1" + "\\" + recKnitRow + "_" + recKnitVelocity);
                    YarnDetectData DY2 = detectYarnInImage(grayscaleMat.getNativeObjAddr(), detectMat_2.getNativeObjAddr(), arrRoi2, arrDetectPar, saveFilePath + "\\" + knitTableName + "\\roi_2" + "\\" + recKnitRow + "_" + recKnitVelocity);
                    matMerge(grayscaleMat.getNativeObjAddr(), resultMat.getNativeObjAddr(), detectMat_1.getNativeObjAddr(), detectMat_2.getNativeObjAddr(), tempRoiArray);
                    Utils.matToBitmap(resultMat, resultBitmap);
                    byte[] tempKnitRowArr = myUtil.convertValue2ByteArr(recKnitRow+"", 6);
                    Log.d(DAG, "tempKnitRowArr:" + Arrays.toString(tempKnitRowArr));
                    System.arraycopy(tempKnitRowArr, 0, detectArr, 1, 5);
                    switch (detectMode){
                        case Detect:{
                            if (DY1.getValue() == "0" && DY2.getValue() == "0" ){
                                detectArr[7] = 0x48;
                            }else {
                                detectArr[7] = 0x49;
                            }
                        }break;
                        //  参数保存
                        case Record:{
                            DY1.setVelocityAndRow(recKnitRow +"_1", recKnitVelocity);
                            DY2.setVelocityAndRow(recKnitRow +"_2", recKnitVelocity);
                            YarnDetectData[] dyArr = {DY1, DY2};
                            sqlInsertYarnData(dyArr);
                            if (DY1.getValue() == "0" && DY2.getValue() == "0" ){
                                detectArr[7] = 0x48;
                            }else {
                                detectArr[7] = 0x49;
                            }
                        }break;
                        case Compare:{

                        }break;
                    }
                    if (flag_serConnect)serByteSend(detectArr);
                    Log.d(DAG, "DetectData:" + Arrays.toString(detectArr));
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (resultViewReadyFlag) {
                            if (flagDetect){
                                drawBitmapToTextureView(resultBitmap);
                            }else {
                                drawBitmapToTextureView(roiBitmap);
                            }
                        }
                    }
                });
                readerImage.close();
            }
        }
    };

    private void sendImageData(int stage){
        if (stage == 1) {
            Log.i(STG, "Send Image Len:" + imageLenStr);
            serStrSend(imageLenStr);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "length:" + jpgByteArray.length, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (stage == 2) {
            serialStatus tempStatus = serNowStatus.get();
            sendByteArrayWithAck(jpgByteArray,tempStatus);
        }
    }

    private void drawBitmapToTextureView(Bitmap bitmap) {
        if (textureView_resultView.getSurfaceTexture() == null) {
            return;
        }

        Canvas canvas = textureView_resultView.lockCanvas();
        if (canvas != null) {
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(bitmap, 0, 0, null);
            } finally {
                textureView_resultView.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void startBackgroundThread() {
        // 相机线程
        if (mCameraSessionThread == null) {
            mCameraSessionThread = new HandlerThread("CameraSessionBackground");
            mCameraSessionThread.start();
            mCameraSessionHandler = new Handler(mCameraSessionThread.getLooper());
        }
        if (mCameraStateThread == null) {
            mCameraStateThread = new HandlerThread("CameraStateBackground");
            mCameraStateThread.start();
            mCameraStateHandler = new Handler(mCameraStateThread.getLooper());
        }
        // ImageReader线程
        if (mImageThread == null) {
            mImageThread = new HandlerThread("ImageBackground");
            mImageThread.start();
            mImageHandler = new Handler(mImageThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if (mCameraSessionThread != null) {
            mCameraSessionThread.quitSafely();
            try {
                mCameraSessionThread.join();
                mCameraSessionThread = null;
                mCameraSessionHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mCameraStateThread != null) {
            mCameraStateThread.quitSafely();
            try {
                mCameraStateThread.join();
                mCameraStateThread = null;
                mCameraStateHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mImageThread != null) {
            mImageThread.quitSafely();
            try {
                mImageThread.join();
                mImageThread = null;
                mImageHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            // Retrieve exposure time from the result
            float real_exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Range real_fps_Range = result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE);
            float real_Iso_value = result.get(CaptureResult.SENSOR_SENSITIVITY);
            float real_focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
//            if (real_exposureTime != 0) {
//                Log.d("camera_info", "ETR: " + real_exposureTime + " FPS: " + real_fps_Range + " FDR: " + real_focusDistance + " ISO: " + real_Iso_value);
//            }
        }
    };

    private void setUpCameraPar() {
        Range<Integer> fpsRange = new Range(240, 240);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mCameraParameters.getFocusDistance());
        mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mCameraParameters.getExposureTime());
        mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, myUtil.getRect(cameraCharacteristics, mCameraParameters.getZoomRatio()));
        mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mCameraParameters.getIso());
        Log.d(TAG, "相机参数设置");
    }

    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    private void startPreview() {
        if (null == mCameraDevice) {
            Log.e(TAG, "CameraDevice is null");
            return;
        }
        try {
            Log.i(TAG, "申请预览");
            // 设置为手动模式
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            setUpCameraPar();
//            图像处理
            mPreviewBuilder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    Log.d(TAG, "createCaptureSession onConfigured");
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mCameraSessionHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(getApplicationContext(), "Failed", Toast.LENGTH_SHORT).show();
                }
            }, mCameraSessionHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;

    private void serRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    private void cameraOpen(final int width, final int height) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                    float maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    Log.d(TAG, "maxZoom:" + maxZoom);
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    // 检查权限并请求权限，需要在主线程中进行
                    if ((checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ||
                            (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                        Log.d(TAG, "No camera and storage permission");
                        // 切换到主线程请求权限
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                requestPermissions(new String[]{android.Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                            }
                        });
                        return;
                    }
                    Log.d(TAG, "开启相机");
                    // 切换到主线程打开相机
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    return;
                                }
                                cameraManager.openCamera(cameraId, cameraStateCallback, mCameraStateHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

        private void cameraClose() {
            try {
                mCameraOpenCloseLock.acquire();
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (mCaptureSession != null) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera closing.");
            } finally {
                mCameraOpenCloseLock.release();
            }
            stopBackgroundThread();
            flagCameraOpen = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_camera_state.setText("Camera Close.");
                }
            });
        }

/************************************************************************/

        /***************   Serial Control   *************************************/
        @Override
        public void onNewData(byte[] bytes) {
            String rec_msg = new String(bytes);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 设置时间格式
            String timestamp = sdf.format(new Date()); // 获取当前时间并格式化为字符串

            // 将时间戳添加到消息字符串前或后
            String RecMsg = timestamp + " - " + rec_msg; // 例如，在时间戳后添加消息
            Log.d(TAG,"RecMsg:" + RecMsg);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 更新TextView显示新的消息（包含时间戳）
                    tv_ser_rec.setText(RecMsg);
                }
            });
            try {
                executeAction(bytes);
            } catch (InterruptedException | CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }

        //    不要操作UI线程，会闪退
        @Override
        public void onRunError(Exception e) {
            serDisconnect();
            stopBackgroundThread();
            resetFlag();
            cameraClose();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    serRefresh();
                }
            });
        }

        private void resetFlag(){
            flagDetect = false;
            flagGetImage = false;
            flagCameraOpen = false;
        }

        void serRefresh() {
            usbManager = (UsbManager) MainActivity.this.getSystemService(Context.USB_SERVICE);
            UsbSerialProber usbDefaultProper = UsbSerialProber.getDefaultProber();
            UsbSerialProber usbCustomProper = CustomProber.getCustomProber();
            serListItems.clear();
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                UsbSerialDriver driver = usbDefaultProper.probeDevice(device);
                if (driver == null) {
                    driver = usbCustomProper.probeDevice(device);
                }
                if (driver != null) {
                    for (int port = 0; port < driver.getPorts().size(); port++)
                        serListItems.add(new SerListItem(device, port, driver));
                    serConnect();
                } else {
                    serListItems.add(new SerListItem(device, 0, null));
                }
            }
            listAdapter.notifyDataSetChanged();
        }

        private void serConnect() {
            if (flag_serConnect) {
                Toast.makeText(MainActivity.this, "Serial port connected", Toast.LENGTH_SHORT).show();
                soundStateMachine.switchState(1);
                return;
            }
            SerListItem currentItem = serListItems.get(0);
            if (currentItem.driver != null) {
                usbSerialPort = currentItem.driver.getPorts().get(currentItem.port);
                UsbDeviceConnection usbConnection = usbManager.openDevice(currentItem.driver.getDevice());
                if (usbConnection == null && !usbManager.hasPermission(currentItem.driver.getDevice())) {
                    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
                    Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
                    intent.setPackage(MainActivity.this.getPackageName());
                    PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent, flags);
                    usbManager.requestPermission(currentItem.driver.getDevice(), usbPermissionIntent);
                    return;
                }
                if (usbConnection != null) {
                    try {
                        usbSerialPort.open(usbConnection);
                        try {
                            int baudRate = 460800;
                            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
                        } catch (UnsupportedOperationException e) {
                        }
                        usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                        usbIoManager.start();
                    } catch (Exception e) {
                        serDisconnect();
                    }
                }
                serStatusDisplay("Serial connect!");
                Log.d(TAG, "Serial connect");
                flag_serConnect = true;
                transToNextStatus();
                startHeartbeatThread();
            } else {
                Toast.makeText(MainActivity.this, "currentItem.driver == null", Toast.LENGTH_SHORT).show();
                serStatusDisplay("Serial driver is null!");
                flag_serConnect = false;
                transStatus(serialStatus.CLOSE);
            }
        }

        private void serDisconnect() {
            if (usbIoManager != null) {
                usbIoManager.setListener(null);
                usbIoManager.stop();
            }
            usbIoManager = null;
            if (usbSerialPort != null) {
                try {
                    usbSerialPort.close();
                } catch (IOException ignored) {
                }
                usbSerialPort = null;
            }

            flag_serConnect = false;
            soundStateMachine.switchState(3);
            Log.d(TAG, "Serial disconnect");
            serStatusDisplay("Serial disconnect!");
            transStatus(serialStatus.CLOSE);
        }

        void serStrSend(String str) {
            if (flag_serConnect && usbSerialPort != null) {
                byte[] data = (str + '\n').getBytes();
                try {
                    usbSerialPort.write(data, WRITE_WAIT_MILLIS);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Log.e(TAG, "Error Serial Send Str:" + str + ",flag_serConnect:" + flag_serConnect);
            }
        }
    void serStrSendByteArr(String str) {
        if (flag_serConnect && usbSerialPort != null) {
            try {
                byte[] data = str.getBytes();
                int chunkSize = 8;
                for (int i = 0; i < data.length; i += chunkSize) {
                    // 计算剩余的字节数
                    int remaining = data.length - i;
                    // 如果剩余的字节数不足8个字节，则创建一个新数组并填充
                    byte[] chunk = new byte[chunkSize];
                    if (remaining >= chunkSize) {
                        System.arraycopy(data, i, chunk, 0, chunkSize);
                    } else {
                        System.arraycopy(data, i, chunk, 0, remaining);
                        for (int j = remaining; j < chunkSize; j++) {
                            chunk[j] = 0x00;  // 填充空格
                        }
                    }
                    usbSerialPort.write(chunk, WRITE_WAIT_MILLIS);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            Log.e(TAG, "Error Serial Send SendByteArr:" + str + ",flag_serConnect:" + flag_serConnect);
        }
    }

    void serByteSend(byte[] arrByte){
        try {
            usbSerialPort.write(arrByte, WRITE_WAIT_MILLIS);
        } catch (IOException e) {
            Log.e(TAG, "Error Serial Send Byte");
            throw new RuntimeException(e);
        }
    }

    private boolean sendByteArrayWithAck(byte[] inputArray, serialStatus nextStatus) {
        serNowStatus.set(serialStatus.MSG_SEND);
        final boolean[] result = {false}; // 用于存储最终结果
        new Thread(new Runnable() {
            @Override
            public void run() {
                int totalSize = inputArray.length;
                int bytesSent = 0;
                boolean globalSendFlag = true;
                if (nextStatus == serialStatus.PIC) {
                    try {
                        usbSerialPort.write(myUtil.byteArrMSG_START, WRITE_WAIT_MILLIS);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                while (bytesSent < totalSize && globalSendFlag) {
                    int chunkEnd = Math.min(bytesSent + CHUNK_SIZE, totalSize);
                    byte[] chunk = new byte[chunkEnd - bytesSent];
                    System.arraycopy(inputArray, bytesSent, chunk, 0, chunk.length);

                    boolean sentSuccessfully = false;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            if (nextStatus == serialStatus.PIC) serStatusDisplay("Send Chunk end:" + chunkEnd);
                            usbSerialPort.write(chunk, WRITE_WAIT_MILLIS);
                            // 等待响应信号
                            if (waitForAck()) {
                                sentSuccessfully = true;
                                bytesSent = chunkEnd;
                                break; // 成功收到响应信号，跳出重发循环
                            } else {
                                Log.e(TAG, "Serial Failed to receive acknowledgment from MCU for chunk at position: " + bytesSent + ", attempt " + (attempt + 1));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if (!sentSuccessfully) {
                        Log.e(TAG, "Failed to send chunk after " + 3 + " attempts. Stopping transmission.");
                        globalSendFlag = false;
                        break; // 若无法发送成功，停止发送
                    }
                }

                if (globalSendFlag) {
                    Log.d("DAG", "Finish send msg.");
                    result[0] = true; // 标记为成功
                    if (nextStatus == serialStatus.PIC) {
                        try {
                            for (int i = 0; i < 3; i++) {
                                usbSerialPort.write(myUtil.byteArrMSG_FINISH, WRITE_WAIT_MILLIS);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                } else {
                    try {
                        usbSerialPort.write(("Error:2" ).getBytes(), WRITE_WAIT_MILLIS);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                serNowStatus.set(nextStatus);
            }
        }).start();
        return result[0];
    }

    // 等待单片机响应信号
    private boolean waitForAck() {
        long timeout = recTimeOut;
        long startTime = System.currentTimeMillis();
        ackReceived = false; // 重置ACK标志

        while ((System.currentTimeMillis() - startTime) < timeout) {
            if (ackReceived) {
                return true;
            }
            try {
                Thread.sleep(100); // 等待100ms后再检查
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e("DAG", "Thread interrupted while waiting for acknowledgment");
                return false; // 返回false以终止等待
            }
        }
        return false;
    }

        void serStatusDisplay(String str) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Now Statuts" + str, Toast.LENGTH_LONG);
                    tv_ser_state.setText(str);
                }
            });
        }
    private void serSendParams() throws InterruptedException {
        if (!flag_serConnect) {
            return;
        }
        // 发送开始消息
        serByteSend(myUtil.byteArrMSG_START);
        // 发送相机参数
        delaySendMsg(mCameraParameters.getFocusDistance(), 200);
        delaySendMsg(mCameraParameters.getIso(), 200);
        delaySendMsg(mCameraParameters.getExposureTime(), 200);
        delaySendMsg(mCameraParameters.getZoomRatio(), 200);
        // 发送两个ROI的坐标
        for (int i = 0; i < 4; i++) {
            delaySendMsg(arrRoi1[i], 200);
        }
        for (int i = 0; i < 4; i++) {
            delaySendMsg(arrRoi2[i], 200);
        }

        // 发送结束消息
        for (int i = 0; i < 3; i++) {
            delaySendMsg(myUtil.byteArrMSG_FINISH, 200);
        }
    }

    private void delaySendMsg(Object value, long sleepTime) throws InterruptedException {
        serStrSendByteArr(String.valueOf(value));
        // 如果需要延迟，可以保留Thread.sleep，但通常不建议在串口通信中频繁使用
         Thread.sleep(sleepTime); // 根据需要决定是否保留
    }

    private void serSetParameter(byte[] inputBytes, int par_status) throws CameraAccessException {
        try {
            switch (par_status) {
                case 1: {
                    int[] roiRange1_x1y1 = myUtil.inputRoiArray(inputBytes);
                    arrRoi1[0] = roiRange1_x1y1[0];
                    arrRoi1[1] = roiRange1_x1y1[1];
                }
                break;
                case 2: {
                    int[] roiRange1_x2y2 = myUtil.inputRoiArray(inputBytes);
                    arrRoi1[2] = roiRange1_x2y2[0];
                    arrRoi1[3] = roiRange1_x2y2[1];
                }
                break;
                case 3: {
                    int[] roiRange2_x1y1 = myUtil.inputRoiArray(inputBytes);
                    arrRoi2[0] = roiRange2_x1y1[0];
                    arrRoi2[1] = roiRange2_x1y1[1];
                }
                break;
                case 4: {
                    int[] roiRange2_x2y2 = myUtil.inputRoiArray(inputBytes);
                    arrRoi2[2] = roiRange2_x2y2[0];
                    arrRoi2[3] = roiRange2_x2y2[1];
                }
                break;
                case 5: {
                    String inputExposureTime = myUtil.convertHexBytesToString(inputBytes);
                    if (!mCameraParameters.setExposureTime(Long.parseLong(inputExposureTime))){
                        Log.d(TAG, "inputExposureTime Error");
                    }
                }
                break;
                case 6: {
                    String inputIso = myUtil.convertHexBytesToString(inputBytes);
                    if (!mCameraParameters.setIso(Integer.parseInt(inputIso))){
                        Log.d(TAG, "inputIso Error");
                    }
                }
                break;
                case 7: {
                    String inputFocusDistance = myUtil.convertHexBytesToString(inputBytes);
                    if (!mCameraParameters.setFocusDistance(Float.parseFloat(inputFocusDistance))){
                        Log.d(TAG, "inputFocusDistance Error");
                    }
                }
                break;
                case 8: {
                    String inputZoomRatio = myUtil.convertHexBytesToString(inputBytes);
                    if (!mCameraParameters.setZoomRatio(Float.parseFloat(inputZoomRatio))){
                        Log.d(TAG, "inputZoomRatio Error");
                    }
                }
                break;
                case 9: {
                    if (inputBytes[4] == 0x3A) {
                        if (inputBytes[5] == 0x31){
                            detectMode = operateMode.Detect;
                        }
                        else if (inputBytes[5] == 0x32) {
                            detectMode = operateMode.Compare;
                        } else if (inputBytes[5] == 0x33) {
                            detectMode = operateMode.Record;
                        }
                    }
                }
                break;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format - " + e.getMessage());
        } catch (NullPointerException e) {
            System.err.println("Error: Null value encountered - " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error occurred - " + e.getMessage());
        } finally {
            serByteSend(myUtil.byteArrACK);  // 确保在所有情况下都会发送ACK
        }
    }

    private void startHeartbeatThread() {
        heartbeatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    serialStatus tempStatus = serNowStatus.get();
                    if ((tempStatus == serialStatus.OPEN || tempStatus == serialStatus.READY) && flag_serConnect) {
                        serByteSend(myUtil.arrHeartBeat_op);
                    }
                    try {
                        // 每8秒发送一次心跳信号
                        Thread.sleep(7000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        heartbeatThread.start();
    }
    private void transStatus(serialStatus newStatus){
        serialStatus preState = serNowStatus.get();
        serNowStatus.set(newStatus);
        serStatusDisplay("Status Change:" + preState + " to " + newStatus + "!");
        if (newStatus != serialStatus.CLOSE && newStatus != serialStatus.OPEN)
            soundStateMachine.switchState(2);
        Log.d(STG,"State change:" + preState + " to "+ newStatus + "!");
    }

        private void transToNextStatus(){
            serialStatus preState = serNowStatus.get();
            switch (preState) {
                case CLOSE:
                    transStatus(serialStatus.OPEN);
                    break;
                case OPEN:
                case ACTIVE:
                case EDIT:
                case PIC:
                case SQL_EDIT:
                    transStatus(serialStatus.READY);
                    break;
                case READY:
                    transStatus(serialStatus.ACTIVE);
                    break;
            }
        }

        private boolean checkByteArray(byte[] inputBytes,byte[] targetBytes, int preIndex){
        for (int i = 0; i < preIndex; i++) {
                if (inputBytes[i] != targetBytes[i])return false;
            }
            return true;
        }

/************************  串口数据处理  ******************************************/
        // 状态变量
        private int edit_roi_status = 0;
        private int edit_params_status = 0;
        private int edit_filename_status = 0;

        // 处理各种状态
        private void executeAction(byte[] inputBytes) throws InterruptedException, CameraAccessException {
            if (flag_serConnect) {
                serialStatus currentStatus = serNowStatus.get();
                if (checkByteArray(inputBytes, myUtil.byteArrSTATUS, 8)) {
                    serStrSend("ST" + currentStatus.ordinal() + "Mo" + detectMode.ordinal());
                    Log.d(STG, "GetStatus: ST" + currentStatus.ordinal() + "Mo" + detectMode.ordinal());
                    return;
                }
                switch (currentStatus) {
                    case MSG_SEND:
                        if (checkByteArray(inputBytes, myUtil.byteArrACK, 8)) {
                            ackReceived = true;
                        }
                        break;
                    case OPEN:
                        handleOpenState(inputBytes);
                        break;
                    case READY:
                        handleReadyState(inputBytes);
                        break;
                    case PIC:
                        handlePicState(inputBytes);
                        break;
                    case EDIT:
                        handleEditState(inputBytes);
                        break;
                    case ACTIVE:
                        handleActiveState(inputBytes);
                        break;
                    case SQL_EDIT:
                        handleSQLState(inputBytes);
                        break;
                }
            }
        }

        // 处理 OPEN 状态
        private void handleOpenState(byte[] inputBytes) {
            if (checkByteArray(inputBytes, myUtil.byteArrOP2RE, 8)) {
                if (!flagCameraOpen) {
                    serOpenCamera();
                }
                transToNextStatus();
            }
        }

        // 处理 READY 状态
        private void handleReadyState(byte[] inputBytes) {
            if (checkByteArray(inputBytes, myUtil.byteArrRE2AC, 8)) {
                handleCameraState();
                serByteSend(myUtil.byteArrPCO);
                transToNextStatus();
            } else if (checkByteArray(inputBytes, myUtil.byteArrRE2ED, 8)) {
                handleCameraState();
                serByteSend(myUtil.byteArrPCO);
                resetEditStatus();
                transStatus(serialStatus.EDIT);
            } else if (checkByteArray(inputBytes, myUtil.byteArrRE2PC, 8)) {
                handleCameraState();
                serByteSend(myUtil.byteArrPCO);
                transStatus(serialStatus.PIC);
            } else if (checkByteArray(inputBytes, myUtil.byteArrRE2SQL, 8)) {
                transStatus(serialStatus.SQL_EDIT);
                serByteSend(myUtil.byteArrPCO);
            }
            else {
                Log.d(STG, "Error RecMsg-Ready:" + Arrays.toString(inputBytes));
            }
        }

        // 处理 PIC 状态
        private void handlePicState(byte[] inputBytes) throws InterruptedException {
            Log.d(TAG, "PIC rec:" + Arrays.toString(inputBytes));
            if (checkByteArray(inputBytes, myUtil.byteArrIMAGE, 8)){
                imageLenStr = "";
                flagGetImage = true;
            }
            else if (checkByteArray(inputBytes, myUtil.byteArrSTA, 8)) {
                Log.d(STG, "Start Send Image");
                sendImageData(2);
            } else if (checkByteArray(inputBytes, myUtil.byteArrEND, 8)) {
                transToNextStatus();
                serByteSend(myUtil.byteArrBA2RE);
            } else {
                Log.e(STG, "Error RecMsg-Pic:" + Arrays.toString(inputBytes));
            }
        }
        
        // 处理 EDIT 状态
        private void handleEditState(byte[] inputBytes) throws CameraAccessException, InterruptedException {
            if (checkByteArray(inputBytes, myUtil.byteArrBA2RE, 8)) {
                resetEditStatus();
                transToNextStatus();
            } else if (checkByteArray(inputBytes, myUtil.byteArrS2ROI1, 8)) {
                resetEditStatus();
                edit_roi_status = 1;
                serStatusDisplay("Edit:ROI");
                Log.d(STG, "Edit State: ROI");
            } else if (checkByteArray(inputBytes, myUtil.byteArrS2CAM1, 8)) {
                resetEditStatus();
                edit_params_status = 5;
                serStatusDisplay("Edit:CAM");
                Log.d(STG, "Edit State: CAM");
            } else if (checkByteArray(inputBytes, myUtil.byteArrMODE, 5)) {
                serSetParameter(inputBytes, 9);
                serStatusDisplay("Edit:MOD");
                Log.d(STG, "Edit State: MOD");
            } else if (checkByteArray(inputBytes, myUtil.byteArrS2NAME, 8)) {
                edit_filename_status = 1;
                serStatusDisplay("Edit:NAME");
                Log.d(STG, "Edit State: KNIT NAME");
            } else if (checkByteArray(inputBytes, myUtil.byteArrGETPAR, 8)) {
                serSendParams();
            } else {
                processEditStateData(inputBytes);
            }
        }

        // 处理 SQL 状态
        int sqlHandleState = 0;
        private void handleSQLState(byte[] inputBytes) throws InterruptedException {
            if (checkByteArray(inputBytes, myUtil.byteArrBA2RE, 8)) {
                transToNextStatus();
            } else if (checkByteArray(inputBytes, myUtil.byteArrTNAM, 8)) {
                sqlGetTableNameArray(true);
                Log.d(STG, "SQL State: Check knit table name");
            } else if (checkByteArray(inputBytes, myUtil.byteArrTCHA, 8)) {
                sqlHandleState = 1;
                Log.d(STG, "SQL State: Change knit table name");
            } else if (checkByteArray(inputBytes, myUtil.byteArrTDRO, 8)) {
                sqlHandleState = 2;
                Log.d(STG, "SQL State: Drop knit table name");
            } else if (checkByteArray(inputBytes, myUtil.byteArrQUERY, 8)) {
                sqlHandleState = 3;
                Log.d(STG, "SQL State: Query knit element");
            } else if (checkByteArray(inputBytes, myUtil.byteArrTDRA, 8)) {
                dbTool.dropAllTables();
                Log.d(STG, "SQL State: Drop all table");
                sqlGetTableNameArray(true);
            } else if ((checkByteArray(inputBytes, myUtil.byteArrTGET, 8))) {
                sqlHandleState = 4;
                listYarnData = dbTool.sqlGetTableData(knitTableName);
                int size = listYarnData.size();
                String listSizeStr = "L" + myUtil.paddingString(String.valueOf(size), 7);
                Log.d(STG, "listSizeStr:" + listSizeStr);
                serStrSend(listSizeStr);
                Log.d(STG, "SQL State: Get table Data");
            } else {
                if (sqlHandleState != 0){
                    processSQLStateData(inputBytes, sqlHandleState);
                    sqlHandleState = 0;
                }
            }
        }
        

        // 处理 ACTIVE 状态
        // 处理 CAMERA 状态
        // 重置编辑状态
    private void handleActiveState(byte[] inputBytes) {
            if (checkByteArray(inputBytes, myUtil.byteArrDetect, 8)) {
                flagDetect = true;
                serByteSend(myUtil.byteArrACK);
            } else if (checkByteArray(inputBytes, myUtil.byteArrEND, 8)) {
                flagDetect = false;
                transToNextStatus();
                serByteSend(myUtil.byteArrBA2RE);
            } else if (checkByteArray(inputBytes, myUtil.byteArrYARN, 3)) {
                byte[] arrYarnRow = Arrays.copyOfRange(inputBytes, 3, 8);
                recKnitRow = Integer.parseInt(myUtil.convertHexBytesToString(arrYarnRow));
                serStatusDisplay("KnitRow:" + recKnitRow);
            } else {
                Log.e(STG, "Error RecMsg-Act:" + Arrays.toString(inputBytes));
            }
        }
    // 处理编辑状态数据
    private void processSQLStateData(byte[] inputBytes, int sqlstate) throws InterruptedException {
        switch (sqlstate){
            case 1:{
                // 切换表
                String recTableName = myUtil.convertHexBytesToString(inputBytes);
                knitTableName = recTableName;
//                dbTool.sqlUpdateCameraParameter(knitTableName,mCameraParameters, arrRoi1, arrRoi2);
                serByteSend(myUtil.byteArrACK);
                Log.e(STG, "Change Table Name:" + knitTableName);

            }break;
            case 2:{
                // 删除表
                String recTableName = myUtil.convertHexBytesToString(inputBytes);
                dbTool.dropTable(recTableName);
                serByteSend(myUtil.byteArrACK);
            }break;
            case 3:{
                String queryName = myUtil.convertHexBytesToString(inputBytes);
                YarnDetectData getData = dbTool.fetchYarnDataById(knitTableName, queryName);
                serByteSend(myUtil.byteArrMSG_START);
                if (getData != null) {
                    serByteSend(getData.toByteArr());
                }else {
                    serStrSend("NoFindData");
                }
                for (int i = 0; i < 3; i++) {
                    serByteSend(myUtil.byteArrMSG_FINISH);
                    Thread.sleep(50);
                }
            }break;
            case 4:{
                Log.d(TAG, "GET " + knitTableName +" Table Data");
                serSendYarnData(listYarnData);
            }break;
        }
    }

        private void processEditStateData(byte[] inputBytes) throws CameraAccessException {
            if (edit_filename_status == 1) {
                String recFileName = myUtil.convertHexBytesToString(inputBytes).trim();
                if (!recFileName.equals(knitTableName) && recFileName.length()!=0){
                    knitTableName = recFileName;
                    int target = sqlCreateTable(knitTableName);
                    serStatusDisplay("rec:" + recFileName);
                    serStrSend("flag:"+target);
                }else {
                    dbTool.sqlUpdateCameraParameter(knitTableName,mCameraParameters, arrRoi1, arrRoi2);
                    edit_filename_status = 0; // Reset after use
                    serByteSend(myUtil.byteArrACK);
                }
            } else if (edit_roi_status > 0) {
                serStatusDisplay("roiStatus:" + edit_roi_status);
                serSetParameter(inputBytes, edit_roi_status);
                edit_roi_status = edit_roi_status + 1;
                if (edit_roi_status == 5){
                    edit_roi_status = 0;
                    transStatus(serialStatus.READY);
                    if (detectMode == operateMode.Record){
                        dbTool.sqlUpdateCameraParameter(knitTableName,mCameraParameters, arrRoi1, arrRoi2);
                    }
                }
            } else if (edit_params_status > 0) {
                serSetParameter(inputBytes, edit_params_status);
                serStatusDisplay("cameraStatus:" + edit_params_status);
                edit_params_status = edit_params_status + 1;
                if (edit_params_status == 9){
                    edit_params_status = 0;
                    setUpCameraPar();
                    mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mCameraSessionHandler);
                    if (detectMode == operateMode.Record){
                        dbTool.sqlUpdateCameraParameter(knitTableName,mCameraParameters, arrRoi1, arrRoi2);
                    }
                }
            }
        }
    private void handleCameraState() {
            if (!flagCameraOpen) {
                serOpenCamera();
            }
        }
    private void resetEditStatus() {
            edit_params_status = 0;
            edit_roi_status = 0;
            edit_filename_status = 0;
        }

/************************  串口数据处理  ******************************************/

/************************  SQL处理  ******************************************/

    private static final int TABLE_EXISTS = 1;
    private static final int TABLE_CREATED = 2;
    private static final int OPERATION_FAILED = -1;

    private int sqlCreateTable(String tableName) {
        if (tableName.length() == 0){
            return OPERATION_FAILED;
        }
        if (dbTool.isTableExists(tableName)) {
            Log.d(QTG, "Table TABLE_EXISTS: " + tableName);
            dbTool.sqlUpdateCameraParameter(knitTableName,mCameraParameters, arrRoi1, arrRoi2);
            return TABLE_EXISTS;
        } else {
            try {
                if (dbTool.createTable(tableName)) {
                    dbTool.sqlInsertCameraParameter(tableName,mCameraParameters, arrRoi1, arrRoi2);
                    Log.d(QTG, "Table TABLE_CREATE: " + tableName);
                    return TABLE_CREATED;
                } else {
                    Log.d(QTG, "Table TABLE_CREATE_FAILED: " + tableName);
                    return OPERATION_FAILED;
                }
            } catch (Exception e) {
                return OPERATION_FAILED;
            }
        }
    }

    private void serSendYarnData(List<YarnDetectData> dataList) {
        int totalLength = 0;
        List<byte[]> byteArrayList = new ArrayList<>();
        int size = dataList.size();
        for (int index = 0; index < size; index++) {
            YarnDetectData data = dataList.get(index);
            byte[] dataByte = data.getByteArray(index);
            byteArrayList.add(dataByte); // 存储每个 byte[]
            totalLength += dataByte.length; // 计算总长度
        }
        Log.d(TAG, "TotalLength: " + totalLength);
        int finishMsgLength = 8;
        totalLength += finishMsgLength * 3; // 更新总长度，补充三次

        byte[] sendByteArray = new byte[totalLength];
        int allIndex = 0;

        for (byte[] dataByte : byteArrayList) {
            System.arraycopy(dataByte, 0, sendByteArray, allIndex, dataByte.length);
            allIndex += dataByte.length;
        }

        // 添加结束标识符到 sendByteArray，补充三次
        for (int i = 0; i < 3; i++) {
            System.arraycopy(myUtil.byteArrMSG_FINISH, 0, sendByteArray, allIndex, finishMsgLength);
            allIndex += finishMsgLength;
        }
        Log.d(TAG, "SendData: " + sendByteArray.toString());
        if (!flag_serConnect) {
            return;
        }
        serialStatus tempStatus = serNowStatus.get();
        sendByteArrayWithAck(sendByteArray, tempStatus);
    }

    private void sqlInsertYarnData(YarnDetectData[] detectData){
        List<ContentValues> valuesList = new ArrayList<>();
        for (int i = 0; i < detectData.length; i++) {
            valuesList.add(dbTool.createContentValues(detectData[i].getKey(), detectData[i].getValue(), detectData[i].getVelocity(), detectData[i].getLum(), detectData[i].getRegion()));
        }
        // 批量插入数据
        dbTool.batchInsertData(knitTableName, valuesList);
    }

        //  通过数据库更新数据
         private void sqlGetCameraParameter(String tableName){
            int tempIso = 0;
            Long tempET = 0L;
            float tempFD = 0.0f;
            float tempZR = 0.0f;
            int[] tempRoi1 = new int[0], tempRoi2 = new int[0];
            try {
                String strET = dbTool.fetchYarnDataById(tableName, "ETime").getValue();
                String strIso =dbTool.fetchYarnDataById(tableName, "Iso").getValue();
                String strFD = dbTool.fetchYarnDataById(tableName, "FDist").getValue();
                String strZR = dbTool.fetchYarnDataById(tableName, "ZRat").getValue();
                String strRoi1 = dbTool.fetchYarnDataById(tableName, "Roi1").getValue();
                String strRoi2 = dbTool.fetchYarnDataById(tableName, "Roi2").getValue();

                tempIso = Integer.parseInt(strIso);
                tempET = Long.parseLong(strET);
                tempFD = Float.parseFloat(strFD);
                tempZR = Float.parseFloat(strZR);
                tempRoi1 = myUtil.stringToIntArray(strRoi1);
                tempRoi2 = myUtil.stringToIntArray(strRoi2);
                Log.d(QTG, "Query CameraParams:" + tempIso +" , " + tempET + "," +  tempFD + "," + tempZR + "," + Arrays.toString(tempRoi1) + "," + Arrays.toString(tempRoi2));
            }catch (NumberFormatException e){
                Log.e(EDG, "sqlGetCameraError:" + e);
            } catch (NullPointerException e){
                 Log.e(EDG, "sqlGetCameraError:" + e);
             }
             if (mCameraParameters.assignedCameraParameters(tempET, tempIso, tempFD, tempZR)) {
                 if (tempRoi1.length == 4 && tempRoi2.length == 4)
                 {
                     for (int i = 0; i < 4; i++) {
                         arrRoi1[i] = tempRoi1[i];
                         arrRoi2[i] = tempRoi2[i];
                     }
                 }
             }
        }

    private void sqlGetTableNameArray(boolean serSend){
        List<String> tablesList = dbTool.getAllTables();
        sendTableNameList = tablesList;
        int index = 0;
        // 排除原生表android
        int listSize = tablesList.size() - 1;
        if (flag_serConnect && serSend) {
            serStrSendByteArr("Len:" + listSize + ";");
        }
        try {
            // 延时发送，每次发送后暂停200毫秒
            Thread.sleep(100); // 这里的时间可以根据你的需求调整
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (String table : tablesList) {
            Log.d(QTG, "TableName: " + table);
            if (index != 0) {
                if (flag_serConnect && serSend) {
                    serStrSendByteArr(index - 1 + ":" + table + ";");
                }
            }
            try {
                // 延时发送，每次发送后暂停200毫秒
                Thread.sleep(100); // 这里的时间可以根据你的需求调整
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            index++;
        }
        if (flag_serConnect && serSend) {
            for (int i = 0; i < 3; i++) {
                serByteSend(myUtil.byteArrMSG_FINISH);
            }
            try {
                // 延时发送，每次发送后暂停200毫秒
                Thread.sleep(100); // 这里的时间可以根据你的需求调整
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void setCameraParameters(long longExposureTime, int intIso, float floatFocusDistance, float floatZoomRatio, String tableName) {
        // 检查传入参数是否有效
        if (mCameraParameters.assignedCameraParameters(longExposureTime, intIso, floatFocusDistance, floatZoomRatio)) {
            // 设置相机参数
            setUpCameraPar();
            try {
                // 设置重复的捕获请求
                mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mCameraSessionHandler);
                // 更新数据库中的相机参数
                dbTool.sqlInsertCameraParameter(tableName,mCameraParameters, arrRoi1, arrRoi2);
                // 打印日志
                Log.d(TAG, "参数设置成功");
            } catch (CameraAccessException e) {
                // 捕获相机访问异常并打印错误
                Log.e(TAG, "Failed to set repeating request: " + e.getMessage(), e);
            }
        } else {
            // 参数无效时，打印错误信息
            Log.e(TAG, "Button Set Camera Params Error");
        }
    }


//    SQL功能测试窗口
    private PopupWindow popupSQLWindow, popupParamsWindow;
    private void showPopupCameraParamsWindow(){
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // 填充PopupWindow布局
        View popupView = inflater.inflate(R.layout.dig_camera_set, null);
        popupParamsWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        // 设置PopupWindow的背景，这样点击外部区域就可以关闭PopupWindow
        popupParamsWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        // 设置PopupWindow的动画效果（可选）
        popupParamsWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        EditText et_exposureTime,et_iso, et_focusDistance, et_zoomRatio;
        et_exposureTime = popupView.findViewById(R.id.et_exposureTime);
        et_exposureTime.setText(mCameraParameters.getExposureTime() + "");
        et_iso = popupView.findViewById(R.id.et_iso);
        et_iso.setText(mCameraParameters.getIso() + "");
        et_focusDistance = popupView.findViewById(R.id.et_focusDistance);
        et_focusDistance.setText(mCameraParameters.getFocusDistance() + "");
        et_zoomRatio = popupView.findViewById(R.id.et_zoomRatio);
        et_zoomRatio.setText(mCameraParameters.getZoomRatio() + "");
        EditText et_camera_roi1_xy_se = popupView.findViewById(R.id.et_roi1_xy_se);
        EditText et_camera_roi2_xy_se = popupView.findViewById(R.id.et_roi2_xy_se);

        Button bt_camera_ed_set = popupView.findViewById(R.id.bt_camera_ed_set);
        Button bt_parameters_backHome  = popupView.findViewById(R.id.bt_paramtersBackHome);
        SeekBar seekBarIso, seekBarExposureTime, seekBarZoomRatio, seekBarFocusDistance;
        seekBarIso = popupView.findViewById(R.id.seekBar_iso);
        seekBarExposureTime = popupView.findViewById(R.id.seekBar_exposureTime);
        seekBarFocusDistance = popupView.findViewById(R.id.seekBar_focusDistance);
        seekBarZoomRatio = popupView.findViewById(R.id.seekBar_zoomRatio);
        bt_parameters_backHome.setOnClickListener(v -> popupParamsWindow.dismiss());
        int[] isoRange = mCameraParameters.getIsoRange();
        long[] exposureTimeRange = mCameraParameters.getExposureTimeRange();
        float[] focusDistanceRange = mCameraParameters.getFocusRange();
        float[] zoomRatioRange = mCameraParameters.getZoomRatioRange();
        int initialProgress = 0;
        initialProgress  = (mCameraParameters.getIso() - isoRange[0]) * 100 / (isoRange[2]);
        seekBarIso.setProgress(initialProgress );
        initialProgress = (int) ((mCameraParameters.getExposureTime() - exposureTimeRange[0]) * 100 / (exposureTimeRange[2]));
        seekBarExposureTime.setProgress(initialProgress );
        initialProgress = (int) ((mCameraParameters.getFocusDistance() - focusDistanceRange[0]) * 100 / (focusDistanceRange[2]));
        seekBarFocusDistance.setProgress(initialProgress );
        initialProgress = (int) ((mCameraParameters.getZoomRatio() - zoomRatioRange[0]) * 100 / (zoomRatioRange[2]));
        seekBarZoomRatio.setProgress(initialProgress );

        String[] strRoi1_xy_se = et_camera_roi1_xy_se.getText().toString().split(",");
        String[] strRoi2_xy_se = et_camera_roi2_xy_se.getText().toString().split(",");

        SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (seekBar.getId() == R.id.seekBar_iso) {
                   int isoValue = isoRange[0] + (progress * (isoRange[1] - isoRange[0]) / 100);
                    et_iso.setText(String.valueOf(isoValue));
                    Log.d(TAG, "ISO Value: " + isoValue);
                } else if (seekBar.getId() == R.id.seekBar_exposureTime) {
                    long exposureTimeValue = exposureTimeRange[0] + (progress * (exposureTimeRange[1] - exposureTimeRange[0]) / 100);
                    et_exposureTime.setText(String.valueOf(exposureTimeValue));
                    Log.d(TAG, "Exposure Time: " + exposureTimeValue);
                } else if (seekBar.getId() == R.id.seekBar_focusDistance) {
                    float focusDistanceValue = focusDistanceRange[0] + (progress * (focusDistanceRange[1] - focusDistanceRange[0]) / 100);
                    et_focusDistance.setText(String.valueOf(focusDistanceValue));
                    Log.d(TAG, "Focus Distance: " + focusDistanceValue);
                } else if (seekBar.getId() == R.id.seekBar_zoomRatio) {
                    float zoomRatioValue = zoomRatioRange[0] + (progress * (zoomRatioRange[1] - zoomRatioRange[0]) / 100);
                    et_zoomRatio.setText(String.valueOf(zoomRatioValue));
                    Log.d(TAG, "Zoom Ratio: " + zoomRatioValue);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekBarExposureTime.setOnSeekBarChangeListener(seekBarListener);
        seekBarIso.setOnSeekBarChangeListener(seekBarListener);
        seekBarFocusDistance.setOnSeekBarChangeListener(seekBarListener);
        seekBarZoomRatio.setOnSeekBarChangeListener(seekBarListener);

        bt_camera_ed_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    Long longExposureTime = Long.parseLong(et_exposureTime.getText().toString());
                    int intIso = Integer.parseInt(et_iso.getText().toString());
                    float floatFocusDistance = Float.parseFloat(et_focusDistance.getText().toString());
                    float floatZoomRatio = Float.parseFloat(et_zoomRatio.getText().toString());
//                    for (int i = 0; i < 4; i++) {
//                        arrRoi1[i] = Integer.parseInt(strRoi1_xy_se[i]);
//                        arrRoi2[i] = Integer.parseInt(strRoi2_xy_se[i]);
//                    }
                    setCameraParameters(longExposureTime, intIso, floatFocusDistance, floatZoomRatio, strParamsTableName);
                }catch (NumberFormatException e){
                    System.out.println("Error: Invalid input string for int conversion.");
                }
            }
        });
        // 显示PopupWindow
        View rootView = findViewById(android.R.id.content); // 获取根视图
        popupParamsWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0); // 在屏幕中心显示PopupWindow
    }

    private void showPopupSQLWindow() {
        // 创建LayoutInflater实例
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // 填充PopupWindow布局
        View popupView = inflater.inflate(R.layout.dig_sql_test, null);
        // 创建PopupWindow对象
        popupSQLWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        // 设置PopupWindow的背景，这样点击外部区域就可以关闭PopupWindow
        popupSQLWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        // 设置PopupWindow的动画效果（可选）
        popupSQLWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        // 查找布局中的视图
        EditText sql_table_name = popupView.findViewById(R.id.et_sql_tablename);
        EditText sql_key = popupView.findViewById(R.id.et_sql_key);
        EditText sql_value = popupView.findViewById(R.id.et_sql_value);
        EditText sql_lum = popupView.findViewById(R.id.et_sql_lum);
        EditText sql_region = popupView.findViewById(R.id.et_sql_region);

        Button bt_tableName_set = popupView.findViewById(R.id.bt_sql_setTableName);
        bt_tableName_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String getTableName = sql_table_name.getText().toString();
                Log.d(QTG, "TableName:"+ getTableName);
                int create_flag = sqlCreateTable(getTableName);
                if (create_flag == TABLE_CREATED) {
                    Toast.makeText(MainActivity.this, "Create Table", Toast.LENGTH_SHORT).show();
                } else if (create_flag == TABLE_EXISTS) {
                    Toast.makeText(MainActivity.this, "Table Exists", Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(MainActivity.this, "Fail to create table", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button bt_sql_write = popupView.findViewById(R.id.bt_sql_write);
        bt_sql_write.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = sql_key.getText().toString();
                String value = sql_value.getText().toString();
                int lum = Integer.parseInt(sql_lum.getText().toString());
                int region = Integer.parseInt(sql_region.getText().toString());
                YarnDetectData yarnDetectData = new YarnDetectData(key,value,0.0f,lum,region);
                String getTableName = sql_table_name.getText().toString();
                long sqlInsertFlag = dbTool.insertOrUpdateData(getTableName,yarnDetectData.getContentValues());
                Log.d(QTG, "InsertFlag:"+ sqlInsertFlag);
            }
        });

        Button bt_sql_load = popupView.findViewById(R.id.bt_sql_load);
        bt_sql_load.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> tables = dbTool.getAllTables();
                for (String table : tables) {
                    Log.d(QTG, "TableName: " + table);
                }
            }
        });
        EditText et_fetch_key = popupView.findViewById(R.id.et_sql_findKey);
        Button bt_sql_fetch = popupView.findViewById(R.id.bt_sql_fetch);
        bt_sql_fetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fetch_key = et_fetch_key.getText().toString();
                String getTableName = sql_table_name.getText().toString();
                YarnDetectData findDetectData = dbTool.fetchYarnDataById(getTableName, fetch_key);
                if (findDetectData != null) {
                    Log.d(QTG, "DetectData" + findDetectData.toString());
                }
            }
        });

        // 显示PopupWindow
        View rootView = findViewById(android.R.id.content); // 获取根视图
        popupSQLWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0); // 在屏幕中心显示PopupWindow
    }
/************************************************************************/
}