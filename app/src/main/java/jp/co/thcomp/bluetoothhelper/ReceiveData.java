package jp.co.thcomp.bluetoothhelper;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

public class ReceiveData {
    public BluetoothDevice device;
    public UUID uuid;
    public byte[] data;
    public int dataSize;
}
