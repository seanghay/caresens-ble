package com.seanghay.caresensble;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by user on 2017-12-18.
 */

public class Util {


    @SuppressLint("StaticFieldLeak")
    public static Context appContext = CareSensBLE.get().getContext();

    public static int getPreference(String key) {
        SharedPreferences pref = appContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        return pref.getInt(key, 0);
    }

    public static void setPreference(String key, int value) {
        SharedPreferences pref = appContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static boolean getPreferenceBool(String key) {
        SharedPreferences pref = appContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        return pref.getBoolean(key, false);
    }

    public static void setPreference(String key, boolean value) {
        SharedPreferences pref = appContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    public static void showToast(String text) {
        Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show();
    }

    public static boolean runningOnKitkatOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

}
