# 浮纱检测程序通信转换
## Note
考虑到can协议的转换性，数据都转换为16进制
## 安卓端程序状态设置
1. OPEN -> 发送心跳信号
2. READY -> (STM32->"RE"->APP)
3. ACTIVE -> 开始检测，发送检测数据
4. EDIT -> 编辑参数、保存参数
5. PIC -> 传输图片
6. MSG_END -> 传输运行

## 全局状态标志位
- APP: serNowState(enum)
- STM32: globalFlag(uint8_t)
- CAN:

## 设置标识符
| Signal | Str | Hex| edit_status |
| :------: | :---: | :-------: | :---: |
| 心跳信号  | cixing | 63 69 78 69 6e 67 0d 0a |
| 图片传输模式启动  | RE2PC | 52 45 32 50 43 0d 0a 00 |
| 开始传输图片  | STA | 53 54 41 0d 0a 00 00 00 |
| 传输响应  | ACK | 41 43 4B 0d 0a 00 00 00|
| 图片传输结束  | END | 45 4E 44 0d 0a 00 00 00 |
| 查看状态  | STATUS | 53 54 41 54 55 53 0d 0a |
| 开始监测 | RE2AC | 52 45 32 41 43 0d 0a 00 |
| 启动参数编辑 | RE2ED | 52 45 32 45 44 0d 0a 00 |
| 返回准备状态 | BA2RE | 42 41 32 52 45 0d 0a 00 |
| 传输照片状态_1 | PCO | 50 43 4F 0d 0a 00 00 00 |
| 传输照片状态_2 | PCC | 50 43 43 0d 0a 00 00 00 |


| Params | Str | Hex| Other | 
| :------: | :---: | :-------: | :---: |
| ROI区域| S2ROI1/S2ROI2 | 53 32 52 4F 49 31/32  0d 0a | 1/2/3/4 |
| ROI1:X1Y1| x1 y1 | 前四位 后四位 |
| ROI1:X2Y2| x2 y2 | 前四位 后四位 |
| 相机参数设置:曝光时间 | S2CAM1 | 53 32 43 41 4D 31 0d 0a |
| 曝光时间 | exposuretime | 八位数据位 | 5 |
| 相机参数设置:ISO | S2CAM2 | 53 32 43 41 4D 32 0d 0a |
| ISO | ISO | 八位数据位 | 6 |
| 相机参数设置:焦距 | S2CAM3 | 53 32 43 41 4D 33 0d 0a |
| 焦距 | focusDistance | 数据位 2E 数据位 | 7 |
| 相机参数设置:缩放 | S2CAM4 | 53 32 43 41 4D 34 0d 0a |
| 缩放 | zoomRatio | 数据位 2E 数据位 | 8 |
| 运行模式切换 | S2MODE | 53 32 43 41 4D 34 0d 0a | 9 |
| 运行模式设置 | Mode:1 | 43 41 4D 34 3A 31 0d 0a | |

## 正常运行流程
1. APP端自动开启(APP:OPEN)->发送心跳信号->STM32;
2. STM32接收心跳信号(globalFlag 0 -> 1)->"OP2RE"->APP(APP: OPEN->READY); STM32->心跳信号->CAN;
3.


## 传输照片流程
1. CAN->"RE2PC"->STM32->APP;
2. APP->"PCO"->STM32->CAN->"ACK"->STM32->APP(相机已开启); APP->"PCC"->STM32->"PC"->APP->"PCO"->STM32->CAN->"ACK"->STM32->APP(相机关闭情况);
3. CAN->STM32->"STA"-> APP(开启传输)(globalFlag 1 -> 2)
4. APP->"Chunk0"->STM32->"ACK"->APP->"Chunk1";(响应传输)
5. APP->"END"->STM32->CAN->"END"->STM32->APP;(因为数据传输中STM32不做字符串识别，所以需要CAN对STM32发送信息改变状态)(globalFlag: 2  -> 1)

## 参数设置流程
1. CAN->"RE2ED"->STM32->APP;(globalFlag:1)(APP:Reday->Edit)
2. APP->"ACK"->STM32->CAN
3. CAN->"S2ROI1"->STM32->APP;
4. APP->"ACK"->STM32->CAN;
5. CAN->"Param"->STM32->APP;
6. APP->"ACK"->STM32->CAN;
7. CAN->"END"->STM32->APP;(APP:Edit->Ready)


## 主要函数

