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
    if (!roi1Array) {
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr; // 获取 ROI 数组失败，返回 null
    }

    jfloat* detectArray = env->GetFloatArrayElements(detectPar, nullptr);
    if (!detectArray) {
        env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr; // 获取检测参数数组失败，返回 null
    }

    // 复制原始图像到输出图像
    matRaw.copyTo(matAfter);

    // 初始化 ROI 区域
    cv::Mat roiImg;
    myRoi(matRaw, roiImg, roi1Array);

    // 检测 ROI 区域中的纱线
    cv::Mat resultImg;
    DetectionResult detectResult;
    myDetect(roiImg, resultImg, detectArray[0], detectArray[1], detectArray[2], &detectResult);

    // 将检测结果复制到输出图像中的相应 ROI 区域
    if (detectResult.position != -1) {
        try {
            matAfter(cv::Rect(roi1Array[0], roi1Array[1], roi1Array[2] - roi1Array[0], roi1Array[3] - roi1Array[1])) = resultImg;
        } catch (const cv::Exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Error in copying result to matAfter: %s", e.what());
            // 清理资源
            env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
            env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
            env->ReleaseStringUTFChars(saveFilePath, filePath);
            return nullptr; // 复制结果失败，返回 null
        }
    }

    // 创建 Java YarnDetectData 对象
    jclass yarnDetectDataClass = env->FindClass("com/example/myapplication/YarnDetectData");
    if (!yarnDetectDataClass) {
        __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Could not find YarnDetectData class");
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr; // 找不到类，返回 null
    }

    jmethodID yarnDetectDataConstructor = env->GetMethodID(yarnDetectDataClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;FII)V");
    if (!yarnDetectDataConstructor) {
        __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Could not find YarnDetectData constructor");
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr; // 找不到构造函数，返回 null
    }

    jobject yarnDetectDataObj = env->NewObject(yarnDetectDataClass, yarnDetectDataConstructor,
                                               env->NewStringUTF("0"),
                                               env->NewStringUTF(std::to_string(detectResult.position).c_str()),
                                               0.0f,
                                               detectResult.areaRatio, // 使用比例
                                               static_cast<int>(detectResult.areaRatio * 100)); // 比例转为百分比
    if (!yarnDetectDataObj) {
        __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Failed to create YarnDetectData object");
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr; // 创建对象失败，返回 null
    }

    // 保存图像到指定路径（如有需要）
    if (strcmp(filePath, "N") != 0) {
        std::string savePath = std::string(filePath) + ".bmp";
        if (!imwrite(savePath, roiImg)) {
            __android_log_print(ANDROID_LOG_ERROR, "MyAppTag", "Failed to save image to path: %s", savePath.c_str());
        } else {
            __android_log_print(ANDROID_LOG_INFO, "MyAppTag", "Image saved to path: %s", savePath.c_str());
        }
    }

    // 释放资源
    env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
    env->ReleaseIntArrayElements(roi, roi1Array, JNI_ABORT);
    env->ReleaseStringUTFChars(saveFilePath, filePath);

    // 返回检测数据对象
    return yarnDetectDataObj;
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