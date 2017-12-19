package jp.co.thcomp.bluetoothhelper;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import java.util.UUID;

public class BluetoothClient {
    //public static final int ClientStatusDisableDiscoverable = BluetoothAccessHelper.StatusDisableDiscoverable;
    public static final int ClientStatusDisableBluetooth = BluetoothAccessHelper.StatusDisableBluetooth;
    public static final int ClientStatusNoSupportBluetooth = BluetoothAccessHelper.StatusNoSupportBluetooth;
    public static final int ClientStatusInit = BluetoothAccessHelper.StatusInit;
    public static final int ClientStatusProgress = BluetoothAccessHelper.StatusProgress;
    public static final int ClientStatusStartBluetooth = BluetoothAccessHelper.StatusStartBluetooth;
    //public static final int ClientStatusStartDiscoverable = BluetoothAccessHelper.StatusStartDiscoverable;

    public interface OnClientStatusChangeListener extends BluetoothAccessHelper.OnBluetoothStatusListener {
        // BluetoothClientによるラップのみ
    }

    public interface OnFoundDeviceListener extends DeviceDiscoverer.OnFoundDeviceListener {
        // BluetoothClientによるラップのみ
    }

    public interface OnNotifyResultListener extends BluetoothAccessHelper.OnNotifyResultListener {
        // BluetoothClientによるラップのみ
    }

    private Context mContext;
    private BluetoothAccessHelper mBtHelper;
    private OnDataReceiveListener mDataReceiveListener;
    private DataReceiveThread mDataReceiveThread;
    private OnClientStatusChangeListener mClientStatusChangeListener;
    private boolean mStartClient = false;
    private BluetoothDevice mTargetDevice;
    private UUID mTargetUuid;

    public BluetoothClient(Context context) {
        mBtHelper = new BluetoothAccessHelper(context);
        mBtHelper.setOnBluetoothStatusListener(getBtStatusListener());

        mContext = context;
    }

    public void setOnClientStatusChangeListener(OnClientStatusChangeListener listener) {
        mClientStatusChangeListener = listener;
    }

    public void setOnNotifyResultListener(OnNotifyResultListener listener) {
        mBtHelper.setOnNotifyResultListener(listener);
    }

    public void setOnDataReceiveListener(OnDataReceiveListener listener) {
        mDataReceiveListener = listener;

        if (mDataReceiveThread != null) {
            mDataReceiveThread.setOnDataReceiveListener(listener);
        }
    }

    public boolean setDeviceName(String deviceName) {
        return mBtHelper.setDeviceName(deviceName);
    }

    public boolean restoreDeviceName() {
        return mBtHelper.restoreDeviceName();
    }

    public boolean sendData(BluetoothDevice targetDevice, UUID targetUuid, byte[] data) {
        boolean availableThisInstance = false;

        if (mTargetDevice != null && mTargetUuid != null && mTargetDevice.equals(targetDevice) && mTargetUuid.equals(targetUuid)) {
            availableThisInstance = true;
        } else if (mTargetDevice == null && mTargetUuid == null) {
            availableThisInstance = true;
        }

        if (availableThisInstance) {
            mTargetDevice = targetDevice;
            mTargetUuid = targetUuid;
            mBtHelper.sendData(mTargetUuid, mTargetDevice, data);

            if (mDataReceiveThread == null) {
                mDataReceiveThread = new DataReceiveThread(mBtHelper, mTargetDevice, mTargetUuid);
                mDataReceiveThread.setOnDataReceiveListener(mDataReceiveListener);
                mDataReceiveThread.start();
            }
        }

        return availableThisInstance;
    }

    public void startClient() {
        if (!mStartClient) {
            mStartClient = true;
            mBtHelper.startBluetoothHelper();
        }
    }

    public void stopClient() {
        if (mDataReceiveThread != null) {
            if (mDataReceiveThread.isRun()) {
                mDataReceiveThread.stop();
                mDataReceiveThread = null;
            }
            mBtHelper.stopBluetoothHelper();
        }
    }

    private BluetoothAccessHelper.OnBluetoothStatusListener getBtStatusListener() {
        return new BluetoothAccessHelper.OnBluetoothStatusListener() {
            @Override
            public void onStatusChange(int status, int scanMode) {
                OnClientStatusChangeListener listener = mClientStatusChangeListener;
                if (listener != null) {
                    listener.onStatusChange(status, BluetoothAccessHelper.sScanMode);
                }
            }
        };
    }
}
