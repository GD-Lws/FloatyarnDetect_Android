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


extern "C" JNIEXPORT jobject JNICALL
Java_com_example_myapplication_MainActivity_detectYarnInImage(JNIEnv* env, jobject p_this, jlong matIn, jlong matOut,
                                                              jintArray roi, jfloatArray detectPar,
                                                              jstring saveFilePath) {
    // 获取 Mat 对象
    cv::Mat& matRaw = *(cv::Mat*) matIn;
    cv::Mat& matAfter = *(cv::Mat*) matOut;

    // 获取保存路径字符串
    const char* filePath = env->GetStringUTFChars(saveFilePath, nullptr);
    if (!filePath) {
        return nullptr; // 获取路径失败，返回 null
    }

    // 获取 ROI 和检测参数
    jint* roi1Array = env->GetIntArrayElements(roi, nullptr);
    jfloat* detectArray = env->GetFloatArrayElements(detectPar, nullptr);

    if (!roi1Array || !detectArray) {
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        if (roi1Array) env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        if (detectArray) env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        return nullptr; // 获取 ROI 或检测参数数组失败，返回 null
    }

    // 复制原始图像到输出图像
    matRaw.copyTo(matAfter);

    // 初始化 ROI 区域
    cv::Mat roiImg;
    myRoi(matRaw, roiImg, roi1Array);

    // 检测 ROI 区域中的纱线
    DetectionResult detectResult;
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "Start Detect");

    myDetect(roiImg, matAfter, detectArray[0], detectArray[1], detectArray[2], &detectResult);

    // 创建 Java YarnDetectData 对象
    jclass yarnDetectDataClass = env->FindClass("com/example/myapplication/YarnDetectData");
    if (!yarnDetectDataClass) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Could not find YarnDetectData class");
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        return nullptr; // 找不到类，返回 null
    }

    jmethodID yarnDetectDataConstructor = env->GetMethodID(yarnDetectDataClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;FII)V");
    if (!yarnDetectDataConstructor) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Could not find YarnDetectData constructor");
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        return nullptr; // 找不到构造函数，返回 null
    }

    jobject yarnDetectDataObj = env->NewObject(yarnDetectDataClass, yarnDetectDataConstructor,
                                               env->NewStringUTF("0"),
                                               env->NewStringUTF(std::to_string(detectResult.position).c_str()),
                                               0.0f,
                                               detectResult.areaRatio,
                                               static_cast<int>(detectResult.areaRatio * 100));
    if (!yarnDetectDataObj) {
        __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Failed to create YarnDetectData object");
    }

    // 保存图像到指定路径（如有需要）
    if (strcmp(filePath, "N") != 0) {
        std::string savePath = std::string(filePath) + ".bmp";
        if (!imwrite(savePath, matAfter)) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to save image to path: %s", savePath.c_str());
        } else {
            __android_log_print(ANDROID_LOG_INFO, TAG, "Image saved to path: %s", savePath.c_str());
        }
    }

    // 释放资源
    env->ReleaseStringUTFChars(saveFilePath, filePath);
    env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
    env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);

    // 返回检测数据对象
    return yarnDetectDataObj;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_matMerge(JNIEnv* env, jobject p_this, jlong matTarget, jlong matOut, jlongArray matInArray, jintArray roiArray) {
    // 获取 Mat 对象
    cv::Mat& matTgt = *(cv::Mat*) matTarget;
    cv::Mat& matAfter = *(cv::Mat*) matOut;

    // 将 matTgt 复制到 matAfter，以确保 matTgt 本身不受影响
    matTgt.copyTo(matAfter);

    // 获取 ROI 和输入的 mat 数组
    jint* roi1Array = env->GetIntArrayElements(roiArray, nullptr);
    if (!roi1Array) {
        return; // 获取 ROI 数组失败，直接返回
    }

    jsize matCount = env->GetArrayLength(matInArray);
    jlong* matArray = env->GetLongArrayElements(matInArray, nullptr);
    if (!matArray) {
        env->ReleaseIntArrayElements(roiArray, roi1Array, JNI_ABORT);
        return; // 获取 mat 数组失败，释放 ROI 数组并返回
    }

    // 遍历输入的 mat 数组，将每个 mat 复制到 matAfter 中的相应位置
    for (jsize i = 0; i < matCount; ++i) {
        cv::Mat& matSrc = *(cv::Mat*) matArray[i];
        try {
            // 使用 x1, y1, x2, y2 定义 ROI 区域
            cv::Rect roi(roi1Array[4 * i], roi1Array[4 * i + 1],
                         roi1Array[4 * i + 2] - roi1Array[4 * i],
                         roi1Array[4 * i + 3] - roi1Array[4 * i + 1]);
            matSrc.copyTo(matAfter(roi));
        } catch (const cv::Exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Error in copying mat to matAfter: %s", e.what());
        }
    }

    // 释放资源
    env->ReleaseIntArrayElements(roiArray, roi1Array, JNI_ABORT);
    env->ReleaseLongArrayElements(matInArray, matArray, JNI_ABORT);
}



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
Java_com_example_myapplication_MainActivity_matDrawRoiRange(JNIEnv* env, jobject p_this, jlong matIn, jlong matOut,
                                                         jintArray roi1, jintArray roi2) {
    // 解引用传入的 Mat 指针
    cv::Mat& matRaw = *(cv::Mat*) matIn;
    cv::Mat& matAfter = *(cv::Mat*) matOut;

    // 将 ROI 数组转换为 C++ 数组
    jint* roi1Array = env->GetIntArrayElements(roi1, nullptr);
    jint* roi2Array = env->GetIntArrayElements(roi2, nullptr);

    // 确保 matOut 已经被正确初始化
    if (matAfter.empty()) {
        // 根据需要初始化 matAfter，这里假设与 matRaw 相同的尺寸和类型
        matAfter.create(matRaw.size(), matRaw.type());
    }

    // 调用 myRectangle 函数绘制矩形
    matAfter = myRectangle(matRaw, roi1Array, roi2Array);

    // 释放 ROI 数组
    env->ReleaseIntArrayElements(roi1, roi1Array, JNI_ABORT);
    env->ReleaseIntArrayElements(roi2, roi2Array, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_bitmapDrawRoiRange(JNIEnv* env, jobject p_this, jobject bitmapIn, jobject bitmapOut,
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