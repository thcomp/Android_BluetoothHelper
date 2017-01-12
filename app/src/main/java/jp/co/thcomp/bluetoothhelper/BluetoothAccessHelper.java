package jp.co.thcomp.bluetoothhelper;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import jp.co.thcomp.activity.HandleResultActivity;
import jp.co.thcomp.util.LogUtil;
import jp.co.thcomp.util.SimplexHashMultimap;

public class BluetoothAccessHelper {
    public static final int StatusNoSupportBluetooth = -1;
    public static final int StatusInit = 0;
    public static final int StatusProgress = 1;
    public static final int StatusStartBluetooth = 10;

    public static final int ServerInit = 0;
    public static final int ServerAccept = 1;
    public static final int ServerReceiveData = 2;
    public static final int ServerClosed = 3;

    public static final int SendSuccess = 0;
    public static final int SendFailByBondError = -1;
    public static final int SendFailByConnectError = -2;
    public static final int SendFailByOutputError = -3;
    public static final int SendFailByUnknownError = -4;

    private static BluetoothAdapter sAdapter;
    private static SimplexHashMultimap<String, BluetoothDevice> sFoundDeviceMap = new SimplexHashMultimap<String, BluetoothDevice>();
    private static ArrayList<BluetoothAccessHelper> sAccessHelperList = new ArrayList<BluetoothAccessHelper>();
    private static boolean sEnableAutoStartBluetooth = false;
    private static int sScanMode = BluetoothAdapter.SCAN_MODE_NONE;
    private static int sBluetoothStatus = StatusInit;
    private static long sStopDiscoverTimeMS = 0;

    private static final String TAG = "BluetoothAccessHelper";
    private static final String LaunchBluetooth = "LaunchBluetooth";
    private static final int LaunchBluetoothInt = LaunchBluetooth.hashCode() & 0x0000FFFF;
    private static final int StopDiscover = "StopDiscover".hashCode();

    public static interface OnBluetoothStatusListener {
        public void onStatusChange(int status, int scanMode);
    }

    public static interface OnDeviceStatusListener {
        public void onFoundDevice(BluetoothDevice foundDevice);
        public void onPairedDevice(BluetoothDevice foundDevice);
    }

    public static interface OnServerReceiveListener {
        public void onStatusChange(int serverStatus);
    }

    public static interface OnNotifyResultListener {
        public void onSendDataResult(int result, BluetoothDevice device, byte[] data, int offset, int kength);
    }

    private Context mContext;
    private String mApplicationName;
    private Handler mMainLooperHandler;
    private OnBluetoothStatusListener mStatusListener;
    private OnNotifyResultListener mNotifyResultListener;
    private BluetoothServerSocket mServerSocket = null;
    private ConcurrentLinkedQueue mSendDataQueue = new ConcurrentLinkedQueue();
    private HashMap<BluetoothDevice, DataBox> mBondingDeviceMap = new HashMap<BluetoothDevice, DataBox>();
    private boolean mStartHelper = false;
    private Thread mSendDataThread;

    public BluetoothAccessHelper(Context context, String applicationName) {
        if (context == null || applicationName == null || applicationName.length() == 0) {
            throw new NullPointerException();
        }
        mContext = context;
        mApplicationName = applicationName;
        mMainLooperHandler = new Handler(context.getMainLooper(), mMessageCallback);
    }

    public void setOnBluetoothStatusListener(OnBluetoothStatusListener listener) {
        mStatusListener = listener;
    }

    public void setOnNotifyResultListener(OnNotifyResultListener listener) {
        mNotifyResultListener = listener;
    }

    public void enableAutoStartBluetooth(boolean enable) {
        if (sEnableAutoStartBluetooth != enable) {
            sEnableAutoStartBluetooth = enable;
            if (enable && sBluetoothStatus == StatusInit) {
                enableBluetooth(null);
            }
        }
    }

