package com.seanghay.caresensble;

import java.util.UUID;

/**
 * Created by isens on 2015. 10. 13..
 */
public class Const {
    // Unit conversion multiplier for glucose values (mg/dL = 18.016 * mmol/L)
    public final static double GlucoseUnitConversionMultiplier = 18.016;
    // Ketone values are stored and transferred multiplied with 10.
    // So actual ketone values should be divided by 10.
    public final static double KetoneMultiplier = 10.0;

    public final static String IS_AUTO_DOWNLOAD = "com.isens.standard.ble.IS_AUTO_DOWNLOAD";
    public final static String IS_TIMESYNC_UTC_TZ = "com.isens.standard.ble.IS_TIMESYNC_UTC_TZ";

    public final static String INTENT_BLE_EXTRA_DATA = "com.isens.standard.ble.BLE_EXTRA_DATA";
    public final static String INTENT_BLE_CONNECT_DEVICE = "com.isens.standard.ble.BLE_CONNECTED_DEVICE";
    public final static String INTENT_BLE_BOND_NONE = "air.SmartLog.android.ble.BLE_BOND_NONE";
    public final static String INTENT_BLE_DEVICE_CONNECTED = "air.SmartLog.android.ble.INTENT_BLE_DEVICE_CONNECTED";
    public final static String INTENT_BLE_DEVICE_DISCONNECTED = "air.SmartLog.android.ble.INTENT_BLE_DEVICE_DISCONNECTED";
    public final static String INTENT_BLE_SERVICE_DISCOVERED = "air.SmartLog.android.ble.INTENT_BLE_SERVICE_DISCOVERED";
    public final static String INTENT_BLE_ERROR = "air.SmartLog.android.ble.BLE_ERROR";
    public final static String INTENT_BLE_DEVICE_NOT_SUPPORTED = "air.SmartLog.android.ble.INTENT_BLE_DEVICE_NOT_SUPPORTED";
    public final static String INTENT_BLE_OPERATE_FAILED = "air.SmartLog.android.ble.INTENT_BLE_OPERATE_FAILED";
    public final static String INTENT_BLE_OPERATE_NOT_SUPPORTED = "air.SmartLog.android.ble.INTENT_BLE_OPERATE_NOT_SUPPORTED";
    public final static String INTENT_BLE_READ_MANUFACTURER = "air.SmartLog.android.ble.BLE_READ_MANUFACTURER";
    public final static String INTENT_BLE_READ_SOFTWARE_REV = "air.SmartLog.android.ble.BLE_READ_SOFTWARE_REVISION";
    public final static String INTENT_BLE_TIMESYNC_SUCCESS = "air.SmartLog.android.ble.INTENT_BLE_TIMESYNC_RESULT";
    public final static String INTENT_BLE_READ_COMPLETED = "air.SmartLog.android.ble.INTENT_BLE_READ_COMPLETED";
    public final static String INTENT_BLE_READ_GREATER_OR_EQUAL = "air.SmartLog.android.ble.INTENT_BLE_READ_GREATER_OR_EQUAL";
    public final static String INTENT_BLE_SOFTWARE_VERSION = "air.SmartLog.android.ble.INTENT_BLE_SOFTWARE_VERSION";
    public final static String INTENT_BLE_SERIAL_NUMBER = "air.SmartLog.android.ble.INTENT_BLE_SERIAL_NUMBER";
    public final static String INTENT_BLE_CHAR_GLUCOSE_CONTEXT = "air.SmartLog.android.ble.INTENT_BLE_CHAR_GLUCOSE_CONTEXT";
    public final static String INTENT_BLE_TOTAL_COUNT = "air.SmartLog.android.ble.INTENT_BLE_TOTAL_DOWNLOAD_COUNT";

    public final static String INTENT_ACTIVITY_RESUME = "com.isens.standard.ble.INTENT_ACTIVITY_RESUME";
    public final static String INTENT_ACTIVITY_PAUSE = "com.isens.standard.ble.INTENT_ACTIVITY_PAUSE";
    public final static String INTENT_START_BLE_SERVICE_FOREGROUND = "com.isens.standard.ble.INTENT_START_BLE_SERVICE_FOREGROUND";
    public final static String INTENT_STOP_BLE_SERVICE_FOREGROUND = "com.isens.standard.ble.INTENT_STOP_BLE_SERVICE_FOREGROUND";
    public final static String INTENT_STOP_SCAN = "com.isens.standard.ble.INTENT_STOP_SCAN";

    //Service
    public final static UUID BLE_SERVICE_GLUCOSE = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_SERVICE_DEVICE_INFO = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_SERVICE_CUSTOM_TIME = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_SERVICE_CUSTOM_TIME_NEW	= UUID.fromString("C4DEA010-5A9D-11E9-8647-D663BD873D93");
    //Characteristic
    public final static UUID BLE_CHAR_GLUCOSE_MEASUREMENT = UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_CHAR_GLUCOSE_CONTEXT = UUID.fromString("00002A34-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_CHAR_DEVICE_INFO_SERIALNO= UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_CHAR_DEVICE_INFO_SOFTWARE_REVISION = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_CHAR_RACP = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_CHAR_CUSTOM_TIME = UUID.fromString("0000FFF1-0000-1000-8000-00805f9b34fb");
    public final static UUID BLE_CHAR_CUSTOM_TIME_NEW	= UUID.fromString("C4DEA3BC-5A9D-11E9-8647-D663BD873D93");

    //Descriptor
    public final static UUID BLE_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}
