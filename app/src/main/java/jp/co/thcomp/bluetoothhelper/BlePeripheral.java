package jp.co.thcomp.bluetoothhelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    private static final int MaxServiceDataByteSize = 24;
    private static final int MaxManufactureSpecifiedDataByteSize = 24;

    private Context mContext;
    private BluetoothAccessHelper mBtHelper;
    private int mPeripheralState = PeripheralStateInit;
    private OnBluetoothStatusListener mBluetoothStatusListener;
    private AdvertiseSettings.Builder mSettingsBuilder = new AdvertiseSettings.Builder();
    private AdvertiseData.Builder mDataBuilder = new AdvertiseData.Builder();
    private BluetoothGattServer mGattServer;
    private HashMap<BluetoothDevice, BleTransferSettings> mTransferSettingMap = new HashMap<>();
    private HashMap<UUID, byte[]> mCharacteristicMap = new HashMap<>();

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

    public void addRootServiceUuid(ParcelUuid serviceUuid) {
        mDataBuilder.addServiceUuid(serviceUuid);
    }

    public void addRootServiceUuidAndData(ParcelUuid serviceUuid, byte[] serviceData) {
        if (serviceData != null && serviceData.length > MaxServiceDataByteSize) {
            throw new IllegalArgumentException("service data is larger than " + MaxServiceDataByteSize);
        }
        mDataBuilder.addServiceData(serviceUuid, serviceData);
    }

    public void setAdvertiseMode(AdvertiseMode mode) {
        if (mode != null) {
            mSettingsBuilder.setAdvertiseMode(mode.getValue());
        }
    }

    public void setAdvertiseTxPower(AdvertiseTxPower txPower) {
        if (txPower != null) {
            mSettingsBuilder.setTxPowerLevel(txPower.getValue());
        }
    }

    public void setConnectable(boolean connectable) {
        mSettingsBuilder.setConnectable(connectable);
    }

    public void addManufacturerData(int manufacturerId, byte[] data) {
        if (data != null && data.length > MaxManufactureSpecifiedDataByteSize) {
            throw new IllegalArgumentException("manufacturer data is larger than " + MaxManufactureSpecifiedDataByteSize);
        }
        mDataBuilder.addManufacturerData(manufacturerId, data);
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
        // advertiseを開始
        startBleAdvertising();

        BluetoothGattService service = mGattServer.getService(serviceUuid);
        GattServiceType serviceType = GattServiceType.Primary;

        if (service != null) {
            for (GattServiceType tempServiceType : GattServiceType.values()) {
                if (tempServiceType.getValue() == service.getType()) {
                    serviceType = tempServiceType;
                    break;
                }
            }

            // データ更新のため一旦サービスを削除
            mGattServer.removeService(service);
        }
        service = new BluetoothGattService(serviceUuid, serviceType.getValue());

        BleSendDataProvider dataProvider = new BleSendDataProvider(data);
        byte[] buffer = null;
        int minMTU = getMinimumMTU();

        List<BluetoothDevice> connectedDeviceList = ((BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE)).getConnectedDevices(BluetoothProfile.GATT);
        for (int packetIndex = 0; (buffer = dataProvider.getPacket(minMTU, packetIndex)) != null; packetIndex++) {
            //UUID characteristicUuid = UUID.fromString(String.format("636d791c-7f9a-4547-9290-%012X", packetIndex));
            UUID characteristicUuid = UUID.randomUUID();
            BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(characteristicUuid, properties, permissions);
            characteristic.setValue(buffer);
            service.addCharacteristic(characteristic);

            LogUtil.d(TAG, "UUID: " + characteristicUuid + ", buffer: " + Arrays.toString(buffer));
            mCharacteristicMap.put(characteristicUuid, buffer);

            for (BluetoothDevice connectedDevice : connectedDeviceList) {
                mGattServer.notifyCharacteristicChanged(connectedDevice, characteristic, true);
            }
        }

        return mGattServer.addService(service);
    }

    public void removeService(UUID serviceUuid) {
        BluetoothGattService service = mGattServer.getService(serviceUuid);

        if (service != null) {
            mGattServer.removeService(service);
        }
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

    public void startBleAdvertising() {
        if ((mPeripheralState & PeripheralStateStart) != PeripheralStateAdvertiserStart) {
            BluetoothLeAdvertiser advertiser = mBtHelper.getBleAdvertiser();
            if (advertiser != null) {
                AdvertiseSettings settings = mSettingsBuilder.build();
                AdvertiseData data = mDataBuilder.build();
                advertiser.startAdvertising(settings, data, mAdvertiseCallback);
                mPeripheralState |= PeripheralStateAdvertiserStart;
            }
        }
    }

    public void stopBleAdvertising() {
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

                        //test // advertiseを開始
                        //startBleAdvertising();
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

            //test // GATTサーバオープン
            //openGattServer();

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
            LogUtil.d(TAG, "onConnectionStateChange");

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
            LogUtil.d(TAG, "onServiceAdded");
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            LogUtil.d(TAG, "onCharacteristicReadRequest: " + characteristic.getUuid() + "requestId: " + requestId + ", offset: " + offset);

            byte[] buffer = mCharacteristicMap.get(characteristic.getUuid());
            if (buffer != null) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, buffer);
                mGattServer.notifyCharacteristicChanged(device, characteristic, true);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            LogUtil.d(TAG, "onCharacteristicWriteRequest");
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            LogUtil.d(TAG, "onDescriptorReadRequest");
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            LogUtil.d(TAG, "onDescriptorWriteRequest");
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            LogUtil.d(TAG, "onExecuteWrite");
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            LogUtil.d(TAG, "onNotificationSent");
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            LogUtil.d(TAG, "onMtuChanged");

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