    public synchronized void startBluetooth() {
        if (sAdapter == null) {
            sAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (sAdapter == null) {
            // device is not support bluetooth
            if (mStatusListener != null) {
                changeStatus(mStatusListener, StatusNoSupportBluetooth, null);
            }
        } else {
            mStartHelper = true;
            sAccessHelperList.add(this);
            if (sAdapter.isEnabled()) {
                if (mStatusListener != null) {
                    changeStatus(mStatusListener, StatusStartBluetooth, null);
                }
                startListening();
            } else {
                enableBluetooth(null);
            }
        }
    }

    public void stopBluetooth() {
        mStartHelper = false;
        sAccessHelperList.remove(this);
        if (sAccessHelperList.size() == 0) {
            stopListening();
        }
    }

    public void requestDiscoverable() {
        if (sScanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            mContext.startActivity(intent);
        }
    }

    public Set<BluetoothDevice> getPairedDevices() {
        Set<BluetoothDevice> retSet = null;

        if (sAdapter != null) {
            retSet = sAdapter.getBondedDevices();
        }

        return retSet;
    }

    public boolean discoverDevices(long durationMS) {
        boolean ret = false;

        if (sAdapter != null) {
            ret = sAdapter.startDiscovery();
            long stopDiscoverTimeMS = System.currentTimeMillis() + durationMS;
            if (sStopDiscoverTimeMS < stopDiscoverTimeMS) {
                sStopDiscoverTimeMS = stopDiscoverTimeMS;
                mMainLooperHandler.removeMessages(StopDiscover);
                mMainLooperHandler.sendEmptyMessageDelayed(StopDiscover, durationMS);
            }
        }

        return ret;
    }

    public boolean startServer() {
        if (mServerSocket == null) {
            new Thread(mServerSocketRunnable).start();
        }
        return true;
    }

    public boolean sendData(BluetoothDevice device, byte[] data) {
        return sendData(device, data, 0, data.length);
    }

    public boolean sendData(BluetoothDevice device, byte[] data, int offset, int length) {
        boolean ret = false;

        if (mStartHelper && device != null && data != null) {
            synchronized (mSendDataQueue) {
                mSendDataQueue.add(new DataBox(device, data, offset, length));
                mSendDataQueue.notify();

                if (mSendDataThread == null) {
                    mSendDataThread = new Thread(mClientRunnable);
                    mSendDataThread.start();
                }
            }
            ret = true;
        }

        return ret;
    }

    private boolean createBond(BluetoothDevice device) {
        boolean ret = false;

        if (device != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ret = device.createBond();
            } else {
                try {
                    Class class1 = Class.forName("android.bluetooth.BluetoothDevice");
                    Method createBondMethod = class1.getMethod("createBond");
                    ret = (Boolean) createBondMethod.invoke(device);
                } catch (ClassNotFoundException e) {
                    LogUtil.e(TAG, e.getLocalizedMessage());
                } catch (InvocationTargetException e) {
                    LogUtil.e(TAG, e.getLocalizedMessage());
                } catch (NoSuchMethodException e) {
                    LogUtil.e(TAG, e.getLocalizedMessage());
                } catch (IllegalAccessException e) {
                    LogUtil.e(TAG, e.getLocalizedMessage());
                }
            }
        }

        return ret;
    }

