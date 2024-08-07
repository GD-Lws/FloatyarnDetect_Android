package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
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

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
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
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity implements SerialInputOutputManager.Listener, View.OnClickListener {

//    native-lib load
    static {
        System.loadLibrary("myapplication");
    }
    public native boolean detectYarnInImage(Bitmap inBitmap, Bitmap outBitmap, int[] roi1, int[] roi2, float[] det_par);
    public native void drawRoiRange(Bitmap inBitmap, Bitmap outBitmap, int[] roi1, int[] roi2);
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
        MSG_END
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
    private UsbManager usbManager = null;
    private UsbSerialPort usbSerialPort = null;
    private ListView lv_device;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private Button bt_ser_clear, bt_ser_connect, bt_ser_disconnect, bt_ser_refresh, bt_ser_send, bt_ser_sqlite, bt_ser_params, bt_ser_ready;
    private Spinner sp_baudRate, sp_mode;
    private TextView tv_ser_rec, tv_ser_state, tv_camera_state;
    private static boolean flag_serConnect = false;
    private static final int WRITE_WAIT_MILLIS = 2000;
    private int baudRate = 460800;
    private int linesPerChunks = 30;
    private static final int CHUNK_SIZE = 10240; // 定义每个包的大小

/************************************************************************/

    /***************   Camera Value   *************************************/

    private CameraCaptureSession mCaptureSession;

    private long camera_exposureTime = new Long(7104250);
    private int camera_Iso = 1200;
    private float camera_focusDistance = 4.12f, camera_zoomRatio = 5.0F;
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

    private boolean flagRoiSet = false;
    private boolean flagDetect = false;
    private boolean flagGetImage = false;
    private boolean flagCameraOpen = false;
    private Button bt_ser_camera, bt_ser_roi;

    //  检测参数
    private int[] arrRoi1 = new int[]{512, 200, 722, 380};
    private int[] arrRoi2 = new int[]{512, 400, 662, 580};
    private float[] arrDetectPar = new float[]{40.0f, 255.0f, 0.4f};
    private int cameraViewWidth = 1920;
    private int cameraViewHeight = 1080;
    /************************************************************************/

    // 工具类
    private UtilTool myUtil;
    private static final String FAG = "FileDebug";
    private static final String CAG = "CameraDebug";
    private static final String SAG = "SerialDebug";
    private static final String DAG = "DetectDebug";
    private static final String STG = "StateDebug";
    private String saveFilePath;
    //    传输图标响应标志
    private volatile boolean ackReceived = false;
    private long recTimeOut = 1000;
/************************************************************************/

    /***************   Transmission Identifier   *************************************/
    byte[] arrHeartBeat_op = {0x63, 0x69, 0x78, 0x69, 0x6e, 0x67, 0x0d, 0x0a};

    byte[] arrRE2PC = {0x52, 0x45, 0x32, 0x50, 0x43, 0x0d, 0x0a, 0x00};
    byte[] arrRE2ED = {0x52, 0x45, 0x32, 0x45, 0x44, 0x0d, 0x0a, 0x00};
    byte[] arrRE2AC = {0x52, 0x45, 0x32, 0x41, 0x43, 0x0d, 0x0a, 0x00};
    byte[] arrBA2RE = {0x42, 0x41, 0x32, 0x52, 0x45, 0x0d, 0x0a, 0x00};
    byte[] arrOP2RE = {0x4F, 0x50, 0x32, 0x52, 0x45, 0x0d, 0x0a, 0x00};
    byte[] arrSTATUS = {0x53, 0x54, 0x41, 0x54, 0x55, 0x53, 0x0d, 0x0a};

    byte[] arrSTA = {0x53, 0x54, 0x41, 0x0d, 0x0a, 0x00, 0x00, 0x00};
    byte[] arrACK = {0x41, 0x43, 0x4B, 0x0d, 0x0a, 0x00, 0x00, 0x00};
    byte[] arrEND = {0x45, 0x4E, 0x44, 0x0d, 0x0a, 0x00, 0x00, 0x00};
    byte[] arrMSG_START = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
    byte[] arrMSG_FINISH = {0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};

    byte[] arrPCO = {0x50, 0x43, 0x4F, 0x0d, 0x0a, 0x00, 0x00, 0x00};
    byte[] arrPCC = {0x50, 0x43, 0x43, 0x0d, 0x0a, 0x00, 0x00, 0x00};

    byte[] arrS2ROI1 = {0x53, 0x32, 0x52, 0x4F, 0x49, 0x31, 0x0d, 0x0a};
    //exposureTime
    byte[] arrS2CAM1 = {0x53, 0x32, 0x43, 0x41, 0x4D, 0x31, 0x0d, 0x0a};
    //ISO
    byte[] arrS2CAM2 = {0x53, 0x32, 0x43, 0x41, 0x4D, 0x32, 0x0d, 0x0a};
    //focusDistance
    byte[] arrS2CAM3 = {0x53, 0x32, 0x43, 0x41, 0x4D, 0x33, 0x0d, 0x0a};
    //zoomRatio
    byte[] arrS2CAM4 = {0x53, 0x32, 0x43, 0x41, 0x4D, 0x34, 0x0d, 0x0a};
    byte[] arrS2MODE = {0x53, 0x32, 0x4D, 0x4F, 0x44, 0x45, 0x0d, 0x0a};


    /************************************************************************/
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myUtil = new UtilTool();
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
        serRefresh();
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
        lv_device = findViewById(R.id.lv_Ser_derive);
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
        bt_ser_refresh = findViewById(R.id.bt_Ser_refresh);
        bt_ser_clear = findViewById(R.id.bt_Ser_clear);
        bt_ser_connect = findViewById(R.id.bt_Ser_open);
        bt_ser_disconnect = findViewById(R.id.bt_Ser_close);
        bt_ser_send = findViewById(R.id.bt_Ser_send);
        bt_ser_camera = findViewById(R.id.bt_Ser_camera);
        bt_ser_roi = findViewById(R.id.bt_Ser_roi);

        tv_ser_rec = findViewById(R.id.tv_Ser_rec);
        tv_ser_state = findViewById(R.id.tv_ser_State);
        tv_camera_state = findViewById(R.id.tv_camera_State);

        bt_ser_refresh.setOnClickListener(this);
        bt_ser_connect.setOnClickListener(this);
        bt_ser_disconnect.setOnClickListener(this);
        bt_ser_camera.setOnClickListener(this);
        bt_ser_roi.setOnClickListener(this);
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
//            String send_msg = et_ser_send.getText().toString();
            serStrSend("SerialTest");
        } else if (id == R.id.bt_Ser_camera) {
            transStatus(serialStatus.PIC);
            serOpenCamera();
        } else if (id == R.id.bt_Ser_roi) {
            flagGetImage = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        serRefresh();
    }

    private void serOpenCamera() {
        if (flagCameraOpen) {
            Log.e(CAG, "摄像头已开启");
            return;
        }
        Log.d(CAG, "相机开启");
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

                // 创建 Bitmap 对象
                Bitmap rawbitmap = Bitmap.createBitmap(yWidth, yHeight, Bitmap.Config.ARGB_8888);
                Bitmap roibitmap = Bitmap.createBitmap(yWidth, yHeight, Bitmap.Config.ARGB_8888);
                // 将灰度图像复制到 Bitmap 中
                Utils.matToBitmap(grayscaleMat, rawbitmap);
                drawRoiRange(rawbitmap, roibitmap, arrRoi1, arrRoi2);
                serialStatus tempStatus = serNowStatus.get();
                if (flagGetImage) {
                    byte[] jpgByteArray = myUtil.saveBitmapAsJpg(roibitmap);
//                    try {
//                        myUtil.writeBytesAsHexToFile(jpgByteArray, saveFilePath, "saveByteArray.txt");
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "length:" + jpgByteArray.length, Toast.LENGTH_SHORT).show();
                        }
                    });
                    sendByteArrayWithAck(jpgByteArray,tempStatus);

                    flagGetImage = false;
                }
                readerImage.close();
            }
        }
    };

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
            if (real_exposureTime != 0) {
                Log.i("camera_info", "ETR: " + real_exposureTime + " FPS: " + real_fps_Range + " FDR: " + real_focusDistance + " ISO: " + real_Iso_value);
            }
        }
    };

    private void setUpCameraPar() {
        Range<Integer> fpsRange = new Range(240, 240);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, camera_focusDistance);
        mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, camera_exposureTime);
        mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, getRect(camera_zoomRatio));
        mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, camera_Iso);
        Log.d(CAG, "相机参数设置");
    }

    private Rect getRect(float Input_zoomRatio) {
        Rect sensorSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int centerX = sensorSize.centerX();
        int centerY = sensorSize.centerY();
        int deltaX = (int) ((sensorSize.width() / (2 * Input_zoomRatio)) + 0.5f);
        int deltaY = (int) ((sensorSize.height() / (2 * Input_zoomRatio)) + 0.5f);
        Rect outputRect = new Rect(
                Math.max(centerX - deltaX, 0),
                Math.max(centerY - deltaY, 0),
                Math.min(centerX + deltaX, sensorSize.width() - 1),
                Math.min(centerY + deltaY, sensorSize.height() - 1));
        return outputRect;
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
            Log.e(CAG, "CameraDevice is null");
            return;
        }
        try {
            Log.i(CAG, "申请预览");
            // 设置为手动模式
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            setUpCameraPar();
//            图像处理
            mPreviewBuilder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    Log.d(CAG, "createCaptureSession onConfigured");
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

    private void cameraOpen(final int width, final int height) {
        // 启动一个后台线程进行一些处理
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                    float maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    Log.d(CAG, "maxZoom:" + maxZoom);
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    // 检查权限并请求权限，需要在主线程中进行
                    if ((checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ||
                            (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                        Log.d(CAG, "No camera and storage permission");
                        // 切换到主线程请求权限
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                requestPermissions(new String[]{android.Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                            }
                        });
                        return;
                    }
                    Log.d(CAG, "开启相机");
                    // 切换到主线程打开相机
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    // TODO: Consider calling
                                    //    ActivityCompat#requestPermissions
                                    // here to request the missing permissions, and then overriding
                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                    //                                          int[] grantResults)
                                    // to handle the case where the user grants the permission. See the documentation
                                    // for ActivityCompat#requestPermissions for more details.
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
            String fullMessage = timestamp + " - " + rec_msg; // 例如，在时间戳后添加消息


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 更新TextView显示新的消息（包含时间戳）
                    tv_ser_rec.setText(fullMessage);
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resetFlag();
                    serRefresh();
                    cameraClose();
                }
            });
        }

        private void resetFlag(){
            flagRoiSet = false;
            flagDetect = false;
            flagGetImage = false;
            flagCameraOpen = false;
        }

        void serRefresh() {
            usbManager = (UsbManager) MainActivity.this.getSystemService(Context.USB_SERVICE);
            UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
            UsbSerialProber usbCustomProber = CustomProber.getCustomProber();
            serListItems.clear();
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
                if (driver == null) {
                    driver = usbCustomProber.probeDevice(device);
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
                            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
                        } catch (UnsupportedOperationException e) {
                        }
                        usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                        usbIoManager.start();
                    } catch (Exception e) {
                        serDisconnect();
                    }
                }
                serStatus("Serial connect!");
                flag_serConnect = true;
                transToNextStatus();
                startHeartbeatThread();
            } else {
                Toast.makeText(MainActivity.this, "currentItem.driver == null", Toast.LENGTH_SHORT).show();
                serStatus("Serial driver is null!");
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
            serStatus("Serial disconnect!");
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
                Log.d("Serial", "Serial send error!");
            }
        }

    void serByteSend(byte[] arrByte){
        try {
            usbSerialPort.write(arrByte, WRITE_WAIT_MILLIS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendByteArrayWithAck(byte[] inputArray, serialStatus nextStatus) {
        serNowStatus.set(serialStatus.MSG_END);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int totalSize = inputArray.length;
                int bytesSent = 0;
                boolean globalSendFlag = true;
                if (nextStatus == serialStatus.PIC) {
                    try {
                        usbSerialPort.write(arrMSG_START, WRITE_WAIT_MILLIS);
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
                            if (nextStatus == serialStatus.PIC)serStatus("Send Chunk end:" + chunkEnd);
                            usbSerialPort.write(chunk, WRITE_WAIT_MILLIS);
                            // 等待响应信号
                            if (waitForAck()) {
                                sentSuccessfully = true;
                                bytesSent = chunkEnd;
                                break; // 成功收到响应信号，跳出重发循环
                            } else {
                                Log.e("DAG", "Failed to receive acknowledgment from MCU for chunk at position: " + bytesSent + ", attempt " + (attempt + 1));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if (!sentSuccessfully) {
                        Log.e("DAG", "Failed to send chunk after " + 3 + " attempts. Stopping transmission.");
                        globalSendFlag = false;
                        break; // 若无法发送成功，停止发送
                    }
                }

                if (globalSendFlag) {
                    Log.d("DAG", "Finish send msg.");
                    if (nextStatus == serialStatus.PIC) {
                        try {
                            usbSerialPort.write(arrMSG_FINISH, WRITE_WAIT_MILLIS);
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
    }

    private static int msg_index = 0;
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


        void serStatus(String str) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Now Statuts" + str, Toast.LENGTH_LONG);
                    tv_ser_state.setText(str);
                }
            });
        }

//  心跳线程
    private Thread heartbeatThread;
    private void startHeartbeatThread() {
        heartbeatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    serialStatus tempStatus = serNowStatus.get();
                    if ((tempStatus == serialStatus.OPEN || tempStatus == serialStatus.READY) && flag_serConnect) {
                        serByteSend(arrHeartBeat_op);
                    }
                    try {
                        // 每8秒发送一次心跳信号
                        Thread.sleep(8000);
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
        serStatus("Status:" + preState + " to " + newStatus + "!");
        Log.d(STG,"State:" + preState + " to "+ newStatus + "!");
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
                    transStatus(serialStatus.READY);
                    break;
                case READY:
                    transStatus(serialStatus.ACTIVE);
                    break;
            }
        }

        private boolean checkByteArray(byte[] inputBytes,byte[] targetBytes, int preIndex){
        if (preIndex > 8)return false;
        for (int i = 0; i < preIndex; i++) {
                if (inputBytes[i] != targetBytes[i])return false;
            }
            return true;
        }


        private void serSetParameter(byte[] inputBytes, int par_status) throws CameraAccessException {
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
                    camera_exposureTime = Long.parseLong(inputExposureTime);
                }
                break;
                case 6: {
                    String inputIso = myUtil.convertHexBytesToString(inputBytes);
                    camera_Iso = Integer.parseInt(inputIso);
                }
                break;
                case 7: {
                    String inputFocusDistance = myUtil.convertHexBytesToString(inputBytes);
                    camera_focusDistance = Float.parseFloat(inputFocusDistance);
                }
                break;
                case 8: {
                    String inputZoomRatio = myUtil.convertHexBytesToString(inputBytes);
                    camera_zoomRatio = Float.parseFloat(inputZoomRatio);
                }
                break;
                case 9: {
                    if (inputBytes[4] == 0x3A) {
                        if (inputBytes[5] == 0x31) detectMode = operateMode.Detect;
                        else if (inputBytes[5] == 0x32) {
                            detectMode = operateMode.Compare;
                        } else if (inputBytes[5] == 0x33) {
                            detectMode = operateMode.Record;
                        }
                    }
                }
            }
            serByteSend(arrACK);
        }

//        git test
        private int edit_roi_status = 0;
        private int edit_params_status = 0;
        private int edit_mode_status = 0;

        private void executeAction(byte[] inputBytes) throws InterruptedException, CameraAccessException {
            serialStatus currentStatus = serNowStatus.get();
            if (checkByteArray(inputBytes,arrSTATUS,8) & flag_serConnect) {
                serStrSend("ST" + currentStatus.ordinal() + "Mo"+ detectMode.ordinal());
                return;
            }
            if (flag_serConnect) {
                switch (currentStatus) {
                    case MSG_END:{
                        if (checkByteArray(inputBytes, arrACK,8)) {
                            ackReceived = true;
                        }
                    }break;
                    case OPEN:
                        if (checkByteArray(inputBytes,arrOP2RE, 8) & flag_serConnect) {
                            if (!flagCameraOpen) {
                                serOpenCamera();
                            }
                            transToNextStatus();
                        }
                        break;
                    case READY:{
                        if (checkByteArray(inputBytes,arrRE2AC,8)) {
                            transToNextStatus();
                        } else if (checkByteArray(inputBytes,arrRE2ED,8)) {
                            if (flagCameraOpen) {
                                serByteSend(arrPCO);
                                edit_params_status = 0;
                                edit_roi_status = 0;
                                edit_mode_status = 0;
                                transStatus(serialStatus.EDIT);
                            } else {
                                serOpenCamera();
                                serByteSend(arrPCC);
                            }
                        } else if (checkByteArray(inputBytes,arrRE2PC,8)) {
                            if (flagCameraOpen) {
                                serByteSend(arrPCO);
                                transStatus(serialStatus.PIC);
                            } else {
                                serOpenCamera();
                                serByteSend(arrPCC);
                            }
                        } else {
                            Log.d(SAG,"Error RecMsg-Ready:" + inputBytes);
                        }
                    }
                        break;
                    case PIC: {
                        if (checkByteArray(inputBytes, arrSTA,8)) {
                            flagGetImage = true;
                        }
                        else if (checkByteArray(inputBytes, arrEND,8)) {
                            transToNextStatus();
                            serByteSend(arrBA2RE);
                        } else {
                            Log.e(SAG, "Error RecMsg-Pic:" + inputBytes);
                        }
                    }
                    break;
                    case EDIT:{
                        if (checkByteArray(inputBytes, arrBA2RE,8)) {
                            edit_params_status = 0;
                            edit_roi_status = 0;
                            edit_mode_status = 0;
                            transToNextStatus();
                        } else if (checkByteArray(inputBytes, arrS2ROI1,5)) {
                            edit_roi_status = 1;
                        }
                        else if (checkByteArray(inputBytes, arrS2CAM1,5)) {
                            edit_params_status = 4;
                        } else if (checkByteArray(inputBytes, arrS2MODE,8)) {
                            edit_mode_status = 9;
                        }
                       else {
                            if (edit_roi_status != 0){
                                serSetParameter(inputBytes,edit_roi_status);
                                edit_roi_status++;
                                if (edit_roi_status == 5){
                                    edit_roi_status = 0;
                                }
                            }
                            if (edit_params_status != 0){
                                serSetParameter(inputBytes,edit_params_status);
                                edit_params_status++;
                                if (edit_params_status == 9){
                                    edit_params_status = 0;
                                }
                            }
                            if (edit_mode_status != 0){
                                serSetParameter(inputBytes,edit_params_status);
                                edit_mode_status = 0;
                            }
                        }
                    }break;
                    case ACTIVE:
                        break;
                    }
                }
            }

    // 获取当前检测状态
//    private String getMsgCurrentPar() {
//        String msg_current_par = "roi_range: ";
//        for (int i = 0; i < 4; i++) {
//            msg_current_par = msg_current_par + arrRoi1[i] + ",";
//        }
//        for (int i = 0; i < 4; i++) {
//            msg_current_par = msg_current_par + arrRoi2[i] + ",";
//        }
//        msg_current_par = msg_current_par + "\n camera_par: E:" + camera_exposureTime + " ISO:" + camera_Iso + " focus:"+ camera_focusDistance;
//        msg_current_par = msg_current_par + "\n detect_par: ";
//        for (int i = 0; i < 3; i++) {
//            msg_current_par = msg_current_par + detect_par_arr[i];
//        }
//        return msg_current_par;
//    }
/************************************************************************/
}