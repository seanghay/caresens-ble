package com.seanghay.caresensble;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.seanghay.caresensble.Const.KetoneMultiplier;
import static com.seanghay.caresensble.Util.runningOnKitkatOrHigher;

/**
 * Created by user on 2017-07-27.
 */

public class GlucoseBleService extends Service {
    public static final SparseArray<GlucoseRecord> mRecords = new SparseArray<GlucoseRecord>();
    public final static int PERMISSION_REQUEST_CODE = 100;
    private final static int SOFTWARE_REVISION_BASE = 1, SOFTWARE_REVISION_1 = 1, SOFTWARE_REVISION_2 = 0; //base: custom profile version
    private static final boolean DEVICE_IS_BONDED = true;
    private static final boolean DEVICE_NOT_BONDED = false;
    private static final int REQUEST_ENABLE_BT = 2;
    private final static int OP_CODE_REPORT_STORED_RECORDS = 1;
    private final static int OP_CODE_REPORT_NUMBER_OF_RECORDS = 4;
    private final static int OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE = 5;
    private final static int OP_CODE_RESPONSE_CODE = 6;
    private final static int COMPLETE_RESULT_FROM_METER = 192;
    private final static int OPERATOR_ALL_RECORDS = 1;
    private final static int OPERATOR_GREATER_OR_EQUAL_RECORDS = 3;
    private final static int FILTER_TYPE_SEQUENCE_NUMBER = 1;
    private final static int RESPONSE_SUCCESS = 1;
    private final static int RESPONSE_OP_CODE_NOT_SUPPORTED = 2;
    private final static int RESPONSE_NO_RECORDS_FOUND = 6;
    private final static int RESPONSE_ABORT_UNSUCCESSFUL = 7;
    private final static int RESPONSE_PROCEDURE_NOT_COMPLETED = 8;
    private final static int OP_CODE_SET_FLAG = 225;
    private static final int MAX_DOWNLOAD_COUNT = 1000;
    private final static int MAX_SEQ_UINT16 = 65535;
    public static boolean mIsActivityForeground;
    public static boolean mIsActivityAlive;
    private final IBinder mBinder = new LocalBinder();
    PendingIntent mPendingIntent;
    private Handler mHandler;
    private NotificationCompat.Builder mNoti;
    private NotificationManager mNotiManager;
    private Notification.Builder mNotiBuilder;
    private ScheduledExecutorService mPeriodicScanningScheduler;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mGlucoseMeasurementCharacteristic;
    private BluetoothGattCharacteristic mGlucoseContextCharacteristic;
    private BluetoothGattCharacteristic mRACPCharacteristic;
    private BluetoothGattCharacteristic mDeviceSerialCharacteristic;
    private BluetoothGattCharacteristic mDeviceSoftwareRevisionCharacteristic;
    private BluetoothGattCharacteristic mCustomTimeCharacteristic;
    private boolean mIsScanning = false;
    private boolean mIsPeriodicScanning;
    private ScanCallback mScanCallback;
    private boolean mIsDeviceConnected;
    private String mSerialNum, mSoftwareVer;
    private String[] mSoftwareVersions;
    private int mSoftwareVersion1;
    private int mTimesyncUtcTzCnt;
    private boolean mIsDownloadFinished = false;

    private BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

            if (device == null || mBluetoothGatt == null) {
                return;
            }

            // skip other devices
            if (!device.getAddress().equals(mBluetoothGatt.getDevice().getAddress())) {
                return;
            }

