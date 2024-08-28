//
// Created by Lws on 2024/4/7.
//

#ifndef MY_APPLICATION_OPENCV_UTILS_H
#define MY_APPLICATION_OPENCV_UTILS_H

#include <opencv2/core.hpp>
#include <iostream>
using namespace cv;

struct DetectionResult {
    int position;          // 物体位置：0 - 左，1 - 中，2 - 右，-1 - 未检测到
    double width;          // 物体宽度
    int areaRatio;      // 所占比例
};

void myFlip(Mat& src);
void myBlur(Mat& src, float sigma);
void myRoi(Mat &src, Mat &roi1, int* roi1Array);
Mat myRectangle(Mat& src, int* roi1, int* roi2);
void myDetect(Mat& roi_img, Mat& result_img, double thresh, double threshMaxVal, double height_percent, DetectionResult* result);
double calTemplateValue(Mat& targetImage, Mat& templateImage);

#endif // MY_APPLICATION_OPENCV_UTILS_H
