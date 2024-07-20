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
import android.graphics.Region;
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

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class MainActivity extends Activity implements SerialInputOutputManager.Listener, View.OnClickListener  {

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
    private enum serialState {
        CLOSE,
        OPEN,
        READY,
        ACTIVE,
        EDIT,
        PIC
    }
    private serialState serNowState = serialState.CLOSE;
    private SerialInputOutputManager usbIoManager;
    private final ArrayList<SerListItem> serListItems = new ArrayList<>();
    private ArrayAdapter<SerListItem> listAdapter;
    private UsbManager usbManager = null;
    private UsbSerialPort usbSerialPort = null;
    private ListView lv_device;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private Button bt_ser_clear, bt_ser_connect, bt_ser_disconnect, bt_ser_refresh, bt_ser_send;
    private TextView  tv_ser_rec, tv_ser_state, tv_camera_state;
    private EditText et_ser_send;
    private static boolean flag_serConnect = false;
    private static final int WRITE_WAIT_MILLIS = 2000;
    private int baudRate = 115200;
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
    private MyUtil myUtil;
    private static Bitmap bm_roi_photo;
    private static final String FAG = "FileDebug";
    private static final String CAG = "CameraDebug";
    private static final String SAG = "SerialDebug";
    private static final String DAG = "DetectDebug";
    private static final String STG = "StateDebug";
    private String saveFilePath;
//    传输图标响应标志
    private volatile boolean ackReceived = false;
/************************************************************************/

/***************   Transmission Identifier   *************************************/
    String strHeartBeat = "cixing";
    String strPicReady = "PICREADY";
    String strPicACK = "PICACK";
    String strRE2AC = "R2AC";
    String strRE2ED = "R2ED";
    String strOP2RE = "O2RE";
    String strAC2RE = "A2RE";

 /************************************************************************/
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myUtil = new MyUtil();
        saveFilePath = getExternalCacheDir().getAbsolutePath() + "/";
        OpenCVLoader.initDebug(false);
        if (myUtil.checkPermissions(MainActivity.this)) {
            Toast.makeText(this, "浮纱检测程序", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(this, "检测权限未授权", Toast.LENGTH_SHORT).show();
        }
        InitView();
        cameraManager = (CameraManager)getApplication().getSystemService(Context.CAMERA_SERVICE);
        try{
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        serRefresh();
    }

    private void InitView(){
        listAdapter = new ArrayAdapter<SerListItem>(MainActivity.this, 0, serListItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                SerListItem item = serListItems.get(position);
                if (view == null)
                    view = MainActivity.this.getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                if(item.driver == null)
                    text1.setText("<no driver>");
                else if(item.driver.getPorts().size() == 1)
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver",""));
                else
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver","")+", Port "+item.port);
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
                if(item.driver == null) {
                    Toast.makeText(MainActivity.this, "no driver", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Select driverId" + item.device.getDeviceId() + "port" +  item.port , Toast.LENGTH_SHORT).show();
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
        et_ser_send = findViewById(R.id.et_Ser_send);
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
        if (id == R.id.bt_Ser_refresh){
            serRefresh();
            Toast.makeText(MainActivity.this, "串口设备刷新", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.bt_Ser_open) {
            serConnect();
        } else if (id == R.id.bt_Ser_close) {
            serDisconnect();
        } else if (id == R.id.bt_Ser_send) {
            String send_msg = et_ser_send.getText().toString();
            serSend(send_msg);
        } else if (id == R.id.bt_Ser_camera) {
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

    private void serOpenCamera(){
        if (flagCameraOpen){Toast.makeText(MainActivity.this, "摄像头已开启",Toast.LENGTH_SHORT).show();return;}
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
                int yPixelStride = yPlane.getPixelStride();
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
                Bitmap bitmap = Bitmap.createBitmap(yWidth, yHeight, Bitmap.Config.ARGB_8888);

                // 将灰度图像复制到 Bitmap 中
                Utils.matToBitmap(grayscaleMat, bitmap);
                if (flagGetImage) {
//                    saveBitmapAsFile(bitmap);
                    byte[] jpgByteArray = myUtil.saveBitmapAsJpg(bitmap);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "length:"+jpgByteArray.length, Toast.LENGTH_SHORT).show();
                        }
                    });
                    sendByteArray(jpgByteArray);
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

        private void setCameraPar() {
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
                setCameraPar();
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

        private void cameraOpen(int width, int height) {
            try {
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                float maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                Log.d(CAG, "maxZoom:" + maxZoom);
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if ((checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) || (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                    Log.d(CAG, "No camera and storage permission");
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                }
                Log.d(CAG, "开启相机");
                cameraManager.openCamera(cameraId, cameraStateCallback, mCameraStateHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
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
            tv_ser_rec.setText(rec_msg);
            executeAction(rec_msg);
        }

        //    不要操作UI线程，会闪退
        @Override
        public void onRunError(Exception e) {
            serDisconnect();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    serRefresh();
                }
            });
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
                serNowState = serialState.CLOSE;
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
            serNowState = serialState.CLOSE;
//            cameraClose();
        }

        void serSend(String str) {
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
        void serSendRepeat(String str){
            if (flag_serConnect && usbSerialPort != null) {
                byte[] data = (str + '\n').getBytes();
                try {
                    for (int i = 0; i < 3; i++) {
                        usbSerialPort.write(data, WRITE_WAIT_MILLIS);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Log.d("Serial", "Serial send error!");
            }
        }

    private void sendByteArray(byte[] inputArray) {
        int totalSize = inputArray.length;
        int bytesSent = 0;

        try {
            while (bytesSent < totalSize) {
                int chunkEnd = Math.min(bytesSent + CHUNK_SIZE, totalSize);
                byte[] chunk = new byte[chunkEnd - bytesSent];
                System.arraycopy(inputArray, bytesSent, chunk, 0, chunk.length);
                usbSerialPort.write(chunk, WRITE_WAIT_MILLIS);
                bytesSent = chunkEnd;
            }
            usbSerialPort.write("END".getBytes(), WRITE_WAIT_MILLIS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int msg_index = 0;
    private void serSendChunks(final List<List<String>> chunks) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int fileIndex = 0;
                boolean globalSendFlag = true;
                for (List<String> chunk : chunks) {
                    StringBuilder chunkBuilder = new StringBuilder();
                    for (String line : chunk) {
                        chunkBuilder.append(line).append("\n");
                    }
                    chunkBuilder.append("0000");
                    String chunkString = chunkBuilder.toString();

                    // 发送chunk，并设置重发次数
                    int retries = 3; // 设置重发次数
                    boolean sentSuccessfully = false;
                    for (int attempt = 0; attempt < retries; attempt++) {
                        serSend(chunkString);
                        // 等待响应信号
                        if (waitForAck()) {
                            sentSuccessfully = true;
                            break; // 成功收到响应信号，跳出重发循环
                        } else {
                            Log.e(DAG, "Failed to receive acknowledgment from MCU for chunk: " + fileIndex + ", attempt " + (attempt + 1));
                        }
                    }

                    if (!sentSuccessfully) {
                        Log.e(DAG, "Failed to send chunk after " + retries + " attempts. Stopping transmission.");
                        msg_index = fileIndex;
                        globalSendFlag = false;
                        break; // 若无法发送成功，停止发送
                    }
                    fileIndex++;
                }

                if (globalSendFlag) {
                    serSend("END");
                } else {
                    serSend("Failed:" + msg_index);
                }
            }
        }).start();
    }

    // 等待单片机响应信号
    private boolean waitForAck() {
        long timeout = 5000; // 超时时间为5秒
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
                Log.e(DAG, "Thread interrupted while waiting for acknowledgment");
                return false; // 返回false以终止等待
            }
        }
        return false;
    }


        // 保存数据到本地文件的方法
        private void strSaveToFile(String file_path, String file_name, String data) {
            // 确保存储目录存在
            File storageDir = new File(file_path);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            // 保存文件
            File file = new File(file_path, file_name);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                fos.write(data.getBytes());
                fos.flush();
                Log.d(DAG, "Data saved to file: " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
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
                    if (serNowState == serialState.OPEN && flag_serConnect) {
                        serSend(strHeartBeat);
                    }
                    try {
                        Thread.sleep(10000); // 每5秒发送一次心跳信号
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        heartbeatThread.start();
    }
        private void transToNextStatus(){
            serialState preState = serNowState;
            switch (serNowState) {
                case CLOSE:
                    serNowState = serialState.OPEN;
                    break;
                case OPEN:
                    serNowState = serialState.READY;
                    break;
                case READY:
                    serNowState = serialState.ACTIVE;
                    break;
                case ACTIVE:
                    serNowState = serialState.OPEN;
                    break;
                case EDIT:
                    serNowState = serialState.READY;
                    break;
            }
            Log.d(STG,"State:" + preState + " to READY!");
        }

        private void executeAction(String rec_msg) {
            String[] rec_arr = rec_msg.split(":");
            int rec_arr_len = rec_arr.length;
            if (rec_arr[0].equals("GS") & flag_serConnect) {
                serSend("Now State is " + serNowState);
            }
            if (flag_serConnect) {
                switch (serNowState) {
                    case OPEN:
                        if (rec_arr_len == 2 & rec_arr[0].equals("RE") & flag_serConnect) {
                            if (!flagCameraOpen) {
                                serOpenCamera();
                            }
                            transToNextStatus();
                        }
                        break;
                    case READY:
                        if (rec_arr_len == 2 & rec_arr[0].equals("AC")) {
                            serSendRepeat("ACS");
                            transToNextStatus();
                        } else if (rec_arr_len == 2 & rec_arr[0].equals("ED")) {
                            serSendRepeat("EDS");
                            serNowState = serialState.EDIT;
                        } else if (rec_arr_len == 2 & rec_arr[0].equals("PC")) {
                            if (flagCameraOpen) {
                                serNowState = serialState.PIC;
                                serSendRepeat("PCO");
                            } else {
                                serOpenCamera();
                                serSendRepeat("PCC");
                            }
                        }
                        break;
                    case PIC:
                        if (rec_arr[0].equals("STA")){
                            ackReceived = false;
                            flagGetImage = true;
                        }
                        if (rec_arr[0].equals("ACK")) {
                            ackReceived = true;
                        } else if (rec_arr[0].equals("END")) {
                            transToNextStatus();
                            serSend("Serial trans to Ready State!");
                        }
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