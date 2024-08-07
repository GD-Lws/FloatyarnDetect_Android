package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.util.Base64;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

public class UtilTool {
    public UtilTool(){

    }
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String FAG = "FileTest";
    // 所需的权限列表
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.FOREGROUND_SERVICE
    };

    private static final String DAG = "VideoTest";
    private static final int REQUEST_CODE_PERMISSION = 123;

    public static boolean checkRoiRange(int width, int height, int[] arr_roi) {
        // 检查数组长度是否符合要求
        if (arr_roi.length != 4) {
            return false;
        }
        // 获取矩形的顶点坐标
        int x1 = arr_roi[0];
        int y1 = arr_roi[1];
        int x2 = arr_roi[2];
        int y2 = arr_roi[3];
        // 检查矩形的顶点坐标是否超出图像范围
        if (x1 < 0 || x1 >= width || y1 < 0 || y1 >= height ||
                x2 < 0 || x2 >= width || y2 < 0 || y2 >= height) {
            return false; // 顶点超出范围，返回false
        }
        return true; // 顶点在范围内，返回true
    }

    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        // Finally, create the Mat.
        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }

    @NonNull
    public static BufferedWriter getBufferedWriter(int[] roi1, int[] roi2, String filePath) throws IOException {
        File file = new File(filePath);
        FileWriter fw = new FileWriter(file);
        BufferedWriter bw = new BufferedWriter(fw);
        // 写入ROI1坐标
        bw.write("ROI1: ");
        for (int i = 0; i < roi1.length; i++) {
            bw.write(roi1[i] + " ");
        }
        bw.newLine();
        // 写入ROI2坐标
        bw.write("ROI2: ");
        for (int i = 0; i < roi2.length; i++) {
            bw.write(roi2[i] + " ");
        }
        bw.newLine();
        return bw;
    }

    // 读取文件并按行分块存储，附加包号
    public List<List<String>> readFileAndSplitIntoChunks(String filePath, int linesPerChunk) {
        List<List<String>> chunks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
            String line;
            int chunkId = 0;
            List<String> chunk = new ArrayList<>();
            chunk.add("ChunkID:" + chunkId); // 附加包号
            while ((line = reader.readLine()) != null) {
                chunk.add(line);
                if (chunk.size() > linesPerChunk) {
                    chunks.add(chunk);
                    chunkId++;
                    chunk = new ArrayList<>();
                    chunk.add("ChunkID:" + chunkId); // 附加包号
                }
            }
            // 添加最后的块
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        } catch (IOException e) {
            Log.e(DAG, "Error reading file", e);
        }
        return chunks;
    }


    public String bitmapToString(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
    // 保存数据到本地文件的方法
    public void strSaveToFile(String file_path, String file_name, String data) {
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


    public static int[] inputRoiArray(byte[] inputBytes) {
        if (inputBytes.length != 8) {
            throw new IllegalArgumentException("Input byte array must be exactly 8 bytes long.");
        }

        byte[] x1Bytes = Arrays.copyOfRange(inputBytes, 0, 4);
        byte[] y1Bytes = Arrays.copyOfRange(inputBytes, 4, 8);

        String x1_str = convertHexBytesToString(x1Bytes);
        String y1_str = convertHexBytesToString(y1Bytes);

        int x1;
        int y1;

        try {
            x1 = Integer.parseInt(x1_str);
            y1 = Integer.parseInt(y1_str);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error converting string to integer: " + e.getMessage());
        }

        return new int[]{x1, y1};
    }

    public static String convertHexBytesToString(byte[] inputBytes) {
        StringBuilder combinedString = new StringBuilder();
        for (int i = 0; i < inputBytes.length; i++) {
            if (inputBytes[i] < 0 || inputBytes[i] > 255) {
                throw new IllegalArgumentException("Invalid byte value: " + inputBytes[i]);
            }
            combinedString.append((char) inputBytes[i]);
        }

        return combinedString.toString();
    }
    public byte[] saveBitmapAsJpg(Bitmap bitmap){
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] jpegData = byteArrayOutputStream.toByteArray();
        return jpegData;
    }
    public void writeBytesAsHexToFile(byte[] byteArray, String directoryPath, String fileName) throws IOException {
        // 创建目录
        File dir = new File(directoryPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 创建文件
        File file = new File(dir, fileName);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));

        for (byte b : byteArray) {
            String hexString = String.format("%02X", b);
            writer.write(hexString);
            writer.write(" "); // 添加空格以便于阅读
        }

        writer.close();
    }

    private void saveBitmapAsFile(Bitmap bitmap, String saveFilePath) {
        FileOutputStream out = null;
        File file = null;
        try {
            // 确保存储目录存在
            File storageDir = new File(saveFilePath);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // 创建文件
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "grayscale_image_" + timeStamp + ".png";
            file = new File(storageDir, fileName);
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // 保存为 PNG 文件
            Log.d(FAG, "Saved file path: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public boolean createFolder(String folderPath) {
        boolean create_folder = false;
        File folder = new File(folderPath);
        if (!folder.exists()) {
            File result_1_folder = new File(folderPath+"/roi1");
            File result_2_folder = new File(folderPath+"/roi2");
            File result_det_folder = new File(folderPath+"/det");
            boolean created = folder.mkdirs(); // 创建文件夹
            boolean cre1 = result_1_folder.mkdirs();
            boolean cre2 = result_2_folder.mkdirs();
            boolean cre_det = result_det_folder.mkdirs();
            if (created & cre1 & cre2 & cre_det) {
                Log.d(FAG, "Folder created: " + folderPath);
                create_folder = true;
            } else {
                Log.e(FAG, "Failed to create folder: " + folderPath);
            }
        } else {
            Log.d(FAG, "Folder already exists: " + folderPath);
        }
        return create_folder;
    }

    public static boolean checkPermissions(Activity activity) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                // 某些权限未授予，需要请求权限
                requestPermissions(activity);
                return false;
            }
        }
        // 所有权限已经被授予
        return true;
    }

    public static Bitmap rotateBitmap(Bitmap source, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
    private static void requestPermissions(Activity activity) {
        activity.requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }
    public static Bitmap matToBitmap(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }
    boolean check_video_permission(String videoPath){
        // 创建 File 对象
        File videoFile = new File(videoPath);

        // 检查文件是否存在
        if (videoFile.exists()) {
            Log.d(DAG, "Video file exists");
        } else {
            Log.d(DAG, "Video file does not exist");
            return false;
        }

        // 检查文件是否可读
        if (videoFile.canRead()) {
            Log.d(DAG, "Video file can be read");
        } else {
            Log.d(DAG, "Video file cannot be read");
            return false;
        }

        // 检查文件是否可写
        if (videoFile.canWrite()) {
            Log.d(DAG, "Video file can be written");
        } else {
            Log.d(DAG, "Video file cannot be written");
            return false;
        }
        return true;
    }

    private static final long MIN_EXPOSURE_TIME = 100000L;
    private static final long MAX_EXPOSURE_TIME = 32000000000L;
    private static final int MIN_ISO = 100;
    private static final int MAX_ISO = 3200;
    private static final float MIN_FOCUS_DISTANCE = 0.2f;
    private static final float MAX_FOCUS_DISTANCE = 10.0f;

    private static final float MIN_ZOOM_RATIO = 1.0f;
    private static final float MAX_ZOOM_RATIO = 10.0f;

    boolean isCameraParametersValid(long exposureTime, int iso, float focusDistance, float zoomRatio) {
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
}
