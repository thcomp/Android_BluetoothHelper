package jp.co.thcomp.bluetoothhelper;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import java.util.HashMap;
import java.util.UUID;

public class BluetoothServer {
    public static final int ServerStatusUnsupport = BluetoothAccessHelper.StatusFirstExtension + 1;
    public static final int ServerStatusStarted = BluetoothAccessHelper.StatusFirstExtension + 2;
    public static final int ServerStatusAcceptConnection = BluetoothAccessHelper.ServerStatusAcceptConnection;
    public static final int ServerStatusCreateConnection = BluetoothAccessHelper.ServerStatusCreateConnection;
    public static final int ServerStatusDisconnectConnection = BluetoothAccessHelper.ServerStatusDisconnectConnection;
    public static final int ServerStatusDisableDiscoverable = BluetoothAccessHelper.StatusDisableDiscoverable;
    public static final int ServerStatusDisableBluetooth = BluetoothAccessHelper.StatusDisableBluetooth;
    public static final int ServerStatusStartDiscoverable = BluetoothAccessHelper.StatusStartDiscoverable;

    public interface OnServerStatusChangeListener extends BluetoothAccessHelper.OnServerStatusChangeListener {
    }

    private BluetoothAccessHelper mBtHelper;
    private UUID mAcceptUUID;
    private boolean mStartServer = false;
    private HashMap<BluetoothDevice, DataReceiveThread> mDataReceiveThreadMap = new HashMap<BluetoothDevice, DataReceiveThread>();
    private OnServerStatusChangeListener mServerStatusChangeListener;
    private OnDataReceiveListener mDataReceiveListener;
    private Integer mServerDiscoverableDuration = null;

    public BluetoothServer(Context context, String applicationName, UUID acceptUUID) {
        mAcceptUUID = acceptUUID;
        mBtHelper = new BluetoothAccessHelper(context, applicationName, acceptUUID.toString());
        mBtHelper.setOnServerStatusChangeListener(getServerStatusChangeListener());
        mBtHelper.setOnBluetoothStatusListener(getBtStatusListener());
    }

    public void startServer() {
        startServer(0);
    }

    public void startServer(Integer durationS) {
        mServerDiscoverableDuration = durationS;
        if (!mStartServer) {
            mStartServer = true;
            mBtHelper.startBluetoothHelper();
        }
    }

    public void stopServer() {
        if (mStartServer) {
            mStartServer = false;
            mBtHelper.stopServer();
            mBtHelper.stopBluetoothHelper();
        }
    }

    public void setOnServerStatusChangeListener(OnServerStatusChangeListener listener) {
        mServerStatusChangeListener = listener;
    }

    public void setOnDataReceiveListener(OnDataReceiveListener listener) {
        mDataReceiveListener = listener;
    }

    public boolean setDeviceName(String deviceName) {
        return mBtHelper.setDeviceName(deviceName);
    }

    public boolean restoreDeviceName() {
        return mBtHelper.restoreDeviceName();
    }

    public void sendData(BluetoothDevice device, byte[] data) {
        mBtHelper.sendData(mAcceptUUID, device, data);
    }

    private BluetoothAccessHelper.OnServerStatusChangeListener getServerStatusChangeListener() {
        return new BluetoothAccessHelper.OnServerStatusChangeListener() {
            @Override
            public void onStatusChange(int serverStatus, BluetoothDevice targetDevice, UUID targetUUID) {
                OnServerStatusChangeListener listener = mServerStatusChangeListener;

                switch (serverStatus) {
                    case ServerStatusAcceptConnection:
                    case ServerStatusCreateConnection: {
                        // threadを準備
                        DataReceiveThread thread = mDataReceiveThreadMap.get(targetDevice);
                        if (thread == null) {
                            thread = new DataReceiveThread(mBtHelper, targetDevice, targetUUID);
                            thread.setOnDataReceiveListener(mDataReceiveListener);
                            mDataReceiveThreadMap.put(targetDevice, thread);
                            thread.start();
                        }

                        if (listener != null) {
                            listener.onStatusChange(serverStatus, targetDevice, targetUUID);
                        }
                        break;
                    }
                    case ServerStatusDisconnectConnection: {
                        DataReceiveThread thread = mDataReceiveThreadMap.remove(targetDevice);

                        if (thread != null) {
                            // threadを停止させる
                            thread.stop();
                        }

                        if (listener != null) {
                            listener.onStatusChange(serverStatus, targetDevice, targetUUID);
                        }
                    }
                    default:
                        // 一応
                        if (listener != null) {
                            listener.onStatusChange(serverStatus, targetDevice, targetUUID);
                        }
                        break;
                }
            }
        };
    }

    private BluetoothAccessHelper.OnBluetoothStatusListener getBtStatusListener() {
        return new BluetoothAccessHelper.OnBluetoothStatusListener() {
            @Override
            public void onStatusChange(int status, int scanMode) {
                OnServerStatusChangeListener listener = mServerStatusChangeListener;

                switch (status) {
                    case BluetoothAccessHelper.StatusNoSupportBluetooth:
                        if (listener != null) {
                            listener.onStatusChange(ServerStatusUnsupport, null, null);
                        }
                        break;
                    case BluetoothAccessHelper.StatusStartBluetooth:
                        if (mStartServer) {
                            mBtHelper.startServer();

                            if (mServerDiscoverableDuration != null && mServerDiscoverableDuration >= 0) {
                                mBtHelper.requestDiscoverable(mServerDiscoverableDuration);
                            }
                        }
                        if (listener != null) {
                            listener.onStatusChange(ServerStatusStarted, null, null);
                        }
                        break;
                    default:
                        if (listener != null) {
                            listener.onStatusChange(status, null, null);
                        }
                        break;
                }
            }
        };
    }
}