            if (bondState == BluetoothDevice.BOND_BONDED) {
                enableRecordAccessControlPointIndication(mBluetoothGatt);
            } else if (bondState == BluetoothDevice.BOND_NONE) {
                broadcastUpdate(Const.INTENT_BLE_BOND_NONE, "");
            }
        }
    };
    private BluetoothAdapter.LeScanCallback mLEScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (device != null) {
                try {
                    if (ScannerServiceParser.decodeDeviceAdvData(scanRecord)) {
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            connect(device.toString());
                        }
                    }
                } catch (Exception e) {
                    e.getMessage();
                }
            }
        }
    };
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            Log.d("","onConnectionStateChange status: " + status + " // newState: " + newState);

            if (status == 133 || status == 129) return;  //ignore GATT_ERROR or GATT_INTERNAL_ERROR
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                String deviceName = gatt.getDevice().getName();
                stopScan();
                // Show device name in UI
                if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {
                    broadcastUpdate(Const.INTENT_BLE_DEVICE_CONNECTED, deviceName);
                    broadcastUpdate(Const.INTENT_STOP_SCAN, "");
                }
                mBluetoothGatt = gatt;
                // ###3. DISCOVER SERVICES
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mIsDeviceConnected = false;

                if (!mIsDownloadFinished) {
                    broadcastUpdate(Const.INTENT_BLE_READ_COMPLETED, "");
                    showNoti();
                    mIsDownloadFinished = true;
                }

                if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {
                    disconnect();
                    broadcastUpdate(Const.INTENT_BLE_DEVICE_DISCONNECTED, "");
                }
                initScan(); // reinit
            }
        }

        private void initCharacteristics() {
            mGlucoseMeasurementCharacteristic = null;
            mGlucoseContextCharacteristic = null;
            mRACPCharacteristic = null;
            mDeviceSerialCharacteristic = null;
            mDeviceSoftwareRevisionCharacteristic = null;
            mCustomTimeCharacteristic = null;
        }

        public void initVariables() {
            mRecords.clear();
            mTimesyncUtcTzCnt = 0;
            mIsDownloadFinished = false;
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                initCharacteristics();
                initVariables();
                for (BluetoothGattService service : gatt.getServices()) {
                    if (Const.BLE_SERVICE_GLUCOSE.equals(service.getUuid())) {  // Glucose Service // 1808
                        mGlucoseMeasurementCharacteristic = service.getCharacteristic(Const.BLE_CHAR_GLUCOSE_MEASUREMENT);   //2A18
                        mGlucoseContextCharacteristic = service.getCharacteristic(Const.BLE_CHAR_GLUCOSE_CONTEXT);   //2A34
                        // Time Synchronization for some old meters
                        mRACPCharacteristic = service.getCharacteristic(Const.BLE_CHAR_RACP);   //2A52
                    }
                    else if (Const.BLE_SERVICE_DEVICE_INFO.equals(service.getUuid())) {    // Device Info Service // 180A
                        mDeviceSerialCharacteristic = service.getCharacteristic(Const.BLE_CHAR_DEVICE_INFO_SERIALNO);  //2A25
                        mDeviceSoftwareRevisionCharacteristic = service.getCharacteristic(Const.BLE_CHAR_DEVICE_INFO_SOFTWARE_REVISION);     //2A28
                    }
                    // Time Synchronization
                    else if (Const.BLE_SERVICE_CUSTOM_TIME.equals(service.getUuid())) {    // Custome Time Service // FFF0
                        mCustomTimeCharacteristic = service.getCharacteristic(Const.BLE_CHAR_CUSTOM_TIME);  //FFF1
                        if (mCustomTimeCharacteristic != null)
                            gatt.setCharacteristicNotification(mCustomTimeCharacteristic, true);
                    }
                    else if (Const.BLE_SERVICE_CUSTOM_TIME_NEW.equals(service.getUuid())) { // A010
                        mCustomTimeCharacteristic = service.getCharacteristic(Const.BLE_CHAR_CUSTOM_TIME_NEW); // A3BC
                        if (mCustomTimeCharacteristic != null)
                            gatt.setCharacteristicNotification(mCustomTimeCharacteristic, true);
                    }
                }
                // Validate the i-SENS device for required characteristics
                if (mGlucoseMeasurementCharacteristic == null || mRACPCharacteristic == null) {
                    broadcastUpdate(Const.INTENT_BLE_DEVICE_NOT_SUPPORTED, "");
                    return;
                }

                if (mDeviceSoftwareRevisionCharacteristic != null) {
                    // ###4. READ SW REV. of DEVICE
                    readDeviceSoftwareRevision(gatt);
                }

            } else {
                broadcastUpdate(Const.INTENT_BLE_ERROR, getResources().getString(R.string.ERROR_DISCOVERY_SERVICE) + " (" + status + ")");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Const.BLE_CHAR_DEVICE_INFO_SOFTWARE_REVISION.equals(characteristic.getUuid())) { // 2A28
                    mSoftwareVersions = characteristic.getStringValue(0).split("\\.");
                    mSoftwareVersion1 = Integer.parseInt(mSoftwareVersions[0]);
                    broadcastUpdate(Const.INTENT_BLE_SOFTWARE_VERSION, characteristic.getStringValue(0));
                    if (mSoftwareVersion1 > SOFTWARE_REVISION_1) {  //If the version is greater than the supported version, disconnect
                        broadcastUpdate(Const.INTENT_BLE_READ_SOFTWARE_REV, characteristic.getStringValue(0));
                        gatt.disconnect();
                        return;
                    } else if (mSoftwareVersion1 >= SOFTWARE_REVISION_BASE && mSoftwareVersion1 == SOFTWARE_REVISION_1) {   //If the version is greater than or equal to base version AND is the same as the supported version
                        if (mCustomTimeCharacteristic == null) {     //  'custom time characteristic' must be present. (OR disconnect)
                            gatt.disconnect();
                            return;
                        }
                    }

                    if (mDeviceSerialCharacteristic != null) {
                        // ###5. READ SERIAL NO of DEVICE
                        readDeviceSerial(gatt);
                    }
                } else if (Const.BLE_CHAR_DEVICE_INFO_SERIALNO.equals(characteristic.getUuid())) { //2A25
                    mSerialNum = characteristic.getStringValue(0);
                    broadcastUpdate(Const.INTENT_BLE_SERIAL_NUMBER, mSerialNum);
                    // ###6. ENABLE RACP (DESCRIPTOR WRITE)

                    if (mBluetoothGatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDED) { //페어링 안된 경우
                        mBluetoothGatt.getDevice().createBond();
                    } else {
                        enableRecordAccessControlPointIndication(gatt);
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Const.BLE_CHAR_GLUCOSE_MEASUREMENT.equals(descriptor.getCharacteristic().getUuid())) { //2A18
                    // ###8. ENABLE GLUCOSE CONTEXT
                    enableGlucoseContextNotification(gatt);
//                    if (mCustomTimeCharacteristic != null) {
//                        // ###9. ENABLE TIME SYNC
//                        enableTimeSyncNotification(gatt);
//                    }
//
//                    if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {  //If auto-download option not checked, do not proceed with time sync & data download.
//                        broadcastUpdate(Const.INTENT_BLE_CHAR_GLUCOSE_CONTEXT, "");
//                        return;
//                    }
//
//                    if (mCustomTimeCharacteristic == null) { //TimeSync for old meters time characteristic is null
//                        // ###10. TIME SYNC
//                        requestTimeSyncForOldMeter();
//                    }
                }
                if (Const.BLE_CHAR_GLUCOSE_CONTEXT.equals(descriptor.getCharacteristic().getUuid())) { //2A34
                    if (mCustomTimeCharacteristic != null) {
                        // ###9. ENABLE TIME SYNC
                        enableTimeSyncNotification(gatt);
                    }

                    if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {  //If auto-download option not checked, do not proceed with time sync & data download.
                        broadcastUpdate(Const.INTENT_BLE_CHAR_GLUCOSE_CONTEXT, "");
                        return;
                    }

                    if (mCustomTimeCharacteristic == null) { //TimeSync for old meters time characteristic is null
                        // ###10. TIME SYNC
                        requestTimeSyncForOldMeter();
                    }
                }
                if (Const.BLE_CHAR_RACP.equals(descriptor.getCharacteristic().getUuid())) { //2A52
                    // ###7. ENABLE GLUCOSE MEASUREMENT
                    enableGlucoseMeasurementNotification(gatt);
                }
                if (Const.BLE_CHAR_CUSTOM_TIME.equals(descriptor.getCharacteristic().getUuid()) ||
                        mCustomTimeCharacteristic.getUuid().equals(Const.BLE_CHAR_CUSTOM_TIME_NEW)) { //FFF1, A3BC
                    if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) { //If auto-download option not checked, do not proceed with time sync & data download.
                        return;
                    }
                    // ###10. TIME SYNC
                    requestCustomTimeSync();
//                    requestTotalCount();
                }
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    broadcastUpdate(Const.INTENT_BLE_ERROR, getString(R.string.ERROR_AUTH_ERROR_WHILE_BONDED) + " (" + status + ")");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final UUID uuid = characteristic.getUuid();


            if (Const.BLE_CHAR_CUSTOM_TIME.equals(uuid) || Const.BLE_CHAR_CUSTOM_TIME_NEW.equals(uuid)) { //FFF1, A3BC
                int offset = 0;
                final int opCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                offset += 2; // skip the operator

                if (opCode == OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE) { // 05: time result
                    Log.d("", "---requestCustomTimeSync");
                    if (Util.getPreferenceBool(Const.IS_TIMESYNC_UTC_TZ) && mTimesyncUtcTzCnt < 3) {    // if UTC+TZ Time sync option is checked,
                        if (mCustomTimeCharacteristic != null) {
                            requestCustomTimeSync();
                        }
                        return;
                    }
                    // ###11. Request stored data total count
                    requestTotalCount();
                }
            } else if (Const.BLE_CHAR_GLUCOSE_MEASUREMENT.equals(uuid)) { //2A18
                int offset = 0;
                final int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                offset += 1;

                final boolean timeOffsetPresent = (flags & 0x01) > 0;
                final boolean typeAndLocationPresent = (flags & 0x02) > 0;
                final boolean sensorStatusAnnunciationPresent = (flags & 0x08) > 0;
                final boolean contextInfoFollows = (flags & 0x10) > 0;

                int sequenceNumber = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                boolean isSavedData = true;
                GlucoseRecord record = mRecords.get(sequenceNumber);
                if (record == null) {
                    record = new GlucoseRecord();
                    isSavedData = false;
                }
                record.sequenceNumber = sequenceNumber;
                record.flag_context = 0;
                offset += 2;

                final int year = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset + 0);
                final int month = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 2);
                final int day = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 3);
                final int hours = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 4);
                final int minutes = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 5);
                final int seconds = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 6);
                offset += 7;

                final Calendar calendar = Calendar.getInstance();
                calendar.set(year, month - 1, day, hours, minutes, seconds);
                record.time = calendar.getTimeInMillis() / 1000;

                if (timeOffsetPresent) {
                    record.timeoffset = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
                    record.time = record.time + (record.timeoffset * 60);
                    offset += 2;
                }

                if (typeAndLocationPresent) {
                    byte[] value = characteristic.getValue();
                    int glucoseValue = (int) bytesToFloat(value[offset], value[offset + 1]);
                    record.glucoseData = glucoseValue;

                    final int typeAndLocation = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 2);
                    int type = (typeAndLocation & 0xF0) >> 4;
                    record.flag_cs = type == 10 ? 1 : 0;
                    offset += 3;
                }

                if (sensorStatusAnnunciationPresent) {
                    int hilow = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                    if (hilow == 64) record.flag_hilow = -1;//lo
                    if (hilow == 32) record.flag_hilow = 1;//hi
                    offset += 2;
                }

                if (contextInfoFollows == false) {
                    record.flag_context = 1;
                }

                try {
                    if (isSavedData == false) {
                        mRecords.put(record.sequenceNumber, record);
                        final GlucoseRecord glucoseRecord = record;
                        Util.setPreference(mSerialNum, record.sequenceNumber);
                    }
                } catch (Exception e) {
                }
            } else if (Const.BLE_CHAR_GLUCOSE_CONTEXT.equals(uuid)) { //2A34
                int offset = 0;
                final int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                offset += 1;

                final boolean carbohydratePresent = (flags & 0x01) > 0;
                final boolean mealPresent = (flags & 0x02) > 0;
                final boolean moreFlagsPresent = (flags & 0x80) > 0;

                final int sequenceNumber = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                if (moreFlagsPresent) offset += 1;

                if (carbohydratePresent) offset += 3;

                final int meal = mealPresent == true ? characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset) : -1;

                boolean isSavedData = true;
                GlucoseRecord record = mRecords.get(sequenceNumber);
                if (record == null) {
                    record = new GlucoseRecord();
                    isSavedData = false;
                }
                if (record == null || mealPresent == false)
                    return;

                record.sequenceNumber = sequenceNumber;
                record.flag_context = 1;

                switch (meal) {
                    case 0:
                        if (record.flag_cs == 0)
                            record.flag_nomark = 1;
                        break;
                    case 1:
                        record.flag_meal = -1;
                        break;
                    case 2:
                        record.flag_meal = 1;
                        break;
                    case 3:
                        record.flag_fasting = 1;
                        break;
                    case 6:
                        record.flag_ketone = 1;
                        break;
                }
                try {
                    if (isSavedData == false)
                        mRecords.put(record.sequenceNumber, record);
                } catch (Exception e) {
                }

            } else if (Const.BLE_CHAR_RACP.equals(uuid)) { // Record Access Control Point characteristic 2A52
                int offset = 0;
                final int opCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                offset += 2; // skip the operator

                if (opCode == COMPLETE_RESULT_FROM_METER) {  //C0
                    final int requestedOpCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset - 1);
                    switch (requestedOpCode) {
                        case RESPONSE_SUCCESS:  //01
                            broadcastUpdate(Const.INTENT_BLE_READ_COMPLETED, "");
                            showNoti();
                            if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {
                                return;
                            }
                            mBluetoothGatt.writeCharacteristic(characteristic);
                            break;
                        case RESPONSE_OP_CODE_NOT_SUPPORTED:  //02
                            break;
                    }
                } else if (opCode == OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE) {  // 05
                    if (mBluetoothGatt == null || mRACPCharacteristic == null) {
                        broadcastUpdate(Const.INTENT_BLE_ERROR, getResources().getString(R.string.ERROR_CONNECTION_STATE_CHANGE));
                        return;
                    }

                    int totalCnt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                    broadcastUpdate(Const.INTENT_BLE_TOTAL_COUNT, String.valueOf(totalCnt));
                    offset += 2;

                    Log.d("", "---requestTotalCount : "+totalCnt);

                    if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {
                        return;
                    }

                    if (mSerialNum == null)
                        return;

                    int sequence = Util.getPreference(mSerialNum);

                    // ###12. GET RECORDS
                    if (sequence <= 0) {
                        requestBleAll();
                    } else {
                        requestBleMoreEqual(sequence);
                    }

                } else if (opCode == OP_CODE_RESPONSE_CODE) { // 06
                    final int responseCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1);
                    offset += 2;

                    switch (responseCode) {
                        case RESPONSE_SUCCESS:
                        case RESPONSE_NO_RECORDS_FOUND: //06000106
                            if (mIsDownloadFinished) {
                                return;
                            }
                            broadcastUpdate(Const.INTENT_BLE_READ_COMPLETED, "");
                            showNoti();
                            if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD) == false) {
                                return;
                            }

