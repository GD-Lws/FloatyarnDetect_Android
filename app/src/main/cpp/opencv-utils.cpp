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

void myRoi(Mat& src, Mat& roi1, int* roi1Array) {
    // 计算矩形的左上角和右下角坐标
    int roi1X1 = roi1Array[0]; // 右上角 X 坐标
    int roi1Y1 = roi1Array[1]; // 右上角 Y 坐标
    int roi1X2 = roi1Array[2]; // 对角线另一点 X 坐标
    int roi1Y2 = roi1Array[3]; // 对角线另一点 Y 坐标

    // 计算矩形左上角坐标
    int roi1LeftX = min(roi1X1, roi1X2);
    int roi1LeftY = min(roi1Y1, roi1Y2);
    int roi1RightX = max(roi1X1, roi1X2);
    int roi1RightY = max(roi1Y1, roi1Y2);

    // 提取 ROI 区域
    Rect roi1Rect(roi1LeftX, roi1LeftY, roi1RightX - roi1LeftX, roi1RightY - roi1LeftY);

    roi1 = src(roi1Rect).clone(); // 从原始图像中克隆 ROI1 区域
}



void myDetect(cv::Mat& roiImage, cv::Mat& outputImage, double thresholdValue, double threshMaxVal, double heightRatio, DetectionResult* result) {
    // 初始化结果
    result->position = -1; // 初始化为-1，表示没有检测到物体
    result->width = 0.0;
    result->areaRatio = 0;

    // 将 ROI 图像转换为灰度图像
//    cv::Mat grayImage;
//    cv::cvtColor(roiImage, grayImage, cv::COLOR_RGBA2GRAY);

    // 对灰度图像进行阈值处理
    cv::Mat binaryImage;
    cv::threshold(roiImage, binaryImage, thresholdValue, threshMaxVal, cv::THRESH_BINARY);

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
            result->position = 0;  // 表示检测到了物体
            result->width = boundingBox.width; // 保存物体宽度
        }

        // 计算物体位置
        int contourCenterX = boundingBox.x + boundingBox.width / 2;
        if (contourCenterX < leftThird) {
            result->position = 0; // 左边
        } else if (contourCenterX >= rightThird) {
            result->position = 2; // 右边
        } else {
            result->position = 1; // 中间
        }
    }
    result->areaRatio = totalArea / (roiImage.rows * roiImage.cols) * 10;
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