    private void startListening() {
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver, new IntentFilter(LaunchBluetooth));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    private void stopListening() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
        mContext.unregisterReceiver(mReceiver);
        if (sAdapter != null) {
            sAdapter.cancelDiscovery();
        }
    }

    private void changeStatus(final OnBluetoothStatusListener targetListener, final Integer status, final Integer scanMode) {
        if (mContext.getMainLooper().getThread().equals(Thread.currentThread())) {
            if (status != null) {
                sBluetoothStatus = status;
            }
            if (scanMode != null) {
                sScanMode = scanMode;
            }

            if (targetListener != null) {
                targetListener.onStatusChange(sBluetoothStatus, sScanMode);
            } else {
                BluetoothAccessHelper[] accessHelperArray = sAccessHelperList.toArray(new BluetoothAccessHelper[0]);
                for (BluetoothAccessHelper accessHelper : accessHelperArray) {
                    if (accessHelper.mStatusListener != null) {
                        accessHelper.mStatusListener.onStatusChange(sBluetoothStatus, sScanMode);
                    }
                }
            }
        } else {
            mMainLooperHandler.post(new Runnable() {
                @Override
                public void run() {
                    changeStatus(targetListener, status, scanMode);
                }
            });
        }
    }

    private void changeStatus(final Integer status, final Integer scanMode) {
        changeStatus(null, status, scanMode);
    }

    private void enableBluetooth(OnBluetoothStatusListener listener) {
        if (sAdapter != null && !sAdapter.isEnabled()) {
            if (sEnableAutoStartBluetooth) {
                if (sAdapter.enable()) {
                    // notify change to all listener for start bluetooth
                    changeStatus(StatusStartBluetooth, null);
                }
            } else {
                if (sBluetoothStatus == StatusInit) {
                    enableBluetoothWithConfirmation();
                    changeStatus(StatusProgress, null);
                } else if (sBluetoothStatus == StatusProgress) {
                    if (listener != null) {
                        listener.onStatusChange(sBluetoothStatus, sScanMode);
                    }
                }
            }
        }
    }

    private void enableBluetoothWithConfirmation() {
        Intent launchIntent = new Intent();
        launchIntent.setClass(mContext, HandleResultActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent transferIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        launchIntent.putExtra(HandleResultActivity.INTENT_PARCELABLE_EXTRA_TRANSFER_INTENT, transferIntent);
        launchIntent.putExtra(HandleResultActivity.INTENT_INT_EXTRA_HOWTO_CALLBACK, HandleResultActivity.CALLBACK_BY_LOCAL_BROADCAST);
        launchIntent.putExtra(HandleResultActivity.INTENT_STRING_EXTRA_CALLBACK_ACTION, LaunchBluetooth);
        launchIntent.putExtra(HandleResultActivity.INTENT_INT_EXTRA_REQUEST_CODE, LaunchBluetoothInt);

        mContext.startActivity(launchIntent);
    }

    private void notifySendDataError(int result, DataBox dataBox) {
        notifySendDataError(result, dataBox.mDevice, dataBox.mData, dataBox.mOffset, dataBox.mLength);
    }

    private void notifySendDataError(final int result, final BluetoothDevice device, final byte[] data, final int offset, final int length) {
        final OnNotifyResultListener fListener = mNotifyResultListener;
        if (fListener != null) {
            if (Thread.currentThread().equals(mContext.getMainLooper().getThread())) {
                fListener.onSendDataResult(result, device, data, offset, length);
            } else {
                mMainLooperHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifySendDataError(result, device, data, offset, length);
                    }
                });
            }
        }
    }

    private static class DataBox {
        public BluetoothDevice mDevice;
        public byte[] mData;
        public int mOffset = 0;
        public int mLength = 0;

        public DataBox(BluetoothDevice device, byte[] data, int offset, int length) {
            if (device == null || data == null) {
                throw new NullPointerException();
            }

            mDevice = device;
            mData = data;
            mOffset = offset;
            mLength = length;
        }
    }

    private Handler.Callback mMessageCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            boolean ret = false;

            if (msg.what == StopDiscover) {
                if (sAdapter != null) {
                    sAdapter.cancelDiscovery();
                }
                ret = true;
            }

            return ret;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (LaunchBluetooth.equals(action)) {
                Intent retIntent = intent.getParcelableExtra(HandleResultActivity.INTENT_PARCELABLE_EXTRA_TRANSFER_INTENT);

                if (retIntent != null) {
                    int ret = retIntent.getIntExtra(HandleResultActivity.INTENT_INT_EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
                    if (ret == Activity.RESULT_OK) {
                        changeStatus(StatusStartBluetooth, null);
                        startListening();
                    }
                }
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                sScanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_NONE);
                changeStatus(null, sScanMode);
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                sFoundDeviceMap.add(device.getName(), device);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                DataBox databox = null;
                synchronized (mBondingDeviceMap) {
                    databox = mBondingDeviceMap.remove(device);
                }

                if (databox != null) {
                    switch (device.getBondState()) {
                        case BluetoothDevice.BOND_BONDED:
                            // put it to sender queue
                            synchronized (mSendDataQueue) {
                                mSendDataQueue.add(databox);
                                mSendDataQueue.notify();
                            }
                            break;
                        default:
                            // give up to send data and notify error
                            notifySendDataError(SendFailByBondError, databox);
                            break;
                    }

                }
            }
        }
    };

    private Runnable mClientRunnable = new Runnable() {
        @Override
        public void run() {
            DataBox dataBox = null;

            while (mStartHelper) {
                dataBox = (DataBox) mSendDataQueue.poll();
                if (dataBox != null) {
                    // send data
                    BluetoothSocket clientSocket = null;
                    try {
                        clientSocket = dataBox.mDevice.createRfcommSocketToServiceRecord(UUID.nameUUIDFromBytes(mApplicationName.getBytes()));
                        if (clientSocket != null) {
                            clientSocket.connect();
                            try {
                                clientSocket.getOutputStream().write(dataBox.mData, dataBox.mOffset, dataBox.mLength);
                            } catch (IOException e) {
                                LogUtil.e(TAG, e.getLocalizedMessage());
                                notifySendDataError(SendFailByOutputError, dataBox);
                            }
                        }
                    } catch (IOException e) {
                        LogUtil.e(TAG, e.getLocalizedMessage());
                        notifySendDataError(SendFailByConnectError, dataBox);
                    } finally {
                        if (clientSocket != null) {
                            try {
                                clientSocket.close();
                            } catch (IOException e1) {
                            }
                        }
                    }
                }

                synchronized (mSendDataQueue) {
                    if (mSendDataQueue.size() == 0) {
                        try {
                            mSendDataQueue.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }

            mSendDataThread = null;
        }
    };

    private Runnable mServerSocketRunnable = new Runnable() {
        @Override
        public void run() {
            BluetoothServerSocket serverSocket = null;

            synchronized (mServerSocketRunnable) {
                if (mServerSocket == null) {
                    try {
                        serverSocket = mServerSocket = sAdapter.listenUsingRfcommWithServiceRecord(mApplicationName, UUID.nameUUIDFromBytes(mApplicationName.getBytes()));
                    } catch (IOException e) {
                        LogUtil.e(TAG, e.getLocalizedMessage());
                    }

                } else {
                    // already run other thread for server
                }
            }

            if (serverSocket != null) {
                while (true) {
                    BluetoothSocket recvSocket = null;
                    try {
                        recvSocket = serverSocket.accept();
                    } catch (IOException e) {
                        mServerSocket = null;
                        break;
                    }

                    if (recvSocket != null) {

                    }
                }
            }
        }
    };
}
