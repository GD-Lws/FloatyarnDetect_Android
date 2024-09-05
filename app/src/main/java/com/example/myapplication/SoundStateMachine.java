package com.example.myapplication;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;

public class SoundStateMachine {

    private Context context;
    private MediaPlayer mediaPlayer;
    private int currentState;
    private Handler handler;

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;
    public static final int STATE_3 = 3;


    public SoundStateMachine(Context context) {
        this.context = context;
        handler = new Handler();
        currentState = STATE_1;  // 初始状态
    }

    // 切换状态
    public void switchState(int newState) {
        currentState = newState;
        playSoundForState(currentState);
    }

    // 根据状态播放对应的声音
    private void playSoundForState(int state) {
        stopCurrentSound();  // 停止当前播放的声音

        int soundResId;
        switch (state) {
            case STATE_1:
                soundResId = R.raw.beep07;
                break;
            case STATE_2:
                soundResId = R.raw.beep09;
                break;
            case STATE_3:
                soundResId = R.raw.beep05;
                break;
            default:
                return;
        }

        mediaPlayer = MediaPlayer.create(context, soundResId);
        mediaPlayer.setLooping(false);  // 设置是否循环播放
        mediaPlayer.start();

        // 如果需要在播放结束后执行某些操作，可以使用 OnCompletionListener
        mediaPlayer.setOnCompletionListener(mp -> {
            // 这里可以处理播放结束后的逻辑
        });
    }

    // 停止当前播放的声音
    private void stopCurrentSound() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // 清理资源
    public void release() {
        stopCurrentSound();
        handler.removeCallbacksAndMessages(null);
    }
}
