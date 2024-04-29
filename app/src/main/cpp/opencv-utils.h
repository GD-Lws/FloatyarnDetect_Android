//
// Created by Lws on 2024/4/7.
//

#ifndef MY_APPLICATION_OPENCV_UTILS_H
#define MY_APPLICATION_OPENCV_UTILS_H
#pragma once
#include <opencv2/core.hpp>
#include <iostream>
using namespace cv;
void myFlip(Mat& src);
void myBlur(Mat& src, float sigma);
void myRoi(Mat& src, Mat& roi1, Mat& roi2, int* roi1Array, int* roi2Array);
Mat myRectangle(Mat& src, int* roi1, int* roi2);
bool myDetect(Mat& roi_img, Mat& result_img,double thresh, double thresh_maxval, double height_percent);
#endif //MY_APPLICATION_OPENCV_UTILS_H
