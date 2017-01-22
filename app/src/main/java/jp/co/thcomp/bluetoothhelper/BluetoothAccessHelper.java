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
import java.io.InputStream;
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
import jp.co.thcomp.util.ThreadUtil;

public class BluetoothAccessHelper {
    public static final int StatusNoSupportBluetooth = -1;
    public static final int StatusInit = 0;
    public static final int StatusProgress = 1;
    public static final int StatusStartBluetooth = 10;

    public static final UUID BT_SDP = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_RFCOMM = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_OBEX = UUID.fromString("00000008-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_HTTP = UUID.fromString("0000000C-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_L2CAP = UUID.fromString("00000100-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_SERVICE_DISCOVERY_SERVER_SERVICE_CLASS_ID = UUID.fromString("00001000-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_BROWSER_GROUP_DESCRIPTOR_SERVICE_CLASS_ID = UUID.fromString("00001001-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_PUBLIC_BROWSE_GROUP = UUID.fromString("00001002-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_OBEX_OBJECT_PUSH = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_OBEX_FILE_TRANSFER = UUID.fromString("00001106-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_PERSONAL_AREA_NETWORK_USER = UUID.fromString("00001115-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_NETWORK_ACCESS_POINT = UUID.fromString("00001116-0000-1000-8000-00805F9B34FB");
    public static final UUID BT_GROUP_NETWORK = UUID.fromString("00001117-0000-1000-8000-00805F9B34FB");

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
    private static final SimplexHashMultimap<String, BluetoothDevice> sFoundDeviceMap = new SimplexHashMultimap<String, BluetoothDevice>();
    private static final ArrayList<BluetoothAccessHelper> sAccessHelperList = new ArrayList<BluetoothAccessHelper>();
    private static boolean sEnableAutoStartBluetooth = false;
    private static int sScanMode = BluetoothAdapter.SCAN_MODE_NONE;
    private static int sBluetoothStatus = StatusInit;
    private static long sStopDiscoverTimeMS = 0;

    private static final String TAG = "BluetoothAccessHelper";
    private static final String LaunchBluetooth = "LaunchBluetooth";
    private static final int LaunchBluetoothInt = LaunchBluetooth.hashCode() & 0x0000FFFF;
    private static final int StopDiscover = "StopDiscover".hashCode();
    private static final int MaxConnectionRetryCount = 3;
    private static final int ConnectionRetryIntervalMS = 1000;

    public interface OnBluetoothStatusListener {
        void onStatusChange(int status, int scanMode);
    }

    public interface OnDeviceStatusListener {
        void onFoundDevice(BluetoothDevice foundDevice);

        void onPairedDevice(BluetoothDevice foundDevice);
    }

    public interface OnServerReceiveListener {
        void onStatusChange(int serverStatus);
    }

    public interface OnNotifyResultListener {
        void onSendDataResult(int result, BluetoothDevice device, byte[] data, int offset, int length);
    }

    private static void addBluetoothAccessHelper(BluetoothAccessHelper accessHelper) {
        if (accessHelper != null) {
            Context context = accessHelper.mContext;

            synchronized (sAccessHelperList) {
                if (sAccessHelperList.size() == 0) {
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                    intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
                    intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    context.registerReceiver(sBroadcastReceiver, intentFilter);
                }

                sAccessHelperList.add(accessHelper);
            }
        }
    }

    private static void removeBluetoothAccessHelper(BluetoothAccessHelper accessHelper) {
        if (accessHelper != null) {
            Context context = accessHelper.mContext;

            synchronized (sAccessHelperList) {
                sAccessHelperList.remove(accessHelper);

                if (sAccessHelperList.size() == 0) {
                    try {
                        context.unregisterReceiver(sBroadcastReceiver);
                    } catch (IllegalArgumentException e) {
                        // 処理なし
                    }
                    if (sAdapter != null) {
                        sAdapter.cancelDiscovery();
                    }
                }
            }
        }
    }

    private static BroadcastReceiver sBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                sScanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_NONE);
                ThreadUtil.runOnMainThread(context, new Runnable() {
                            @Override
                            public void run() {
                                for (BluetoothAccessHelper accessHelper : sAccessHelperList) {
                                    accessHelper.changeStatus(null, sScanMode);
                                }
                            }
                        }
                );
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                sFoundDeviceMap.add(device.getName(), device);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                ThreadUtil.runOnMainThread(context, new Runnable() {
                    @Override
                    public void run() {
                        DataBox databox = null;

                        for (BluetoothAccessHelper accessHelper : sAccessHelperList) {
                            synchronized (accessHelper.mBondingDeviceMap) {
                                databox = accessHelper.mBondingDeviceMap.remove(device);
                            }

                            if (databox != null) {
                                switch (device.getBondState()) {
                                    case BluetoothDevice.BOND_BONDED:
                                        // put it to sender queue
                                        synchronized (accessHelper.mSendDataQueue) {
                                            accessHelper.mSendDataQueue.add(databox);
                                            accessHelper.mSendDataQueue.notify();
                                        }
                                        break;
                                    default:
                                        // give up to send data and notify error
                                        accessHelper.notifySendDataResult(SendFailByBondError, databox);
                                        break;
                                }

                            }
                        }
                    }
                });
            }
        }
    };

