#include "opencv-utils.h"
#include <opencv2/imgproc.hpp>

void myFlip(Mat& src){
    flip(src, src , 0);
}

void myBlur(Mat& src, float  sigma){
    GaussianBlur(src, src, Size(), sigma);
}


Mat myRectangle(Mat& src, int* roi1, int* roi2) {
    // 检查 ROI 数组是否有效
    if (roi1 == nullptr || roi2 == nullptr) {
        std::cerr << "Invalid ROI array!" << std::endl;
        return src.clone(); // 返回原图像的克隆，避免修改原图像
    }

    // 创建一个与原始图像相同大小的新图像
    Mat result = src.clone();

    // 确定矩形的两个顶点
    Point pt1(roi1[0], roi1[1]);
    Point pt2(roi1[2], roi1[3]);

    Point pt3(roi2[0], roi2[1]);
    Point pt4(roi2[2], roi2[3]);

    // 在新图像上绘制矩形，颜色为白色，线宽为5
    rectangle(result, pt1, pt2, Scalar(0, 255, 0), 5);
    rectangle(result, pt3, pt4, Scalar(0, 255, 0), 5);

    return  result;
}

void myRoi(Mat& src, Mat& roi1, Mat& roi2, int* roi1Array, int* roi2Array) {
    // 计算矩形的左上角和右下角坐标
    int roi1X1 = roi1Array[0]; // 右上角 X 坐标
    int roi1Y1 = roi1Array[1]; // 右上角 Y 坐标
    int roi1X2 = roi1Array[2]; // 对角线另一点 X 坐标
    int roi1Y2 = roi1Array[3]; // 对角线另一点 Y 坐标

    int roi2X1 = roi2Array[0]; // 右上角 X 坐标
    int roi2Y1 = roi2Array[1]; // 右上角 Y 坐标
    int roi2X2 = roi2Array[2]; // 对角线另一点 X 坐标
    int roi2Y2 = roi2Array[3]; // 对角线另一点 Y 坐标

    // 计算矩形左上角坐标
    int roi1LeftX = min(roi1X1, roi1X2);
    int roi1LeftY = min(roi1Y1, roi1Y2);
    int roi1RightX = max(roi1X1, roi1X2);
    int roi1RightY = max(roi1Y1, roi1Y2);

    int roi2LeftX = min(roi2X1, roi2X2);
    int roi2LeftY = min(roi2Y1, roi2Y2);
    int roi2RightX = max(roi2X1, roi2X2);
    int roi2RightY = max(roi2Y1, roi2Y2);

    // 提取 ROI 区域
    Rect roi1Rect(roi1LeftX, roi1LeftY, roi1RightX - roi1LeftX, roi1RightY - roi1LeftY);
    Rect roi2Rect(roi2LeftX, roi2LeftY, roi2RightX - roi2LeftX, roi2RightY - roi2LeftY);

    roi1 = src(roi1Rect).clone(); // 从原始图像中克隆 ROI1 区域
    roi2 = src(roi2Rect).clone(); // 从原始图像中克隆 ROI2 区域
}

struct DetectionResult {
    int position;          // 物体位置：0 - 左，1 - 中，2 - 右，-1 - 未检测到
    double width;          // 物体宽度
    double areaRatio;      // 所占比例
};

