package jp.co.thcomp.bluetoothhelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;

public class FoundLeDevice {
    private BluetoothDevice mDevice;

    private int mRssi;

    private byte[] mRawScanRecord;

    private ScanResult mScanResult;

    private List<ParcelUuid> mServiceUuidList;

    private List<BluetoothGattService> mDiscoveredServiceList;

    private int mCallbackType;

    public FoundLeDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
        mDevice = device;
        mRssi = rssi;
        mRawScanRecord = scanRecord;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FoundLeDevice(int callbackType, ScanResult scanResult) {
        mCallbackType = callbackType;
        mDevice = scanResult.getDevice();
        mRssi = scanResult.getRssi();
        mScanResult = scanResult;

        ScanRecord scanRecord = scanResult.getScanRecord();
        if (scanRecord != null) {
            mRawScanRecord = scanRecord.getBytes();
        }
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public int getRssi() {
        return mRssi;
    }

    public byte[] getRawScanRecord() {
        return mRawScanRecord;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public byte[] getManufactureDataArray(int id) {
        return mScanResult != null ? mScanResult.getScanRecord().getManufacturerSpecificData(id) : null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public List<ParcelUuid> getServiceUuidList() {
        if (mServiceUuidList == null) {
            mServiceUuidList = mScanResult.getScanRecord().getServiceUuids();
        }

        return mServiceUuidList;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public int getCallbackType() {
        return mCallbackType;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    void setServiceList(List<BluetoothGattService> serviceList) {
        mDiscoveredServiceList = serviceList;

        if (serviceList != null && serviceList.size() > 0) {
            mServiceUuidList = new ArrayList<>();
            for (BluetoothGattService service : serviceList) {
                mServiceUuidList.add(ParcelUuid.fromString(service.getUuid().toString()));
            }
        }
    }

    public List<BluetoothGattService> getServiceList() {
        return mDiscoveredServiceList;
    }
}
