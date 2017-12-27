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
import android.os.ParcelUuid;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import jp.co.thcomp.util.LogUtil;
import jp.co.thcomp.util.ToastUtil;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleCentral {
    private static final String TAG = BleCentral.class.getSimpleName();
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
    private HashMap<BluetoothDevice, BluetoothGatt> mDiscoveringServiceDeviceMap = new HashMap<>();
    private HashMap<BluetoothDevice, FoundLeDevice> mFoundLeDeviceMap = new HashMap<>();
    private HashMap<BluetoothDevice, BluetoothGatt> mConnectedGattMap = new HashMap<>();
    private HashMap<BluetoothDevice, Long> mConnectionTimeoutMap = new HashMap<>();
    private BluetoothGattCharacteristic mCurrentReadingCharacteristic = null;
    private ArrayList<BluetoothGattCharacteristic> mRequestingReadCharacteristicList = new ArrayList<>();
    private Handler mMainLooperHandler;
    private long mConnectionTimeoutMS = DefaultConnectionTimeoutMS;

    private static final int CentralStateInit = 0;
    private static final int CentralStateStart = 1;
    private static final int CentralStateDiscoverPeripheral = 2;

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
        stopScanPeripheral();

        if ((mCentralState & CentralStateStart) == CentralStateStart) {
            mBtHelper.stopBluetoothHelper();
            mCentralState &= (~CentralStateStart);
        }
    }

    public boolean startScanPeripheral(int duration) {
        LogUtil.d(TAG, "startScanPeripheral(S): duration: " + duration);

        boolean ret = false;
        if (((mCentralState & CentralStateStart) == CentralStateStart) && ((mCentralState & CentralStateDiscoverPeripheral) == CentralStateInit)) {
            mFoundLeDeviceMap.clear();
            ret = mDeviceDiscoverer.startDiscoverLeDevices(duration, mRootFoundLeDeviceListener);
            if (ret) {
                mCentralState |= CentralStateDiscoverPeripheral;
            }
        }

        LogUtil.d(TAG, "startScanPeripheral(E): ret: " + ret);
        return ret;
    }

    public void stopScanPeripheral() {
        LogUtil.d(TAG, "stopScanPeripheral(S):");
        if ((mCentralState & CentralStateDiscoverPeripheral) == CentralStateDiscoverPeripheral) {
            mCentralState &= (~CentralStateDiscoverPeripheral);
            mDeviceDiscoverer.stopDiscoverLeDevices(mFoundLeDeviceListener, true);
        }
        LogUtil.d(TAG, "stopScanPeripheral(E):");
    }

    public FoundLeDevice[] getFoundLeDeviceList() {
        return mFoundLeDeviceMap.values().toArray(new FoundLeDevice[0]);
    }

    public void writeCharacteristic(BluetoothDevice device, UUID serviceUuid, UUID characteristicUuid, byte[] data) {
        LogUtil.d(TAG, "writeCharacteristic(S): service uuid: " + serviceUuid + ", characteristic uuid: " + characteristicUuid);

        connectPeripheral(device);
        BluetoothGatt gatt = mConnectedGattMap.get(device);
        if (gatt != null) {
            BluetoothGattService service = gatt.getService(serviceUuid);
            if (service == null) {
                // 数秒待ってみよう
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                service = gatt.getService(serviceUuid);
            }

            if (service != null) {
                BluetoothGattCharacteristic targetCharacteristic = service.getCharacteristic(characteristicUuid);
                if (targetCharacteristic != null) {
                    targetCharacteristic.setValue(data);
                    if (gatt.writeCharacteristic(targetCharacteristic)) {
                        ToastUtil.showToast(mContext, "success write characteristic data", Toast.LENGTH_LONG);
                        LogUtil.d(TAG, "success write characteristic data: " + Arrays.toString(data));
                    } else {
                        LogUtil.d(TAG, "fail write characteristic data: " + Arrays.toString(data));
                    }
                } else {
                    LogUtil.e(TAG, "not found characteristic: " + characteristicUuid);
                }
            } else {
                LogUtil.e(TAG, "not found service: " + serviceUuid);
            }
        }

        LogUtil.d(TAG, "writeCharacteristic(E):");
    }

    public BluetoothGattCharacteristic[] readCharacteristics(BluetoothDevice device, UUID
            serviceUuid) {
        LogUtil.d(TAG, "readCharacteristics(S): service uuid: " + serviceUuid);
        connectPeripheral(device);

        BluetoothGatt gatt = mConnectedGattMap.get(device);
        ArrayList<BluetoothGattCharacteristic> tempRet = new ArrayList<>();
        BluetoothGattCharacteristic[] ret = null;

        if (gatt != null) {
            BluetoothGattService service = gatt.getService(serviceUuid);
            if (service != null) {
                List<BluetoothGattCharacteristic> tempList = service.getCharacteristics();
                if (tempList != null && tempList.size() > 0) {
                    for (BluetoothGattCharacteristic characteristic : tempList) {
                        if (characteristic.getValue() == null) {
                            if (mCurrentReadingCharacteristic == null) {
                                mCurrentReadingCharacteristic = characteristic;
                                gatt.readCharacteristic(characteristic);
                                LogUtil.d(TAG, "readCharacteristic: UUID: " + characteristic.getUuid());
                            } else if (!mRequestingReadCharacteristicList.contains(characteristic.getUuid())) {
                                mRequestingReadCharacteristicList.add(characteristic);
                            }
                        }

                        tempRet.add(characteristic);
                    }
                }

                for (BluetoothGattService includedService : service.getIncludedServices()) {
                    tempRet.addAll(Arrays.asList(readCharacteristics(gatt, includedService)));
                }
            }

            if (tempRet.size() > 0) {
                for (BluetoothGattCharacteristic characteristic : tempRet) {
                    if (characteristic.getValue() == null) {
                        if (mCurrentReadingCharacteristic == null) {
                            mCurrentReadingCharacteristic = characteristic;
                            gatt.readCharacteristic(characteristic);
                            LogUtil.d(TAG, "readCharacteristic: UUID: " + characteristic.getUuid());
                        } else if (!mRequestingReadCharacteristicList.contains(characteristic.getUuid())) {
                            mRequestingReadCharacteristicList.add(characteristic);
                        }
                    }
                }
            }
        }

        LogUtil.d(TAG, "readCharacteristics(S): service uuid: " + serviceUuid);
        return tempRet.toArray(new BluetoothGattCharacteristic[0]);
    }

    private BluetoothGattCharacteristic[] readCharacteristics(BluetoothGatt
                                                                      gatt, BluetoothGattService service) {
        LogUtil.d(TAG, "readCharacteristics(S): service uuid: " + service.getUuid());
        ArrayList<BluetoothGattCharacteristic> tempRet = new ArrayList<>();
        List<BluetoothGattService> serviceList = service.getIncludedServices();
        BluetoothGattCharacteristic[] ret = null;

        if (serviceList != null && serviceList.size() > 0) {
            for (BluetoothGattService includedService : serviceList) {
                tempRet.addAll(Arrays.asList(readCharacteristics(gatt, includedService)));
            }
        }

        List<BluetoothGattCharacteristic> tempList = service.getCharacteristics();
        if (tempList != null && tempList.size() > 0) {
            for (BluetoothGattCharacteristic characteristic : tempList) {
                if (characteristic.getValue() == null) {
                    if (mCurrentReadingCharacteristic == null) {
                        mCurrentReadingCharacteristic = characteristic;
                        gatt.readCharacteristic(characteristic);
                        LogUtil.d(TAG, "readCharacteristic: UUID: " + characteristic.getUuid());
                    } else if (!mRequestingReadCharacteristicList.contains(characteristic.getUuid())) {
                        mRequestingReadCharacteristicList.add(characteristic);
                    }
                }

                tempRet.add(characteristic);
            }
        }
        ret = tempRet.toArray(new BluetoothGattCharacteristic[0]);

        LogUtil.d(TAG, "readCharacteristics(E): ret length: " + ret.length);
        return ret;
    }

    public void connectPeripheral(BluetoothDevice device) {
        LogUtil.d(TAG, "connectPeripheral(S)");
        synchronized (mConnectedGattMap) {
            BluetoothGatt gatt = mConnectedGattMap.get(device);
            if (gatt == null) {
                gatt = device.connectGatt(mContext, false, mBtGattCallback);
                if (gatt != null) {
                    if (gatt.connect()) {
                        mConnectedGattMap.put(device, gatt);
                    } else {
                        LogUtil.e(TAG, "BluetoothGatt connect failed");
                    }
                }
            }
        }
        LogUtil.d(TAG, "connectPeripheral(E)");
    }

    public void disconnectPeripheral(BluetoothDevice device) {
        LogUtil.d(TAG, "disconnectPeripheral(S)");
        synchronized (mConnectedGattMap) {
            BluetoothGatt gatt = mConnectedGattMap.remove(device);
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        }

        mFoundLeDeviceMap.remove(device);
        mConnectionTimeoutMap.remove(device);
        mDiscoveringServiceDeviceMap.remove(device);

        LogUtil.d(TAG, "disconnectPeripheral(E)");
    }

    public void discoverServices(BluetoothDevice device) {
        LogUtil.d(TAG, "discoverServices(S)");

        if (!mDiscoveringServiceDeviceMap.containsKey(device)) {
            connectPeripheral(device);

            synchronized (mConnectedGattMap) {
                BluetoothGatt gatt = mConnectedGattMap.get(device);
                if (gatt != null) {
                    if (gatt.discoverServices()) {
                        mDiscoveringServiceDeviceMap.put(device, gatt);
                    } else {
                        LogUtil.e(TAG, "discover service failed");
                    }
                }
            }
        }

        LogUtil.d(TAG, "discoverServices(E)");
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

    private DeviceDiscoverer.OnFoundLeDeviceListener mRootFoundLeDeviceListener = new DeviceDiscoverer.OnFoundLeDeviceListener() {
        @Override
        public void onFoundLeDevice(FoundLeDevice device) {
            boolean notifyFoundLeDevice = false;

            if (mDiscoveringServiceDeviceMap.containsKey(device.getDevice())) {
                List<ParcelUuid> serviceUuidList = device.getServiceUuidList();
                notifyFoundLeDevice = (serviceUuidList != null) && (serviceUuidList.size() > 0);

                if (notifyFoundLeDevice) {
                    LogUtil.d(TAG, "discover service on onFoundLeDevice");
                    mDiscoveringServiceDeviceMap.remove(device.getDevice());
                } else {
                    LogUtil.d(TAG, "not found service on onFoundLeDevice");
                }
            } else {
                notifyFoundLeDevice = !mFoundLeDeviceMap.containsKey(device.getDevice());
            }

            if (notifyFoundLeDevice) {
                mFoundLeDeviceMap.put(device.getDevice(), device);
                OnFoundLeDeviceListener listener = mFoundLeDeviceListener;
                if (listener != null) {
                    listener.onFoundLeDevice(device);
                }
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
                LogUtil.d(TAG, "BluetoothGatt.STATE_CONNECTED: discover services: " + gatt.getDevice().getAddress());

                if (mDiscoveringServiceDeviceMap.remove(device) != null) {
                    // サービス検索が空ぶっていたので、再度実行
                    discoverServices(device);
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                LogUtil.d(TAG, "BluetoothGatt.STATE_DISCONNECTED");
                synchronized (mConnectedGattMap) {
                    mConnectionTimeoutMap.remove(device);
                }
                disconnectPeripheral(device);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            BluetoothDevice device = gatt.getDevice();
            long connectionTimeoutMS = System.currentTimeMillis() + mConnectionTimeoutMS;

            LogUtil.d(TAG, "onServicesDiscovered: " + device.getAddress());
            synchronized (mConnectedGattMap) {
                mConnectionTimeoutMap.put(gatt.getDevice(), connectionTimeoutMS);
                mMainLooperHandler.sendMessageDelayed(Message.obtain(mMainLooperHandler, MsgConnectionTimeout, new ConnectionInfo(device, connectionTimeoutMS)), mConnectionTimeoutMS);
            }

            if (mDiscoveringServiceDeviceMap.containsKey(device)) {
                // 見つかったserviceはこのタイミングでしか取得できないことが多い？次のデバイス発見通知で得られるScanResultから取得できなかった模様
                FoundLeDevice foundLeDevice = mFoundLeDeviceMap.remove(device);
                if (foundLeDevice != null) {
                    ArrayList<ParcelUuid> serviceUuidList = new ArrayList<>();
                    List<BluetoothGattService> serviceList = gatt.getServices();

                    for (BluetoothGattService service : serviceList) {
                        serviceUuidList.add(ParcelUuid.fromString(service.getUuid().toString()));
                    }

                    foundLeDevice.setServiceUuidList(serviceUuidList);
                    mRootFoundLeDeviceListener.onFoundLeDevice(foundLeDevice);
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                LogUtil.d(TAG, "onCharacteristicRead: UUID: " + characteristic.getUuid() + ", value: " + Arrays.toString(characteristic.getValue()));

                mCurrentReadingCharacteristic = null;
                if (mRequestingReadCharacteristicList.size() > 0) {
                    while (mRequestingReadCharacteristicList.size() > 0) {
                        mCurrentReadingCharacteristic = mRequestingReadCharacteristicList.remove(0);
                        if (mCurrentReadingCharacteristic.getValue() == null) {
                            LogUtil.d(TAG, "readCharacteristic: UUID: " + mCurrentReadingCharacteristic.getUuid());
                            gatt.readCharacteristic(mCurrentReadingCharacteristic);
                            break;
                        }
                    }
                }

                OnDataReceiveListener listener = mDataReceiveListener;
                if (listener != null) {
                    ReceiveCharacteristicData receiveData = new ReceiveCharacteristicData();
                    receiveData.device = gatt.getDevice();
                    receiveData.uuid = characteristic.getUuid();
                    receiveData.data = characteristic.getValue();
                    receiveData.dataSize = receiveData.data != null ? receiveData.data.length : 0;
                    listener.onDataReceive(receiveData);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            LogUtil.d(TAG, "onCharacteristicWrite(S): UUID: " + characteristic.getUuid() + ", value: " + Arrays.toString(characteristic.getValue()));
            LogUtil.d(TAG, "onCharacteristicWrite(E)");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            LogUtil.d(TAG, "onCharacteristicChanged(S): UUID: " + characteristic.getUuid() + ", value: " + Arrays.toString(characteristic.getValue()));
            LogUtil.d(TAG, "onCharacteristicChanged(E)");
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
