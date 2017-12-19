package jp.co.thcomp.bluetoothhelper;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

class DataReceiveThread implements Runnable {
    private BluetoothAccessHelper mBtHelper;
    private BluetoothDevice mTargetDevice;
    private UUID mTargetUuid;
    private OnDataReceiveListener mDataReceiveListener;
    private boolean mRunThread = false;

    public DataReceiveThread(BluetoothAccessHelper btHelper, BluetoothDevice targetDevice, UUID targetUuid) {
        mBtHelper = btHelper;
        mTargetDevice = targetDevice;
        mTargetUuid = targetUuid;
    }

    public void setOnDataReceiveListener(OnDataReceiveListener listener) {
        mDataReceiveListener = listener;
    }

    public boolean isRun() {
        return mRunThread;
    }

    public void start() {
        if (!mRunThread) {
            mRunThread = true;
            new Thread(this).start();
        }
    }

    public void stop() {
        if (mRunThread) {
            mRunThread = false;
        }
    }

    @Override
    public void run() {
        if (mTargetDevice != null && mTargetUuid != null) {
            ReceiveData receiveData = new ReceiveData();
            receiveData.device = mTargetDevice;
            receiveData.uuid = mTargetUuid;
            receiveData.data = new byte[1024];

            while (mRunThread) {
                receiveData.dataSize = mBtHelper.readData(mTargetDevice, mTargetUuid, receiveData.data);
                if (receiveData.dataSize > 0) {
                    OnDataReceiveListener listener = mDataReceiveListener;

                    if (listener != null) {
                        listener.onDataReceive(receiveData);
                    }
                }
            }
        }
    }
}