//                            disconnect();

                            mIsDownloadFinished = true;
                            break;
                        case RESPONSE_OP_CODE_NOT_SUPPORTED:
                            broadcastUpdate(Const.INTENT_BLE_OPERATE_NOT_SUPPORTED, "");
                            break;
                        case RESPONSE_PROCEDURE_NOT_COMPLETED:
                        case RESPONSE_ABORT_UNSUCCESSFUL:
                        default:
                            break;
                    }
                }
            }
        }

        private void readDeviceSoftwareRevision(final BluetoothGatt gatt) {
            gatt.readCharacteristic(mDeviceSoftwareRevisionCharacteristic);
        }

        private void readDeviceSerial(final BluetoothGatt gatt) {
            gatt.readCharacteristic(mDeviceSerialCharacteristic);
        }
    };
    private final BroadcastReceiver mBleServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String extraData = intent.getStringExtra(Const.INTENT_BLE_EXTRA_DATA);

            switch (action) {
                case Const.INTENT_ACTIVITY_RESUME:
                    initScan();
                    break;
                case Const.INTENT_ACTIVITY_PAUSE:
                    initScan();
                    break;
                case Const.INTENT_BLE_CONNECT_DEVICE:
                    if (extraData != null && extraData.length() > 0) {
                        connect(extraData);
                    }
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_ON) {
                        initScan();
                    }
                    break;
                case Const.INTENT_START_BLE_SERVICE_FOREGROUND:
