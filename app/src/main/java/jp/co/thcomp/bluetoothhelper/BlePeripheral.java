package jp.co.thcomp.bluetoothhelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.HashMap;
import java.util.UUID;

import jp.co.thcomp.util.LogUtil;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BlePeripheral {
    private static final String TAG = BlePeripheral.class.getSimpleName();
    public static final int StatusDisableBluetooth = BluetoothAccessHelper.StatusDisableBluetooth;
    public static final int StatusNoSupportBluetooth = BluetoothAccessHelper.StatusNoSupportBluetooth;
    public static final int StatusInit = BluetoothAccessHelper.StatusInit;
    public static final int StatusStartBleAdvertising = BluetoothAccessHelper.StatusFirstExtension + 1;
    public static final int StatusDisableBleAdvertising = BluetoothAccessHelper.StatusFirstExtension + 2;

    public enum AdvertiseMode {
        Balanced(AdvertiseSettings.ADVERTISE_MODE_BALANCED),
        HighPower(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY),
        LowPower(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);

        private int mMode;

        AdvertiseMode(int mode) {
            mMode = mode;
        }

        int getValue() {
            return mMode;
        }
    }

    public enum AdvertiseTxPower {
        High(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH),
        Medium(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM),
        Low(AdvertiseSettings.ADVERTISE_TX_POWER_LOW),
        UltraLow(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW);

        private int mTxPower;

        AdvertiseTxPower(int txPower) {
            mTxPower = txPower;
        }

        int getValue() {
            return mTxPower;
        }
    }

    public enum GattServiceType {
        Primary(BluetoothGattService.SERVICE_TYPE_PRIMARY),
        Secondary(BluetoothGattService.SERVICE_TYPE_SECONDARY);

        private int mServiceType;

        GattServiceType(int serviceType) {
            mServiceType = serviceType;
        }

        int getValue() {
            return mServiceType;
        }
    }

    private static final int PeripheralStateInit = 0;
    private static final int PeripheralStateStart = 1;
    private static final int PeripheralStateAdvertiserStart = 2;
    private static final int PeripheralStateOpenGattServer = 4;

    private Context mContext;
    private BluetoothAccessHelper mBtHelper;
    private int mPeripheralState = PeripheralStateInit;
    private OnBluetoothStatusListener mBluetoothStatusListener;
    private AdvertiseMode mAdvertiseMode = AdvertiseMode.Balanced;
    private boolean mConnectable = false;
    private ParcelUuid mRootServiceUuid;
    private AdvertiseTxPower mAdvertiseTxPower = AdvertiseTxPower.Medium;
    private BluetoothGattServer mGattServer;
    private HashMap<BluetoothDevice, BleTransferSettings> mTransferSettingMap = new HashMap<>();

    public BlePeripheral(Context context) {
        mContext = context;
        mBtHelper = new BluetoothAccessHelper(mContext);
        mBtHelper.setOnBluetoothStatusListener(getBluetoothStatusListener());
    }

    public void setOnBluetoothStatusListener(OnBluetoothStatusListener listener) {
        mBluetoothStatusListener = listener;
    }

    public void addService(UUID serviceUuid, GattServiceType serviceType) {
        if (mGattServer != null) {
            BluetoothGattService service = new BluetoothGattService(serviceUuid, serviceType.getValue());
            mGattServer.addService(service);
        }
    }

    public void setRootServiceUuid(ParcelUuid serviceUuid) {
        mRootServiceUuid = serviceUuid;
    }

    public void setAdvertiseMode(AdvertiseMode mode) {
        if (mode != null) {
            mAdvertiseMode = mode;
        }
    }

    public void setAdvertiseTxPower(AdvertiseTxPower txPower) {
        if (txPower != null) {
            mAdvertiseTxPower = txPower;
        }
    }

    public void setConnectable(boolean connectable) {
        mConnectable = connectable;
    }

    public void start() {
        if (mPeripheralState == PeripheralStateInit) {
            mPeripheralState = PeripheralStateStart;
            mBtHelper.startBluetoothHelper();
        }
    }

    public void stop() {
        if ((mPeripheralState & PeripheralStateStart) == PeripheralStateStart) {
            stopBleAdvertising();
            closeGattServer();
            mBtHelper.stopBluetoothHelper();
            mPeripheralState &= (~PeripheralStateStart);
        }
    }

    public boolean setDeviceName(String deviceName) {
        return mBtHelper.setDeviceName(deviceName);
    }

    public boolean restoreDeviceName() {
        return mBtHelper.restoreDeviceName();
    }

    public boolean setCharacteristic(UUID serviceUuid, int properties, int permissions, byte[] data) {
        BluetoothGattService service = mGattServer.getService(serviceUuid);

        if (service != null) {
            // データ更新のため一旦サービスを削除
            mGattServer.removeService(service);
        }
        service = new BluetoothGattService(serviceUuid, GattServiceType.Primary.getValue());

        BleSendDataProvider dataProvider = new BleSendDataProvider(data);
        byte[] buffer = null;
        int minMTU = getMinimumMTU();

        for (int packetIndex = 0; (buffer = dataProvider.getPacket(minMTU, packetIndex)) != null; packetIndex++) {
            BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.randomUUID(), properties, permissions);
            characteristic.setValue(buffer);
            service.addCharacteristic(characteristic);
        }

        return mGattServer.addService(service);
    }

    private int getMinimumMTU() {
        int ret = BleTransferSettings.DefaultMTU;

        if (mTransferSettingMap.size() > 0) {
            // 調査用に一旦最大値に上げる
            ret = Integer.MAX_VALUE;
            int tempMTU = 0;

            for (BleTransferSettings settings : mTransferSettingMap.values().toArray(new BleTransferSettings[0])) {
                tempMTU = settings.getMTU();
                if (ret > tempMTU) {
                    ret = tempMTU;
                }
            }
        }

        return ret;
    }

    private void startBleAdvertising() {
        if ((mPeripheralState & PeripheralStateStart) != PeripheralStateAdvertiserStart) {
            BluetoothLeAdvertiser advertiser = mBtHelper.getBleAdvertiser();
            if (advertiser != null) {
                AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
                settingsBuilder.setAdvertiseMode(mAdvertiseMode.getValue());
                settingsBuilder.setConnectable(mConnectable);
                settingsBuilder.setTxPowerLevel(mAdvertiseTxPower.getValue());

                AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
                if (mRootServiceUuid != null) {
                    // UUIDが設定されているときは名前を設定するとデータ超過となるので、名前設定は行わない
                    dataBuilder.setIncludeDeviceName(false);
                    dataBuilder.addServiceUuid(mRootServiceUuid);
                    dataBuilder.addServiceData(mRootServiceUuid, "data".getBytes());
                } else {
                    // UUIDが設定されていないときは名前を含める
                    dataBuilder.setIncludeDeviceName(true);
                }

                AdvertiseSettings settings = settingsBuilder.build();
                AdvertiseData data = dataBuilder.build();
                advertiser.startAdvertising(settings, data, mAdvertiseCallback);
                mPeripheralState |= PeripheralStateAdvertiserStart;
            }
        }
    }

    private void stopBleAdvertising() {
        if ((mPeripheralState & PeripheralStateAdvertiserStart) == PeripheralStateAdvertiserStart) {
            BluetoothLeAdvertiser advertiser = mBtHelper.getBleAdvertiser();
            if (advertiser != null) {
                advertiser.stopAdvertising(mAdvertiseCallback);
            }

            mPeripheralState &= (~PeripheralStateAdvertiserStart);
        }
    }

    private void openGattServer() {
        if ((mPeripheralState & PeripheralStateOpenGattServer) != PeripheralStateOpenGattServer) {
            BluetoothManager btManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            mGattServer = btManager.openGattServer(mContext, mBtGattServerCallback);
            if (mGattServer != null) {
                mPeripheralState |= PeripheralStateOpenGattServer;
            }
        }
    }

    private void closeGattServer() {
        if ((mPeripheralState & PeripheralStateOpenGattServer) == PeripheralStateOpenGattServer) {
            mGattServer.clearServices();
            mGattServer.close();
            mGattServer = null;

            mPeripheralState &= (~PeripheralStateOpenGattServer);
        }
    }

    private BluetoothAccessHelper.OnBluetoothStatusListener getBluetoothStatusListener() {
        return new BluetoothAccessHelper.OnBluetoothStatusListener() {
            @Override
            public void onStatusChange(int status, int scanMode) {
                OnBluetoothStatusListener listener = mBluetoothStatusListener;

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
                    case BluetoothAccessHelper.StatusStartBluetooth:
                        if (listener != null) {
                            listener.onStatusChange(status, scanMode);
                        }

                        // GATTサーバオープン
                        openGattServer();

                        // advertiseを開始
                        startBleAdvertising();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            LogUtil.i(TAG, "success to start advertise");
            super.onStartSuccess(settingsInEffect);
            OnBluetoothStatusListener listener = mBluetoothStatusListener;

            // GATTサーバオープン
            openGattServer();

            if (listener != null) {
                listener.onStatusChange(StatusStartBleAdvertising, BluetoothAccessHelper.sScanMode);
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            LogUtil.i(TAG, "fail to start advertise: " + errorCode);
            super.onStartFailure(errorCode);
            OnBluetoothStatusListener listener = mBluetoothStatusListener;

            stopBleAdvertising();
            if (listener != null) {
                listener.onStatusChange(StatusDisableBleAdvertising, BluetoothAccessHelper.sScanMode);
            }
        }
    };

    private BluetoothGattServerCallback mBtGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    synchronized (mTransferSettingMap) {
                        if (!mTransferSettingMap.containsKey(device)) {
                            mTransferSettingMap.put(device, new BleTransferSettings(device));
                        }
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    synchronized (mTransferSettingMap) {
                        mTransferSettingMap.remove(device);
                    }
                    break;
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);

            synchronized (mTransferSettingMap) {
                BleTransferSettings setting = mTransferSettingMap.get(device);
                if (setting != null) {
                    setting.setMTU(mtu);
                }
            }
        }
    };

    public interface OnBluetoothStatusListener extends BluetoothAccessHelper.OnBluetoothStatusListener {
        // 拡張なし
    }
}
