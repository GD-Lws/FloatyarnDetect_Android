package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION)){
            Intent intent_start = new Intent(context, MainActivity.class);
            intent_start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent_start);
        }
    }
}
