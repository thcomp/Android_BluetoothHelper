package jp.co.thcomp.bluetoothhelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleCentral {
    private static final long DefaultConnectionTimeoutMS = 30 * 1000;
    private static final int MsgConnectionTimeout = "MsgConnectionTimeout".hashCode();
    public static final int StatusDisableBluetooth = BluetoothAccessHelper.StatusDisableBluetooth;
    public static final int StatusNoSupportBluetooth = BluetoothAccessHelper.StatusNoSupportBluetooth;
    public static final int StatusInit = BluetoothAccessHelper.StatusInit;
    public static final int StatusStartBluetooth = BluetoothAccessHelper.StatusStartBluetooth;

    private Context mContext;
    private BluetoothAccessHelper mBtHelper;
    private OnBluetoothStatusListener mBluetoothStatusListener;
    private OnFoundLeDeviceListener mFoundLeDeviceListener;
    private OnDataReceiveListener mDataReceiveListener;
    private int mCentralState = CentralStateInit;
    private DeviceDiscoverer mDeviceDiscoverer;
    private HashMap<BluetoothDevice, BluetoothGatt> mConnectedGattMap = new HashMap<>();
    private HashMap<BluetoothDevice, Long> mConnectionTimeoutMap = new HashMap<>();
    private HashMap<BluetoothDevice, BleReceiveDataProvider> mReceiveDataMap = new HashMap<>();
    private Handler mMainLooperHandler;
    private long mConnectionTimeoutMS = DefaultConnectionTimeoutMS;

    private static final int CentralStateInit = 0;
    private static final int CentralStateStart = 1;

    public BleCentral(Context context) {
        mContext = context;
        mBtHelper = new BluetoothAccessHelper(context);
        mBtHelper.setOnBluetoothStatusListener(getBluetoothStatusListener());

        mDeviceDiscoverer = DeviceDiscoverer.getInstance(context);
        mMainLooperHandler = new Handler(context.getMainLooper(), mMainLooperCallback);
    }

    public void setOnBluetoothStatusListener(OnBluetoothStatusListener listener) {
        mBluetoothStatusListener = listener;
    }

    public void setOnFoundLeDeviceListener(OnFoundLeDeviceListener listener) {
        mFoundLeDeviceListener = listener;
    }

    public void setOnDataReceiveListener(OnDataReceiveListener listener) {
        mDataReceiveListener = listener;
    }

    public void start() {
        if (mCentralState == CentralStateInit) {
            mCentralState = CentralStateStart;
            mBtHelper.startBluetoothHelper();
        }
    }

    public void stop() {
        if ((mCentralState & CentralStateStart) == CentralStateStart) {
            mBtHelper.stopBluetoothHelper();
            mCentralState &= (~CentralStateStart);
        }
    }

    public boolean startScanPeripheral(int duration) {
        boolean ret = false;
        if ((mCentralState & CentralStateStart) == CentralStateStart) {
            ret = mDeviceDiscoverer.startDiscoverLeDevices(duration, getFoundLeDeviceListener());
        }
        return ret;
    }

    public void stopScanPeripheral() {
        mDeviceDiscoverer.stopDiscoverLeDevices(mFoundLeDeviceListener, true);
    }

    public BluetoothGattCharacteristic[] readCharacteristics(BluetoothDevice device, UUID serviceUuid) {
        connectPeripheral(device);
        BluetoothGatt gatt = mConnectedGattMap.get(device);
        ArrayList<BluetoothGattCharacteristic> ret = new ArrayList<>();

        if (gatt != null) {
            BluetoothGattService service = gatt.getService(serviceUuid);
            if (service != null) {
                ret.addAll(service.getCharacteristics());

                for (BluetoothGattService includedService : service.getIncludedServices()) {
                    ret.addAll(Arrays.asList(readCharacteristics(includedService)));
                }
            }
        }

        return ret.toArray(new BluetoothGattCharacteristic[0]);
    }

    public BluetoothGattCharacteristic[] readAllCharacteristics(BluetoothDevice device) {
        BluetoothGatt gatt = mConnectedGattMap.get(device);
        ArrayList<BluetoothGattCharacteristic> ret = new ArrayList<>();

        if (gatt != null) {
            BluetoothGattCharacteristic rootCharacteristic = new BluetoothGattCharacteristic(null, 0, 0);


            List<BluetoothGattService> serviceList = gatt.getServices();
            if (serviceList.size() > 0) {
                for (BluetoothGattService service : serviceList) {
                    ret.addAll(Arrays.asList(readCharacteristics(service)));
                }
            }
        }

        return ret.toArray(new BluetoothGattCharacteristic[0]);
    }

    private BluetoothGattCharacteristic[] readCharacteristics(BluetoothGattService service) {
        ArrayList<BluetoothGattCharacteristic> ret = new ArrayList<>();
        List<BluetoothGattService> serviceList = service.getIncludedServices();

        if (serviceList != null && serviceList.size() > 0) {
            for (BluetoothGattService includedService : serviceList) {
                ret.addAll(Arrays.asList(readCharacteristics(includedService)));
            }
        }
        ret.addAll(service.getCharacteristics());

        return ret.toArray(new BluetoothGattCharacteristic[0]);
    }

    public void connectPeripheral(BluetoothDevice device) {
        long connectionTimeoutMS = System.currentTimeMillis() + mConnectionTimeoutMS;
        synchronized (mConnectedGattMap) {
            BluetoothGatt gatt = mConnectedGattMap.get(device);
            if (gatt == null) {
                gatt = device.connectGatt(mContext, true, mBtGattCallback);
                if (gatt != null) {
                    mConnectedGattMap.put(device, gatt);
                }
            }
        }
    }

    public void disconnectPeripheral(BluetoothDevice device) {
        synchronized (mConnectedGattMap) {
            BluetoothGatt gatt = mConnectedGattMap.remove(device);
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        }
    }

    private BluetoothAccessHelper.OnBluetoothStatusListener getBluetoothStatusListener() {
        return new BluetoothAccessHelper.OnBluetoothStatusListener() {
            @Override
            public void onStatusChange(int status, int scanMode) {
                BleCentral.OnBluetoothStatusListener listener = mBluetoothStatusListener;

                switch (status) {
                    case StatusDisableBluetooth:
                        if (listener != null) {
                            listener.onStatusChange(status, scanMode);
                        }
                        break;
                    case StatusNoSupportBluetooth:
                        if (listener != null) {
                            listener.onStatusChange(status, scanMode);
                        }
                        break;
                    case StatusStartBluetooth:
                        if (listener != null) {
                            listener.onStatusChange(status, scanMode);
                        }
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private DeviceDiscoverer.OnFoundLeDeviceListener getFoundLeDeviceListener() {
        return new DeviceDiscoverer.OnFoundLeDeviceListener() {
            @Override
            public void onFoundLeDevice(BluetoothDevice device) {
                OnFoundLeDeviceListener listener = mFoundLeDeviceListener;
                if (listener != null) {
                    listener.onFoundLeDevice(device);
                }
            }

            @Override
            public void onTimeoutFindLeDevice() {
                OnFoundLeDeviceListener listener = mFoundLeDeviceListener;
                if (listener != null) {
                    listener.onTimeoutFindLeDevice();
                }
            }
        };
    }

    private Handler.Callback mMainLooperCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == MsgConnectionTimeout) {
                ConnectionInfo connectInfo = (ConnectionInfo) message.obj;
                if ((connectInfo != null) && (connectInfo.device != null)) {
                    boolean connectionTimeout = false;
                    synchronized (mConnectedGattMap) {
                        Long connectionTimeoutMS = mConnectionTimeoutMap.get(connectInfo.device);
                        if (connectionTimeoutMS != null) {
                            if (connectionTimeoutMS > connectInfo.connectionTimeoutMS) {
                                // 更新されているので、無視
                            } else {
                                connectionTimeout = true;
                            }
                        }
                    }

                    if (connectionTimeout) {
                        // コネクションが所定期間使われなかったので切断
                        disconnectPeripheral(connectInfo.device);
                    }
                }
            }
            return false;
        }
    };

    private BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            BluetoothDevice device = gatt.getDevice();
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                long connectionTimeoutMS = System.currentTimeMillis() + mConnectionTimeoutMS;

                synchronized (mConnectedGattMap) {
                    mConnectionTimeoutMap.put(gatt.getDevice(), connectionTimeoutMS);
                    mMainLooperHandler.sendMessageDelayed(Message.obtain(mMainLooperHandler, MsgConnectionTimeout, new ConnectionInfo(device, connectionTimeoutMS)), mConnectionTimeoutMS);
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                synchronized (mConnectedGattMap) {
                    mConnectionTimeoutMap.remove(device);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }
    };

    private static class ConnectionInfo {
        public BluetoothDevice device;
        public long connectionTimeoutMS;

        public ConnectionInfo(BluetoothDevice device, long connectionTimeoutMS) {
            this.device = device;
            this.connectionTimeoutMS = connectionTimeoutMS;
        }
    }

    public interface OnBluetoothStatusListener extends BluetoothAccessHelper.OnBluetoothStatusListener {
        // 拡張なし
    }

    public interface OnFoundLeDeviceListener extends DeviceDiscoverer.OnFoundLeDeviceListener {
        // 拡張なし
    }
}
