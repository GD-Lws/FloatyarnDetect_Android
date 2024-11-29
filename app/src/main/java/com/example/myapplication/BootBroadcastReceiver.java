package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver {
//    private static final String TAG = "BootCompletedReceiver";

    static final String ACTION = "android.intent.action.BOOT_COMPLETED";
    @Override

    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION)) {
            Intent mainActivityIntent = new Intent(context, MainActivity.class);  // 要启动的Activity
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mainActivityIntent);
        }
        if (intent.getAction().equals("WALLPAPER_CHANGED")) {
            System.out.println("=============");
        }
    }

//    private void scheduleJob(Context context) {
//        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
//        if (jobScheduler != null) {
//            int jobId = 1;
//            ComponentName serviceComponent = new ComponentName(context, BootJobService.class);
//
//            JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent);
//
//            // 设置Job为开机启动
//            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
//            builder.setRequiresDeviceIdle(false);
//            builder.setRequiresStorageNotLow(false);
//            builder.setRequiresBatteryNotLow(false);
//            builder.setPersisted(true); // 使Job在设备重启后仍然存在
//
//            JobInfo jobInfo = builder.build();
//
//            try {
//                jobScheduler.schedule(jobInfo);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
