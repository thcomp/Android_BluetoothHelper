package jp.co.thcomp.bluetoothhelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.List;

public class FoundLeDevice {
    private BluetoothDevice mDevice;

    private int mRssi;

    private byte[] mRawScanRecord;

    private ScanResult mScanResult;

    private List<ParcelUuid> mServiceUuidList;

    public FoundLeDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
        mDevice = device;
        mRssi = rssi;
        mRawScanRecord = scanRecord;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FoundLeDevice(ScanResult scanResult) {
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

    void setServiceUuidList(List<ParcelUuid> serviceUuidList) {
        mServiceUuidList = serviceUuidList;
    }
}