    private Context mContext;
    private String mApplicationName;
    private UUID mServerUuid = null;
    private Handler mMainLooperHandler;
    private OnBluetoothStatusListener mStatusListener;
    private OnNotifyResultListener mNotifyResultListener;
    private BluetoothServerSocket mServerSocket = null;
    private final ConcurrentLinkedQueue mSendDataQueue = new ConcurrentLinkedQueue();
    private final HashMap<BluetoothDevice, DataBox> mBondingDeviceMap = new HashMap<>();
    private boolean mStartHelper = false;
    private Thread mSendDataThread;
    private final HashMap<BluetoothDevice, HashMap<UUID, BluetoothSocket>> mConnectedSocketMap = new HashMap<>();

    public BluetoothAccessHelper(Context context, String applicationName) {
        this(context, applicationName, null);
    }

    public BluetoothAccessHelper(Context context, String applicationName, String uuidText) {
        if (context == null || applicationName == null || applicationName.length() == 0) {
            throw new NullPointerException("context == null || applicationName == null || applicationName.length() == 0");
        }
        mContext = context;

        mApplicationName = applicationName;
        if (uuidText != null) {
            mServerUuid = UUID.fromString(uuidText);
        }
        mMainLooperHandler = new Handler(context.getMainLooper(), mMessageCallback);
    }

    public void setServerUuid(String uuidText) {
        if (uuidText == null) {
            throw new NullPointerException("uuidText == null");
        }

        mServerUuid = UUID.fromString(uuidText);
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

    public synchronized void startBluetoothHelper() {
        if (sAdapter == null) {
            sAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (sAdapter == null) {
            // device is not support bluetooth
            if (mStatusListener != null) {
                changeStatus(mStatusListener, StatusNoSupportBluetooth, null);
            }
        } else {
            if (!mStartHelper) {
                mStartHelper = true;
                LocalBroadcastManager.getInstance(mContext).registerReceiver(mLocalBroadcastReceiver, new IntentFilter(LaunchBluetooth));

                if (sAdapter.isEnabled()) {
                    addBluetoothAccessHelper(this);
                    if (mStatusListener != null) {
                        changeStatus(mStatusListener, StatusStartBluetooth, null);
                    }
                } else {
                    enableBluetooth(null);
                }
            }
        }
    }

    public synchronized boolean isEnableBluetooth() {
        if (sAdapter == null) {
            sAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        return sAdapter != null && sAdapter.isEnabled();
    }

    public void stopBluetoothHelper() {
        if (mStartHelper) {
            mStartHelper = false;
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mLocalBroadcastReceiver);
            removeBluetoothAccessHelper(this);

            disconnectAll();
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
        boolean ret = false;

        if (mStartHelper && (mServerUuid != null)) {
            if (mServerSocket == null) {
                new Thread(mServerSocketRunnable).start();
            }
            ret = true;
        }

        return ret;
    }

    public boolean stopServer() {
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                LogUtil.e(TAG, e.getLocalizedMessage());
            } finally {
                mServerSocket = null;
            }
        }
        return true;
    }

    public boolean connect(BluetoothDevice device, UUID targetUuid) {
        boolean ret = false;
        DataBox dataBox = new DataBox(targetUuid, device, new byte[0], 0, 0);
        BluetoothSocket clientSocket = getClientSocket(dataBox);

        return clientSocket != null;
    }

    public boolean isConnected(BluetoothDevice device, UUID targetUuid) {
        boolean ret = false;
        HashMap<UUID, BluetoothSocket> connectedSocketMap = null;

        synchronized (mConnectedSocketMap) {
            connectedSocketMap = mConnectedSocketMap.get(device);
        }

        if (connectedSocketMap != null) {
            synchronized (connectedSocketMap) {
                ret = (connectedSocketMap.get(targetUuid) != null);
            }
        }

        return ret;
    }

    public boolean disconnect(BluetoothDevice device, UUID targetUuid) {
        boolean ret = false;
        HashMap<UUID, BluetoothSocket> connectedSocketMap = null;

        synchronized (mConnectedSocketMap) {
            connectedSocketMap = mConnectedSocketMap.get(device);
        }

        if (connectedSocketMap != null) {
            synchronized (connectedSocketMap) {
                BluetoothSocket connectedSocket = connectedSocketMap.remove(targetUuid);
                if (connectedSocket != null) {
                    try {
                        connectedSocket.close();
                    } catch (Exception e) {
                    }
                }

                ret = true;
            }
        }

        return ret;
    }

    public void disconnectAll() {
        synchronized (mConnectedSocketMap) {
            for (HashMap<UUID, BluetoothSocket> socketMap : mConnectedSocketMap.values()) {
                for (BluetoothSocket connectedSocket : socketMap.values()) {
                    try {
                        connectedSocket.close();
                    } catch (IOException e) {
                    }
                }
            }
            mConnectedSocketMap.clear();
        }
    }

    public boolean sendData(String uuidText, BluetoothDevice device, byte[] data) {
        return sendData(UUID.fromString(uuidText), device, data, 0, data.length);
    }

    public boolean sendData(UUID uuid, BluetoothDevice device, byte[] data) {
        return sendData(uuid, device, data, 0, data.length);
    }

    public boolean sendData(String uuidText, BluetoothDevice device, byte[] data, int offset, int length) {
        return sendData(UUID.fromString(uuidText), device, data, offset, length);
    }

    public boolean sendData(UUID uuid, BluetoothDevice device, byte[] data, int offset, int length) {
        boolean ret = false;

        if (mStartHelper && device != null && data != null) {
            synchronized (mSendDataQueue) {
                DataBox dataBox = new DataBox(uuid, device, data, offset, length);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    mSendDataQueue.add(dataBox);
                    mSendDataQueue.notify();

                    if (mSendDataThread == null) {
                        mSendDataThread = new Thread(mClientRunnable);
                        mSendDataThread.start();
                    }
                } else {
                    // create bound and wait for the result
                    mBondingDeviceMap.put(device, dataBox);
                    createBond(dataBox);
                }
            }
            ret = true;
        }

        return ret;
    }