DetectionResult myDetect(Mat& roiImage, Mat& outputImage, double thresholdValue, double maxThresholdValue, double heightRatio) {
    DetectionResult result;
    result.position = -1; // 初始化为-1，表示没有检测到物体
    result.width = 0.0;
    result.areaRatio = 0.0;

    // 将 ROI 图像转换为灰度图像
    cv::Mat grayImage;
    cv::cvtColor(roiImage, grayImage, cv::COLOR_RGBA2GRAY);

    // 对灰度图像进行阈值处理
    cv::Mat binaryImage;
    cv::threshold(grayImage, binaryImage, thresholdValue, maxThresholdValue, cv::THRESH_BINARY);

    // 查找轮廓
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(binaryImage, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    // 获取阈值高度
    int heightThreshold = static_cast<int>(roiImage.rows * heightRatio);

    // 初始化变量
    double totalArea = 0.0;
    int imageWidth = roiImage.cols;
    int leftThird = imageWidth / 3;
    int rightThird = 2 * leftThird;

    // 克隆输入图像作为输出图像
    outputImage = roiImage.clone();

    for (const auto& contour : contours) {
        cv::Rect boundingBox = cv::boundingRect(contour);
        double contourArea = boundingBox.area();
        totalArea += contourArea;

        if (boundingBox.width < heightThreshold) {
            cv::drawContours(outputImage, std::vector<std::vector<cv::Point>>{contour}, -1, cv::Scalar(0, 255, 255), -1);  // 填充绿色
        } else {
            cv::drawContours(outputImage, std::vector<std::vector<cv::Point>>{contour}, -1, cv::Scalar(255, 0, 150), -1);  // 填充红色
            cv::rectangle(outputImage, boundingBox, cv::Scalar(0, 0, 255), 2);  // 画红色矩形框
            result.position = 0;  // 表示检测到了物体
            result.width = boundingBox.width; // 保存物体宽度
        }

        // 计算物体位置
        int contourCenterX = boundingBox.x + boundingBox.width / 2;
        if (contourCenterX < leftThird) {
            result.position = 0; // 左边
        } else if (contourCenterX >= rightThird) {
            result.position = 2; // 右边
        } else {
            result.position = 1; // 中间
        }
    }

    // 计算所占比例
    result.areaRatio = totalArea / (roiImage.rows * roiImage.cols);

    return result;
}


extern "C" JNIEXPORT jobject JNICALL
Java_com_example_myapplication_MainActivity_detectYarnInImage(JNIEnv* env, jobject p_this, jlong matIn, jlong matOut,
                                                              jintArray roi1, jintArray roi2, jfloatArray detectPar,
                                                              jstring saveFilePath, jint yarnRow) {
    cv::Mat& matRaw = *(cv::Mat*) matIn;
    cv::Mat& matAfter = *(cv::Mat*) matOut;

    // 获取保存路径字符串
    const char* filePath = env->GetStringUTFChars(saveFilePath, nullptr);
    if (filePath == nullptr) {
        return nullptr; // 获取路径失败，返回 null
    }

    // 获取 C++ 整数数组来存储 ROI 值
    jint* roi1Array = env->GetIntArrayElements(roi1, nullptr);
    jint* roi2Array = env->GetIntArrayElements(roi2, nullptr);
    jfloat* detectArray = env->GetFloatArrayElements(detectPar, nullptr);

    // 复制原始图像到输出图像
    matRaw.copyTo(matAfter);

    // 使用提取的值初始化 ROI 区域
    cv::Mat matroi1, matroi2;
    try {
        myRoi(matRaw, matroi1, matroi2, roi1Array, roi2Array);
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error in myRoi: %s", e.what());
        env->ReleaseIntArrayElements(roi1, roi1Array, JNI_ABORT);
        env->ReleaseIntArrayElements(roi2, roi2Array, JNI_ABORT);
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr;
    }

    // 检测 ROI 区域中的纱线
    DetectionResult detectResult1 = myDetect(matroi1, matAfter, detectArray[0], detectArray[1], detectArray[2]);
    DetectionResult detectResult2 = myDetect(matroi2, matAfter, detectArray[0], detectArray[1], detectArray[2]);

    // 将检测结果复制到输出图像中的相应 ROI 区域
    try {
        // 检查并拷贝检测结果
        if (detectResult1.position != -1) {
            matAfter(cv::Rect(roi1Array[0], roi1Array[1], roi1Array[2] - roi1Array[0], roi1Array[3] - roi1Array[1])) = matroi1;
        }
        if (detectResult2.position != -1) {
            matAfter(cv::Rect(roi2Array[0], roi2Array[1], roi2Array[2] - roi2Array[0], roi2Array[3] - roi2Array[1])) = matroi2;
        }
    } catch (const cv::Exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error in copying result to matAfter: %s", e.what());
        env->ReleaseIntArrayElements(roi1, roi1Array, JNI_ABORT);
        env->ReleaseIntArrayElements(roi2, roi2Array, JNI_ABORT);
        env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
        env->ReleaseStringUTFChars(saveFilePath, filePath);
        return nullptr;
    }

    // 创建 Java YarnDetectData 对象
    jclass yarnDetectDataClass = env->FindClass("com/example/myapplication/YarnDetectData");
    jmethodID yarnDetectDataConstructor = env->GetMethodID(yarnDetectDataClass, "<init>", "()V");
    jobject yarnDetectDataObj = env->NewObject(yarnDetectDataClass, yarnDetectDataConstructor);

    // 设置 YarnDetectData 对象的属性
    jfieldID keyField = env->GetFieldID(yarnDetectDataClass, "key", "Ljava/lang/String;");
    jfieldID valueField = env->GetFieldID(yarnDetectDataClass, "value", "Ljava/lang/String;");
    jfieldID velocityField = env->GetFieldID(yarnDetectDataClass, "veloctiy", "F");
    jfieldID lumField = env->GetFieldID(yarnDetectDataClass, "lum", "I");
    jfieldID regionField = env->GetFieldID(yarnDetectDataClass, "region", "I");

    env->SetObjectField(yarnDetectDataObj, keyField, env->NewStringUTF("0"));
    env->SetObjectField(yarnDetectDataObj, valueField, env->NewStringUTF(std::to_string(detectResult1.position).c_str()));
    env->SetFloatField(yarnDetectDataObj, velocityField, 0.0f);
    env->SetIntField(yarnDetectDataObj, lumField, detectResult1.width); // 使用宽度
    env->SetIntField(yarnDetectDataObj, regionField, static_cast<int>(detectResult1.areaRatio * 100)); // 比例转为百分比

    // 保存图像到指定路径（如有需要）
    if (strcmp(filePath, "N") != 0) {
        std::string basePath(filePath);
        std::string savePath1 = basePath + "/roi1/matresult1_" + std::to_string(yarnRow) + ".bmp";
        std::string savePath2 = basePath + "/roi2/matresult2_" + std::to_string(yarnRow) + ".bmp";
        imwrite(savePath1, matroi1);
        imwrite(savePath2, matroi2);

        if (detectResult1.position != -1) {
            std::string savePath3 = basePath + "/det/matresult1_" + std::to_string(yarnRow) + ".bmp";
            imwrite(savePath3, matroi1);
        }
        if (detectResult2.position != -1) {
            std::string savePath4 = basePath + "/det/matresult2_" + std::to_string(yarnRow) + ".bmp";
            imwrite(savePath4, matroi2);
        }

        __android_log_print(ANDROID_LOG_INFO, TAG, "matresult1 save path: %s", savePath1.c_str());
        __android_log_print(ANDROID_LOG_INFO, TAG, "matresult2 save path: %s", savePath2.c_str());
    }

    // 释放资源
    env->ReleaseIntArrayElements(roi1, roi1Array, JNI_ABORT);
    env->ReleaseIntArrayElements(roi2, roi2Array, JNI_ABORT);
    env->ReleaseFloatArrayElements(detectPar, detectArray, JNI_ABORT);
    env->ReleaseStringUTFChars(saveFilePath, filePath);

    // 返回检测数据对象
    return yarnDetectDataObj;
}


double calTemplateValue(Mat& targetImage, Mat& templateImage){
    // 创建结果矩阵
    Mat resultImage;
    // 进行模板匹配
    matchTemplate(targetImage, templateImage, resultImage, TM_CCOEFF_NORMED);
    // 找到最佳匹配位置
    Point maxLoc;
    double maxVal;
    minMaxLoc(resultImage, NULL, &maxVal, NULL, &maxLoc);
    // 返回最大匹配值
    return maxVal;
}