//                    startForeground(1, mNoti.build());
                    startForeground(1, mNotiBuilder.setPriority(Notification.PRIORITY_MIN).build());
                    initScan();
                    break;
                case Const.INTENT_STOP_BLE_SERVICE_FOREGROUND:
                    stopForeground(true);
                    mNotiManager.cancelAll();
                    initScan();
                    break;
            }
        }
    };

    private static IntentFilter makeBleServiceIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Const.INTENT_BLE_CONNECT_DEVICE);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(Const.INTENT_START_BLE_SERVICE_FOREGROUND);
        intentFilter.addAction(Const.INTENT_STOP_BLE_SERVICE_FOREGROUND);
        intentFilter.addAction(Const.INTENT_ACTIVITY_RESUME);
        intentFilter.addAction(Const.INTENT_ACTIVITY_PAUSE);
        return intentFilter;
    }

    public static SparseArray<GlucoseRecord> getRecords() {
        return mRecords;
    }

    public class LocalBinder extends Binder {
        public GlucoseBleService getService() {
            return GlucoseBleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopScan();
            close();
            unregisterReceiver(mBondingBroadcastReceiver);
            unregisterReceiver(mBleServiceReceiver);
            if (mPeriodicScanningScheduler != null) {
                mPeriodicScanningScheduler.shutdownNow();
                mPeriodicScanningScheduler = null;
            }
        } catch (Exception e) {
        }

    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();

        // Register receiver to process Bluetooth Intents
        registerReceiver(mBleServiceReceiver, makeBleServiceIntentFilter());

        // Notification
        mNotiManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//        mNoti = new NotificationCompat.Builder(getApplicationContext()).setSmallIcon(R.mipmap.justble).setContentTitle("BLE(GL) Example").setOngoing(true);
//        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
//        mNoti.setContentIntent(mPendingIntent);
//        mNotiManager.notify(1, mNoti.build());

        mNotiBuilder = new Notification.Builder(this)
                .setContentTitle("BLE(GL) Example")
                .setOngoing(true)
                .setWhen(0)
                .setSmallIcon(R.drawable.ic_baseline_health_and_safety_24);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel("mqtt", "mqtt", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("mqtt notification channel");
            notificationChannel.enableLights(true);
            notificationChannel.enableVibration(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mNotiManager.createNotificationChannel(notificationChannel);
            mNotiBuilder.setChannelId("mqtt");
        }

        //
        boolean isBleAvailable = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ? true : false;
        if (isBleAvailable && runningOnKitkatOrHigher()) { // 4.4 or later
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if (mBluetoothAdapter == null) {
                Util.showToast(getString(R.string.ble_not_supported));
            } else {
                initScan();
            }
        } else {
            Util.showToast("BLE off. Turn on ble mode");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // initScan, pauseScan, resumeScan, reinitScan
    private void initScan() {
        //
        stopScan();

        //
        if (Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD)) {
            // make service to foreground(attached with notification) to keep it alive regardless of app status.
            int noti_id = 1;
//            startForeground(noti_id, mNoti.build());  //
            startForeground(1, mNotiBuilder.setPriority(Notification.PRIORITY_MIN).build());
        } else {
            // stop foreground of this service because the app is foreground.
            stopForeground(true);
            mNotiManager.cancelAll();
        }

        // periodic means not always (1 scan per 15s)
        // periodic scanning becomes true when the service goes background
        mIsPeriodicScanning = false;
        if (mPeriodicScanningScheduler != null) {
            mPeriodicScanningScheduler.shutdownNow();
            mPeriodicScanningScheduler = null;
        }

        if (mBluetoothAdapter.isEnabled()
                && mBluetoothAdapter.getBondedDevices().size() > 0
                && Util.getPreferenceBool(Const.IS_AUTO_DOWNLOAD)) {
            if (mIsActivityForeground) {
                startScan();
            } else {
                // periodic repetition of startScan() and stopScan()
                mIsPeriodicScanning = true;
                mPeriodicScanningScheduler = Executors.newSingleThreadScheduledExecutor();
                mPeriodicScanningScheduler.scheduleAtFixedRate(new Runnable() {
                    public void run() {
                        try {
                            startScan();
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    stopScan();
                                }
                            }, 2500);
                        } catch (Exception e) {
                            e.getMessage();
                        }
                    }
                }, 15, 15, TimeUnit.SECONDS);
            }
        }
    }

    public void startScan() {
        // ###1. START SCAN
        mIsDeviceConnected = false;
        try {
            if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (mScanCallback == null) {
                        initCallbackLollipop();
                    }

                    //
                    List<ScanFilter> filters = new ArrayList<ScanFilter>();
                    ScanSettings scansettings = new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .setReportDelay(0)
                            .build();
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mBluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(mScanCallback);
                        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
                        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, scansettings, mScanCallback);
                    }
                } else {
                    // Samsung Note II with Android 4.3 build JSS15J.N7100XXUEMK9 is not filtering by UUID at all. We have to disable it
                    mBluetoothAdapter.startLeScan(mLEScanCallback);
                }
            }
        } catch (Exception e) {
            mIsScanning = false;
            e.getMessage();
        }
        mIsScanning = true;
    }

    public void stopScan() {
        try {
            if (mIsScanning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Stop scan and flush pending scan
                    mBluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(mScanCallback);
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
                } else {
                    mBluetoothAdapter.stopLeScan(mLEScanCallback);
                }
                mIsScanning = false;
            }
        } catch (Exception e) {
            e.getMessage();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initCallbackLollipop() {
        if (mScanCallback != null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            this.mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (result != null) {
                        try {
                            if (ScannerServiceParser.decodeDeviceAdvData(result.getScanRecord().getBytes())) {
                                // already connected device should be ingnored afterwards to prevent duplicated connection
                                if (!mIsDeviceConnected && result.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                                    mIsDeviceConnected = true;
                                    // ###2. CONNECT DEVICE
                                    mIsDeviceConnected = connect(result.getDevice().toString());
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                }
            };
        }
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }

        if (mBluetoothManager != null && mBluetoothManager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
            return false;
        }

        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBondingBroadcastReceiver, filter);

        mBluetoothGatt = device.connectGatt(getApplicationContext(), true, mGattCallback);
        refreshDeviceCache(mBluetoothGatt);
        mBluetoothDeviceAddress = address;

        return true;
    }

    private boolean refreshDeviceCache(BluetoothGatt gatt) {
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                return bool;
            }
        } catch (Exception e) {
            e.getMessage();
        }
        return false;
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Thread.sleep(200);
                mBluetoothGatt.disconnect();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Thread.sleep(200);
                mBluetoothGatt.writeCharacteristic(mRACPCharacteristic);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }
        if (mRecords != null) mRecords.clear();
    }

    private void enableGlucoseMeasurementNotification(final BluetoothGatt gatt) {
        if (mGlucoseMeasurementCharacteristic == null) {
            return;
        }
        gatt.setCharacteristicNotification(mGlucoseMeasurementCharacteristic, true);
        final BluetoothGattDescriptor descriptor = mGlucoseMeasurementCharacteristic.getDescriptor(Const.BLE_DESCRIPTOR);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private void enableGlucoseContextNotification(final BluetoothGatt gatt) {
        if (mGlucoseContextCharacteristic == null) {
            return;
        }
        gatt.setCharacteristicNotification(mGlucoseContextCharacteristic, true);
        final BluetoothGattDescriptor descriptor = mGlucoseContextCharacteristic.getDescriptor(Const.BLE_DESCRIPTOR);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private void enableRecordAccessControlPointIndication(final BluetoothGatt gatt) {
        if (mRACPCharacteristic == null) {
            return;
        }
        gatt.setCharacteristicNotification(mRACPCharacteristic, true);
        final BluetoothGattDescriptor descriptor = mRACPCharacteristic.getDescriptor(Const.BLE_DESCRIPTOR);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private void enableTimeSyncNotification(final BluetoothGatt gatt) {
        if (mCustomTimeCharacteristic == null) {
            return;
        }
        gatt.setCharacteristicNotification(mCustomTimeCharacteristic, true);
        final BluetoothGattDescriptor descriptor = mCustomTimeCharacteristic.getDescriptor(Const.BLE_DESCRIPTOR);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    public boolean requestTimeSyncForOldMeter() {
        if (mBluetoothGatt == null || mRACPCharacteristic == null) {
            broadcastUpdate(Const.INTENT_BLE_ERROR,
                    getString(R.string.ERROR_CONNECTION_STATE_CHANGE) + "null");
            return false;
        }

        setTimeSyncForOldMeter(mRACPCharacteristic);
        return mBluetoothGatt.writeCharacteristic(mRACPCharacteristic);
    }

    public void setTimeSyncForOldMeter(final BluetoothGattCharacteristic characteristic) {
        Calendar currCal = new GregorianCalendar();

        byte bCurrYear1 = (byte) (currCal.get(Calendar.YEAR) & 0xff);
        byte bCurrYear2 = (byte) ((currCal.get(Calendar.YEAR) >> 8) & 0xff);
        byte bCurrMonth = (byte) ((currCal.get(Calendar.MONTH) + 1) & 0xff);
        byte bCurrDay = (byte) (currCal.get(Calendar.DAY_OF_MONTH & 0xff));
        byte bCurrHour = (byte) (currCal.get(Calendar.HOUR_OF_DAY) & 0xff);
        byte bCurrMin = (byte) (currCal.get(Calendar.MINUTE) & 0xff);
        byte bCurrSec = (byte) (currCal.get(Calendar.SECOND) & 0xff);

        byte[] data = {0x04, 0x01, 0x01, 0x00, bCurrYear1, bCurrYear2, bCurrMonth, bCurrDay, bCurrHour, bCurrMin,
                bCurrSec};

        characteristic.setValue(new byte[data.length]);

        for (int i = 0; i < data.length; i++) {
            characteristic.setValue(data);
        }
    }

    public boolean requestCustomTimeSync() {
        if (mBluetoothGatt == null || mCustomTimeCharacteristic == null) {
            broadcastUpdate(Const.INTENT_BLE_ERROR, getResources().getString(R.string.ERROR_CONNECTION_STATE_CHANGE) + "null");
            return false;
        }

        // 1. UTC+TZ -> method use CTS(Current Time Service) in future
        // from rev2.0(?) //setTimeAndTimeZone(utctime, timeoffset);

        // 2. UTC+TZ ->  provisional method for meter whose version is below 1.4
        // 2.1 Reset TimeOffset to 0
        // 2.2 Reset BaseTime to UTC
        // 2.3 Apply LocalTime to 2.2, save final UTC + TimeZone
        if (Util.getPreferenceBool(Const.IS_TIMESYNC_UTC_TZ)) {
            switch (mTimesyncUtcTzCnt) {
                case 0:
                    Calendar calendar =  Calendar.getInstance();
                    calendar.set(2030, 0, 1, 0, 0, 0);
                    setCustomTimeSync(mCustomTimeCharacteristic, calendar);
                    break;
                case 1:
                    setCustomTimeSync(mCustomTimeCharacteristic, GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC")));
                    break;
                case 2:
                    setCustomTimeSync(mCustomTimeCharacteristic, new GregorianCalendar());
                    break;
            }
        }
        // 3. Local+TimeOffset -> previous method
        else {
            setCustomTimeSync(mCustomTimeCharacteristic, new GregorianCalendar());
        }
        return mBluetoothGatt.writeCharacteristic(mCustomTimeCharacteristic);
    }

    private void setCustomTimeSync(final BluetoothGattCharacteristic characteristic, Calendar currCal) {
        if (characteristic == null) return;

        mTimesyncUtcTzCnt++;

        byte bCurrYear1 = (byte) (currCal.get(Calendar.YEAR) & 0xff);
        byte bCurrYear2 = (byte) ((currCal.get(Calendar.YEAR) >> 8) & 0xff);
        byte bCurrMonth = (byte) ((currCal.get(Calendar.MONTH) + 1) & 0xff);
        byte bCurrDay = (byte) (currCal.get(Calendar.DAY_OF_MONTH) & 0xff);
        byte bCurrHour = (byte) (currCal.get(Calendar.HOUR_OF_DAY) & 0xff);
        byte bCurrMin = (byte) (currCal.get(Calendar.MINUTE) & 0xff);
        byte bCurrSec = (byte) (currCal.get(Calendar.SECOND) & 0xff);

        byte op_code_1 = (byte) ((byte) COMPLETE_RESULT_FROM_METER & 0xff);
        byte[] data = {op_code_1, 0x03, 0x01, 0x00, bCurrYear1, bCurrYear2, bCurrMonth, bCurrDay, bCurrHour, bCurrMin, bCurrSec};

        characteristic.setValue(new byte[data.length]);

        for (int i = 0; i < data.length; i++) {
            characteristic.setValue(data);
        }

    }
    private void requestTotalCount() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getTotalDataCnt() == false) {
                    try {
                        Thread.sleep(500);
                        getTotalDataCnt();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
    public boolean getTotalDataCnt() {
        if (mBluetoothGatt == null || mRACPCharacteristic == null) {
            broadcastUpdate(Const.INTENT_BLE_ERROR, getResources().getString(R.string.ERROR_CONNECTION_STATE_CHANGE) + "null");
            return false;
        }
        Log.d("", "---getTotalDataCnt");
        setOpCode(mRACPCharacteristic, OP_CODE_REPORT_NUMBER_OF_RECORDS, OPERATOR_ALL_RECORDS);
        return mBluetoothGatt.writeCharacteristic(mRACPCharacteristic);
    }

    public boolean getAllRecords() {
        if (mBluetoothGatt == null || mRACPCharacteristic == null) {
            broadcastUpdate(Const.INTENT_BLE_ERROR, getResources().getString(R.string.ERROR_CONNECTION_STATE_CHANGE) + "null");
            return false;
        }
        Log.d("", "---getAllRecords");
        mRecords.clear();
        setOpCode(mRACPCharacteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS);
        return mBluetoothGatt.writeCharacteristic(mRACPCharacteristic);
    }

    private void requestBleAll() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getAllRecords() == false) {
                    try {
                        Thread.sleep(500);
                        getAllRecords();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void requestBleMoreEqual(final int sequence) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getRecordsGreaterOrEqual(sequence + 1) == false) {
                    try {
                        Thread.sleep(500);
                        getRecordsGreaterOrEqual(sequence + 1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public boolean getRecordsGreaterOrEqual(int sequence) {
        if (mBluetoothGatt == null || mRACPCharacteristic == null) {
            broadcastUpdate(Const.INTENT_BLE_ERROR, getResources().getString(R.string.ERROR_CONNECTION_STATE_CHANGE) + "null");
            return false;
        }

        mRecords.clear();

        if (mCustomTimeCharacteristic == null) { //0403
            setOpCode(mRACPCharacteristic, OP_CODE_REPORT_NUMBER_OF_RECORDS, OPERATOR_GREATER_OR_EQUAL_RECORDS, sequence);
        } else {
            setOpCode(mRACPCharacteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_GREATER_OR_EQUAL_RECORDS, sequence);
        }

        return mBluetoothGatt.writeCharacteristic(mRACPCharacteristic);
    }

    private void setOpCode(final BluetoothGattCharacteristic characteristic, final int opCode, final int operator, final Integer... params) {
        if (characteristic == null) return;

        final int size = 2 + ((params.length > 0) ? 1 : 0) + params.length * 2; // 1 byte for opCode, 1 for operator, 1 for filter type (if parameters exists) and 2 for each parameter
        characteristic.setValue(new byte[size]);

        int offset = 0;
        characteristic.setValue(opCode, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        offset += 1;

        characteristic.setValue(operator, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        offset += 1;

        if (params.length > 0) {
            characteristic.setValue(FILTER_TYPE_SEQUENCE_NUMBER, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
            offset += 1;

            for (final Integer i : params) {
                characteristic.setValue(i, BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;
            }
        }
    }

    private void broadcastUpdate(final String action, final String data) {
        final Intent intent = new Intent(action);
        if (data != "")
            intent.putExtra(Const.INTENT_BLE_EXTRA_DATA, data);
        sendBroadcast(intent);
    }

    private float bytesToFloat(byte b0, byte b1) {
        return (float) unsignedByteToInt(b0) + ((unsignedByteToInt(b1) & 0x0F) << 8);

    }

    private int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    private void showNoti() {
        if (mIsActivityForeground == false) {
            mNoti = new NotificationCompat.Builder(getApplicationContext()).setSmallIcon(R.drawable.ic_baseline_health_and_safety_24).setContentTitle("BLE(GL) Example").setOngoing(true);
            mNoti.setContentIntent(mPendingIntent);
            if (mRecords != null && mRecords.size() > 0) {
                GlucoseRecord record = mRecords.valueAt(mRecords.size() - 1);
                if (record.flag_ketone == 1) {
                    mNoti.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
                            .setContentText(String.valueOf((int) record.glucoseData / KetoneMultiplier) + "mmol/L" + " (K)");
                } else {
                    mNoti.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
                            .setContentText(String.valueOf((int) record.glucoseData) + "mg/dL");
                }
            } else {
                mNoti.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
                        .setContentText("No data downloaded.");
            }
            mNotiManager.notify(1, mNoti.build());

            mHandler.postDelayed(new Runnable() { // To disappear Heads Up notification after 5 seconds
                @Override
                public void run() {
                    mNoti.setPriority(Notification.PRIORITY_DEFAULT).setDefaults(Notification.DEFAULT_LIGHTS);
                    mNotiManager.notify(1, mNoti.build());
                }
            }, 5000);
        }
    }

    public boolean timeSync() {
        mTimesyncUtcTzCnt = 0;
        boolean result;
        if (mCustomTimeCharacteristic != null) {    //TimeSync for new meters
            result = requestCustomTimeSync();
        } else {    //TimeSync for some old meters time characteristic is null
            result = requestTimeSyncForOldMeter();
        }
        if (result == true) {
            broadcastUpdate(Const.INTENT_BLE_TIMESYNC_SUCCESS, "");
        }
        return result;
    }


}