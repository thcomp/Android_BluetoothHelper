package jp.co.thcomp.bluetoothhelper;

import android.bluetooth.BluetoothDevice;

public class BleTransferSettings {
    public static final int DefaultMTU = 20;

    private BluetoothDevice mTargetDevice;
    private int mMtu = DefaultMTU;

    public BleTransferSettings(BluetoothDevice targetDevice) {
        mTargetDevice = targetDevice;
    }

    public void setMTU(int mtu) {
        mMtu = mtu;
    }

    public int getMTU() {
        return mMtu;
    }
}
