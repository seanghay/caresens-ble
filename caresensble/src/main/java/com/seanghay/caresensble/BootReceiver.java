package com.seanghay.caresensble;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by user on 2017-07-31.
 */

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (getPreferenceBool(context, Const.IS_AUTO_DOWNLOAD)) {
            context.startService(new Intent(context, GlucoseBleService.class));
        }
    }

    private boolean getPreferenceBool(Context context, String key) {
        SharedPreferences pref = context.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        return pref.getBoolean(key, false);
    }

}
