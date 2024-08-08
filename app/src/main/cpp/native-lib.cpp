#include <jni.h>
#include <string>
#include <opencv2/imgproc.hpp>
#include <opencv2/core.hpp>
#include <android/bitmap.h>
#include "opencv-utils.h"
#include <android/log.h>
#include <opencv2/imgcodecs.hpp>

using namespace cv;
static char TAG[] = {"nativeRun"};
void bitmapToMat(JNIEnv *env, jobject bitmap, Mat& dst, jboolean needUnPremultiplyAlpha)
{
    AndroidBitmapInfo  info;
    void*              pixels = 0;

    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        dst.create(info.height, info.width, CV_8UC4);
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(needUnPremultiplyAlpha) cvtColor(tmp, dst, COLOR_mRGBA2RGBA);
            else tmp.copyTo(dst);
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            Mat tmp(info.height, info.width, CV_8UC2, pixels);
            cvtColor(tmp, dst, COLOR_BGR5652RGBA);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nBitmapToMat}");
        return;
    }
}

void matToBitmap(JNIEnv* env, Mat src, jobject bitmap, jboolean needPremultiplyAlpha)
{
    AndroidBitmapInfo  info;
    void*              pixels = 0;

    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( src.dims == 2 && info.height == (uint32_t)src.rows && info.width == (uint32_t)src.cols );
        CV_Assert( src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, COLOR_GRAY2RGBA);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, COLOR_RGB2RGBA);
            } else if(src.type() == CV_8UC4){
                if(needPremultiplyAlpha) cvtColor(src, tmp, COLOR_RGBA2mRGBA);
                else src.copyTo(tmp);
            }
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, COLOR_GRAY2BGR565);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, COLOR_RGB2BGR565);
            } else if(src.type() == CV_8UC4){
                cvtColor(src, tmp, COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nMatToBitmap}");
        return;
    }
}


extern "C" JNIEXPORT jstring
JNICALL
Java_com_example_myapplication_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_MainActivity_detectYarnInImage(JNIEnv* env, jobject p_this, jobject bitmapIn, jobject bitmapOut,
                                                              jintArray roi1, jintArray roi2, jfloatArray detectPar,
                                                              jstring savefilePath, jint yarnRow) {
    Mat matIn, matOut;
    bitmapToMat(env, bitmapIn, matIn, false);

    const char *filePath = env->GetStringUTFChars(savefilePath, nullptr);
    if (filePath == nullptr) {
        // 获取字符串失败
        return false;
    }

    // 创建C++整数数组来存储ROI值
    jint* roi1Array  = env->GetIntArrayElements(roi1, nullptr);
    jint* roi2Array = env->GetIntArrayElements(roi2, nullptr);
    jfloat* detectArray = env->GetFloatArrayElements(detectPar, nullptr);

    // 使用提取的值初始化 ROI 区域
    Mat matroi1, matroi2;
    myRoi(matIn, matroi1, matroi2, roi1Array, roi2Array);

    // 检测 ROI 区域中的纱线
    Mat matresult1, matresult2;
    bool detect_flag_1 = myDetect(matroi1, matresult1, detectArray[0], detectArray[1], detectArray[2]);
    bool detect_flag_2 = myDetect(matroi2, matresult2, detectArray[0], detectArray[1], detectArray[2]);

    // 将检测结果复制到输出图像中的相应 ROI 区域
    matresult1.copyTo(matIn(Rect(roi1Array[0], roi1Array[1], roi1Array[2] - roi1Array[0], roi1Array[3] - roi1Array[1])));
    matresult2.copyTo(matIn(Rect(roi2Array[0], roi2Array[1], roi2Array[2] - roi2Array[0], roi2Array[3] - roi2Array[1])));

    // 生成保存路径并保存图像
    if (strcmp(filePath, "N") != 0)
    {
        std::string savePath1 = std::string(filePath) + "/roi1/matresult1_" + std::to_string(yarnRow) + ".bmp";
        std::string savePath2 = std::string(filePath) + "/roi2/matresult2_" + std::to_string(yarnRow) + ".bmp";
        imwrite(savePath1, matresult1);
        imwrite(savePath2, matresult2);
        if (detect_flag_1){
            std::string savePath3 = std::string(filePath) + "/det/matresult1_" + std::to_string(yarnRow) + ".bmp";
            imwrite(savePath3, matresult1);
        }
        if (detect_flag_2){
            std::string savePath4 = std::string(filePath) + "/det/matresult2_" + std::to_string(yarnRow) + ".bmp";
            imwrite(savePath4, matresult2);
        }
        __android_log_print(ANDROID_LOG_INFO, TAG, "matresult1 save path: %s", savePath1.c_str());
        __android_log_print(ANDROID_LOG_INFO, TAG, "matresult2 save path: %s", savePath2.c_str());
    }

    // 将输出图像转换为 Bitmap
    matToBitmap(env, matIn, bitmapOut, false);

    // 释放资源
    matroi1.release();
    matroi2.release();
    matresult1.release();
    matresult2.release();
    env->ReleaseIntArrayElements(roi1, roi1Array, JNI_ABORT);
    env->ReleaseIntArrayElements(roi2, roi2Array, JNI_ABORT);
    env->ReleaseStringUTFChars(savefilePath, filePath);

    // 返回检测结果
    return (detect_flag_1 | detect_flag_2);
}
//bool detect_flag_1 = myDetect(matroi1, matresult1, 30.0, 255.0, 0.3);
//bool detect_flag_2 = myDetect(matroi2, matresult2, 30.0, 255.0, 0.3);


extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_flip(JNIEnv* env, jobject p_this, jobject bitmapIn, jobject bitmapOut) {
    Mat src;
    bitmapToMat(env, bitmapIn, src, false);
    // NOTE bitmapToMat returns Mat in RGBA format, if needed convert to BGRA using cvtColor

    myFlip(src);

    // NOTE matToBitmap expects Mat in GRAY/RGB(A) format, if needed convert using cvtColor
    matToBitmap(env, src, bitmapOut, false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_blur(JNIEnv* env, jobject p_this, jobject bitmapIn, jobject bitmapOut, jfloat sigma) {
    Mat src;
    bitmapToMat(env, bitmapIn, src, false);
    myBlur(src, sigma);
    matToBitmap(env, src, bitmapOut, false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_drawRoiRange(JNIEnv* env, jobject p_this, jobject bitmapIn, jobject bitmapOut,
                                                         jintArray roi1, jintArray roi2) {
    Mat src;
    bitmapToMat(env, bitmapIn, src, false);
    // 将 ROI 数组转换为 C++ 数组
    jint* roi1Array = env->GetIntArrayElements(roi1, nullptr);
    jint* roi2Array = env->GetIntArrayElements(roi2, nullptr);
//    __android_log_print(ANDROID_LOG_INFO, TAG, "DrawRoi");
    // 调用 myRectangle 函数绘制矩形
    Mat result = myRectangle(src, roi1Array, roi2Array);

    // 将绘制后的图像转换为 Bitmap 并返回
    matToBitmap(env, result, bitmapOut, false);
    src.release();
    result.release();
    env->ReleaseIntArrayElements(roi1, roi1Array, JNI_ABORT);
    env->ReleaseIntArrayElements(roi2, roi2Array, JNI_ABORT);
}