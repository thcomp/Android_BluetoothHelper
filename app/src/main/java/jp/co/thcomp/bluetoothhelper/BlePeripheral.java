package jp.co.thcomp.bluetoothhelper;

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BlePeripheral {
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

    private static final int PeripheralStateInit = 0;
    private static final int PeripheralStateStart = 1;
    private static final int PeripheralStateAdvertiserStart = 2;

    private Context mContext;
    private BluetoothAccessHelper mBtHelper;
    private int mPeripheralState = PeripheralStateInit;
    private OnBluetoothStatusListener mBluetoothStatusListener;
    private AdvertiseMode mAdvertiseMode = AdvertiseMode.Balanced;
    private boolean mConnectable = false;
    private AdvertiseTxPower mAdvertiseTxPower = AdvertiseTxPower.Medium;

    public BlePeripheral(Context context) {
        mContext = context;
        mBtHelper = new BluetoothAccessHelper(mContext);
        mBtHelper.setOnBluetoothStatusListener(getBluetoothStatusListener());
    }

    public void setOnBluetoothStatusListener(OnBluetoothStatusListener listener) {
        mBluetoothStatusListener = listener;
    }

    public void setAdvertiseMode(AdvertiseMode mode) {
        if (mode != null) {
            mAdvertiseMode = mode;
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
            mBtHelper.stopBluetoothHelper();
            mPeripheralState &= (~PeripheralStateStart);
        }
    }

    public boolean send() {
        boolean ret = false;
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
                advertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), mAdvertiseCallback);
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
            super.onStartSuccess(settingsInEffect);
            OnBluetoothStatusListener listener = mBluetoothStatusListener;
            if (listener != null) {
                listener.onStatusChange(StatusStartBleAdvertising, BluetoothAccessHelper.sScanMode);
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            OnBluetoothStatusListener listener = mBluetoothStatusListener;
            if (listener != null) {
                listener.onStatusChange(StatusDisableBleAdvertising, BluetoothAccessHelper.sScanMode);
            }
        }
    };

    public interface OnBluetoothStatusListener extends BluetoothAccessHelper.OnBluetoothStatusListener {
        // 拡張なし
    }
}