    public int readData(BluetoothDevice device, UUID uuid, byte[] readBuffer) {
        int ret = -1;

        BluetoothSocket socket = getServerSocket(device, uuid);

        if (socket != null) {
            InputStream stream = null;
            try {
                stream = socket.getInputStream();
            } catch (IOException e) {
                LogUtil.exception(TAG, e);
            }

            if (stream != null) {
                try {
                    ret = stream.read(readBuffer);
                } catch (IOException e) {
                    LogUtil.exception(TAG, e);
                }
            }
        }

        return ret;
    }

    private boolean createBond(DataBox dataBox) {
        boolean ret = false;

        if (dataBox != null && dataBox.mDevice != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ret = dataBox.mDevice.createBond();
            } else {
                try {
                    Class class1 = Class.forName("android.bluetooth.BluetoothDevice");
                    Method createBondMethod = class1.getMethod("createBond");
                    ret = (Boolean) createBondMethod.invoke(dataBox.mDevice);
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

    private void changeStatus(final OnBluetoothStatusListener targetListener, final Integer status, final Integer scanMode) {
        ThreadUtil.runOnMainThread(mContext, new Runnable() {
            @Override
            public void run() {
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
            }
        });
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
                } else if (sBluetoothStatus == StatusStartBluetooth) {
                    // bluetooth is enabled at once, but user changes disabled
                    enableBluetoothWithConfirmation();
                    changeStatus(StatusProgress, null);
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

    private BluetoothSocket getClientSocket(DataBox dataBox) {
        BluetoothSocket clientSocket = null;
        HashMap<UUID, BluetoothSocket> connectedSocketMap = null;
        synchronized (mConnectedSocketMap) {
            connectedSocketMap = mConnectedSocketMap.get(dataBox.mDevice);
        }
        if (connectedSocketMap != null) {
            synchronized (connectedSocketMap) {
                clientSocket = connectedSocketMap.get(dataBox.mTargetUUID);
            }
        }

        try {
            if (clientSocket == null || !clientSocket.isConnected()) {
                if (clientSocket != null) {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                    }
                }

                // create connection socket
                int trialCount = 0;
                Exception lastException = null;
                while (trialCount <= MaxConnectionRetryCount) {
                    try {
                        clientSocket = dataBox.mDevice.createRfcommSocketToServiceRecord(dataBox.mTargetUUID);
                        clientSocket.connect();

                        if (connectedSocketMap == null) {
                            connectedSocketMap = new HashMap<UUID, BluetoothSocket>();
                            synchronized (mConnectedSocketMap) {
                                mConnectedSocketMap.put(dataBox.mDevice, connectedSocketMap);
                            }
                        }

                        synchronized (connectedSocketMap) {
                            connectedSocketMap.put(dataBox.mTargetUUID, clientSocket);
                        }

                        LogUtil.d(TAG, "connection established");
                        break;
                    } catch (IOException e) {
                        lastException = e;
                        clientSocket = null;
                        Thread.sleep(ConnectionRetryIntervalMS);
                        trialCount++;
                    }
                }

                if (lastException != null) {
                    LogUtil.e(TAG, lastException.getLocalizedMessage());
                }
            } else {
                LogUtil.d(TAG, "reuse connection");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, e.getLocalizedMessage());
        }

        return clientSocket;
    }

    private BluetoothSocket getServerSocket(BluetoothDevice device, UUID uuid) {
        BluetoothSocket serverSocket = null;
        HashMap<UUID, BluetoothSocket> connectedSocketMap = null;
        synchronized (mConnectedSocketMap) {
            connectedSocketMap = mConnectedSocketMap.get(device);
        }

        if (connectedSocketMap != null) {
            synchronized (connectedSocketMap) {
                serverSocket = connectedSocketMap.get(uuid);
            }
        }

        return serverSocket;
    }

    private void notifySendDataResult(int result, DataBox dataBox) {
        notifySendDataResult(result, dataBox.mDevice, dataBox.mData, dataBox.mOffset, dataBox.mLength);
    }

    private void notifySendDataResult(final int result, final BluetoothDevice device, final byte[] data, final int offset, final int length) {
        final OnNotifyResultListener fListener = mNotifyResultListener;
        if (fListener != null) {
            ThreadUtil.runOnMainThread(mContext, new Runnable() {
                @Override
                public void run() {
                    fListener.onSendDataResult(result, device, data, offset, length);
                }
            });
        }
    }

    private static class DataBox {
        UUID mTargetUUID;
        BluetoothDevice mDevice;
        byte[] mData;
        int mOffset = 0;
        int mLength = 0;

        DataBox(UUID uuid, BluetoothDevice device, byte[] data, int offset, int length) {
            if (uuid == null || device == null || data == null) {
                throw new NullPointerException("uuid == " + uuid + " || device == " + device + " || data == " + data);
            }

            mTargetUUID = uuid;
            mDevice = device;
            mData = data;
            mOffset = offset;
            mLength = length;
        }
    }

    private final Handler.Callback mMessageCallback = new Handler.Callback() {
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

    private final Runnable mClientRunnable = new Runnable() {
        @Override
        public void run() {
            DataBox dataBox = null;

            while (mStartHelper || mSendDataQueue.size() > 0) {
                dataBox = (DataBox) mSendDataQueue.poll();
                if (dataBox != null) {
                    // send data
                    BluetoothSocket clientSocket = getClientSocket(dataBox);

                    if (clientSocket != null && clientSocket.isConnected()) {
                        try {
                            clientSocket.getOutputStream().write(dataBox.mData, dataBox.mOffset, dataBox.mLength);
                            notifySendDataResult(SendSuccess, dataBox);
                        } catch (IOException e) {
                            LogUtil.e(TAG, e.getLocalizedMessage());
                            notifySendDataResult(SendFailByOutputError, dataBox);
                        }
                    } else {
                        notifySendDataResult(SendFailByConnectError, dataBox);
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

    private final Runnable mServerSocketRunnable = new Runnable() {
        @Override
        public void run() {
            BluetoothServerSocket serverSocket = null;

            synchronized (mServerSocketRunnable) {
                if (mServerSocket == null) {
                    try {
                        serverSocket = mServerSocket = sAdapter.listenUsingRfcommWithServiceRecord(mApplicationName, mServerUuid);
                    } catch (IOException e) {
                        LogUtil.e(TAG, e.getLocalizedMessage());
                    }

                } else {
                    // already run other thread for server
                }
            }

            if (serverSocket != null) {
                while (mServerSocket != null) {
                    BluetoothSocket connectedSocket = null;
                    try {
                        connectedSocket = serverSocket.accept();
                    } catch (IOException e) {
                        mServerSocket = null;
                        break;
                    }

                    if (mStartHelper && (connectedSocket != null)) {
                        synchronized (mConnectedSocketMap) {
                            HashMap<UUID, BluetoothSocket> connectedSocketMap = mConnectedSocketMap.get(mServerUuid);
                            if (connectedSocketMap == null) {
                                connectedSocketMap = new HashMap<UUID, BluetoothSocket>();
                            }
                            connectedSocketMap.put(mServerUuid, connectedSocket);
                            mConnectedSocketMap.put(connectedSocket.getRemoteDevice(), connectedSocketMap);
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (LaunchBluetooth.equals(action)) {
                int ret = intent.getIntExtra(HandleResultActivity.INTENT_INT_EXTRA_RESULT_CODE_FROM_TARGET, Activity.RESULT_CANCELED);

                if (ret == Activity.RESULT_OK) {
                    LocalBroadcastManager.getInstance(mContext).registerReceiver(mLocalBroadcastReceiver, new IntentFilter(LaunchBluetooth));
                    addBluetoothAccessHelper(BluetoothAccessHelper.this);
                }

                changeStatus(StatusStartBluetooth, null);
            }
        }
    };
}
