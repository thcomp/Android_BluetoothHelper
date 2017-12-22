package jp.co.thcomp.bluetoothhelper;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jp.co.thcomp.util.LogUtil;

public class DeviceDiscoverer {
    private static final String TAG = DeviceDiscoverer.class.getSimpleName();
    private static final int MsgNotifyTimeout = "MsgNotifyTimeout".hashCode();
    private static final int MsgFindDevice = "MsgFindDevice".hashCode();
    private static final int MsgNotifyLeTimeout = "MsgNotifyLeTimeout".hashCode();
    private static final int MsgFindLeDevice = "MsgFindLeDevice".hashCode();
    private static DeviceDiscoverer sInstance;
    private static Object sSemaphore = new Object();

    public static DeviceDiscoverer getInstance(Context context) {
        synchronized (sSemaphore) {
            if (sInstance == null) {
                sInstance = new DeviceDiscoverer(context);
            }
        }

        return sInstance;
    }

    public interface OnFoundDeviceListener {
        void onFoundDevice(BluetoothDevice device);

        void onTimeoutFindDevice();
    }

    public interface OnFoundLeDeviceListener {
        void onFoundLeDevice(BluetoothDevice device);

        void onTimeoutFindLeDevice();
    }

    private Context mContext;
    private BluetoothAccessHelper mBtHelper;
    private ArrayList<BluetoothDevice> mFoundDeviceList = new ArrayList<BluetoothDevice>();
    private HashMap<Long, ArrayList<OnFoundDeviceListener>> mTimeoutTimeMap = new HashMap<Long, ArrayList<OnFoundDeviceListener>>();
    private ArrayList<BluetoothDevice> mFoundLeDeviceList = new ArrayList<BluetoothDevice>();
    private HashMap<Long, ArrayList<OnFoundLeDeviceListener>> mLeTimeoutTimeMap = new HashMap<Long, ArrayList<OnFoundLeDeviceListener>>();
    private Handler mMainLooperHandler;

    private DeviceDiscoverer(Context context) {
        if (context == null) {
            throw new NullPointerException("context == null");
        }

        mContext = context;
        mBtHelper = new BluetoothAccessHelper(context);
        mMainLooperHandler = new Handler(context.getMainLooper(), mCallback);
    }

    public boolean startDiscoverDevices(long durationMS, OnFoundDeviceListener listener) {
        LogUtil.d(TAG, "startDiscoverDevices(S)");

        boolean ret = false;
        if ((ret = mBtHelper.startDiscoverDevices())) {
            long timeoutTimeMS = System.currentTimeMillis() + durationMS;

            if (durationMS == 0) {
                // durationが指定されたときは無制限待ち
                timeoutTimeMS = Long.MAX_VALUE;
                durationMS = Long.MAX_VALUE;
            } else {
                timeoutTimeMS += durationMS;
            }

            synchronized (mTimeoutTimeMap) {
                if (mTimeoutTimeMap.size() == 0) {
                    LogUtil.d(TAG, "register broadcast receiver");
                    mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                }

                ArrayList<OnFoundDeviceListener> listenerList = mTimeoutTimeMap.get(timeoutTimeMS);

                if (listenerList == null) {
                    listenerList = new ArrayList<OnFoundDeviceListener>();
                    mTimeoutTimeMap.put(timeoutTimeMS, listenerList);
                }

                if (!listenerList.contains(listener)) {
                    listenerList.add(listener);
                }
            }

            mMainLooperHandler.sendMessageDelayed(Message.obtain(mMainLooperHandler, MsgNotifyTimeout, timeoutTimeMS), durationMS);
        }

        LogUtil.d(TAG, "startDiscoverDevices(E): " + ret);
        return ret;
    }

    public void stopDiscoverDevices(OnFoundDeviceListener listener, boolean removeAllMatchedListener) {
        LogUtil.d(TAG, "stopDiscoverDevices(S)");
        synchronized (mTimeoutTimeMap) {
            if (mTimeoutTimeMap.size() > 0) {
                ArrayList<OnFoundDeviceListener> listenerList = null;
                for (Long reservedTimeoutTimeMS : mTimeoutTimeMap.keySet()) {
                    if ((listenerList = mTimeoutTimeMap.get(reservedTimeoutTimeMS)).contains(listener)) {
                        listenerList.remove(listener);
                        if (listenerList.size() == 0) {
                            mTimeoutTimeMap.remove(reservedTimeoutTimeMS);
                        }

                        if (removeAllMatchedListener) {
                            continue;
                        } else {
                            break;
                        }
                    }
                }

                if (mTimeoutTimeMap.size() == 0) {
                    LogUtil.d(TAG, "unregister broadcast receiver");
                    mContext.unregisterReceiver(mReceiver);
                }

                mBtHelper.stopDiscoverDevices();
            }
        }
        LogUtil.d(TAG, "stopDiscoverDevices(E)");
    }

    public boolean startDiscoverLeDevices(long durationMS, OnFoundLeDeviceListener listener) {
        LogUtil.d(TAG, "startDiscoverDevices(S)");

        boolean ret = false;
        if ((ret = mBtHelper.startDiscoverLeDevices(mFoundLeDeviceListener))) {
            long timeoutTimeMS = System.currentTimeMillis() + durationMS;

            if (durationMS == 0) {
                // durationが指定されたときは無制限待ち
                timeoutTimeMS = Long.MAX_VALUE;
                durationMS = Long.MAX_VALUE;
            } else {
                timeoutTimeMS += durationMS;
            }

            synchronized (mLeTimeoutTimeMap) {
                ArrayList<OnFoundLeDeviceListener> listenerList = mLeTimeoutTimeMap.get(timeoutTimeMS);

                if (listenerList == null) {
                    listenerList = new ArrayList<OnFoundLeDeviceListener>();
                    mLeTimeoutTimeMap.put(timeoutTimeMS, listenerList);
                }

                if (!listenerList.contains(listener)) {
                    listenerList.add(listener);
                }
            }

            mMainLooperHandler.sendMessageDelayed(Message.obtain(mMainLooperHandler, MsgNotifyLeTimeout, timeoutTimeMS), durationMS);
        }

        LogUtil.d(TAG, "startDiscoverDevices(E): " + ret);
        return ret;
    }

    public void stopDiscoverLeDevices(OnFoundLeDeviceListener listener, boolean removeAllMatchedListener) {
        LogUtil.d(TAG, "stopDiscoverDevices(S)");
        synchronized (mLeTimeoutTimeMap) {
            if (mLeTimeoutTimeMap.size() > 0) {
                ArrayList<OnFoundLeDeviceListener> listenerList = null;
                for (Long reservedTimeoutTimeMS : mLeTimeoutTimeMap.keySet()) {
                    if ((listenerList = mLeTimeoutTimeMap.get(reservedTimeoutTimeMS)).contains(listener)) {
                        listenerList.remove(listener);
                        if (listenerList.size() == 0) {
                            mLeTimeoutTimeMap.remove(reservedTimeoutTimeMS);
                        }

                        if (removeAllMatchedListener) {
                            continue;
                        } else {
                            break;
                        }
                    }
                }

                if (mLeTimeoutTimeMap.size() == 0) {
                    LogUtil.d(TAG, "unregister broadcast receiver");
                    mContext.unregisterReceiver(mReceiver);
                }

                mBtHelper.stopDiscoverDevices();
            }
        }
        LogUtil.d(TAG, "stopDiscoverDevices(E)");
    }

    private Handler.Callback mCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == MsgNotifyTimeout) {
                LogUtil.d(TAG, "handle MsgNotifyTimeout");
                Long timeoutTimeMS = message.obj instanceof Long ? (Long) message.obj : null;

                if (timeoutTimeMS != null) {
                    synchronized (mTimeoutTimeMap) {
                        if (mTimeoutTimeMap.size() > 0) {
                            for (Long reservedTimeoutTimeMS : mTimeoutTimeMap.keySet().toArray(new Long[0])) {
                                if (reservedTimeoutTimeMS < timeoutTimeMS) {
                                    ArrayList<OnFoundDeviceListener> listenerList = mTimeoutTimeMap.remove(reservedTimeoutTimeMS);
                                    if (listenerList != null && listenerList.size() > 0) {
                                        for (OnFoundDeviceListener listener : listenerList) {
                                            listener.onTimeoutFindDevice();
                                        }
                                    }
                                }
                            }

                            if (mTimeoutTimeMap.size() == 0) {
                                LogUtil.d(TAG, "unregister broadcast receiver");
                                mContext.unregisterReceiver(mReceiver);
                            }
                        }
                    }
                }
            } else if (message.what == MsgFindDevice) {
                LogUtil.d(TAG, "handle MsgFindDevice");
                BluetoothDevice device = (BluetoothDevice) message.obj;

                synchronized (mTimeoutTimeMap) {
                    if (mTimeoutTimeMap.size() > 0) {
                        ArrayList<Long> removeList = new ArrayList<>();

                        for (Map.Entry<Long, ArrayList<OnFoundDeviceListener>> entrySet : mTimeoutTimeMap.entrySet()) {
                            ArrayList<OnFoundDeviceListener> listenerList = entrySet.getValue();
                            if (listenerList.size() > 0) {
                                for (OnFoundDeviceListener listener : listenerList) {
                                    listener.onFoundDevice(device);
                                }
                            } else {
                                removeList.add(entrySet.getKey());
                            }
                        }
                    }
                }
            } else if (message.what == MsgNotifyLeTimeout) {
                LogUtil.d(TAG, "handle MsgNotifyLeTimeout");
                Long timeoutTimeMS = message.obj instanceof Long ? (Long) message.obj : null;

                if (timeoutTimeMS != null) {
                    synchronized (mLeTimeoutTimeMap) {
                        if (mLeTimeoutTimeMap.size() > 0) {
                            for (Long reservedTimeoutTimeMS : mLeTimeoutTimeMap.keySet().toArray(new Long[0])) {
                                if (reservedTimeoutTimeMS < timeoutTimeMS) {
                                    ArrayList<OnFoundLeDeviceListener> listenerList = mLeTimeoutTimeMap.remove(reservedTimeoutTimeMS);
                                    if (listenerList != null && listenerList.size() > 0) {
                                        for (OnFoundLeDeviceListener listener : listenerList) {
                                            listener.onTimeoutFindLeDevice();
                                        }
                                    }
                                }
                            }

                            if (mLeTimeoutTimeMap.size() == 0) {
                                LogUtil.d(TAG, "stop discover BLE device");
                                mBtHelper.stopDiscoverLeDevices();
                            }
                        }
                    }
                }
            } else if (message.what == MsgFindLeDevice) {
                LogUtil.d(TAG, "handle MsgFindLeDevice");
                BluetoothDevice device = (BluetoothDevice) message.obj;

                synchronized (mTimeoutTimeMap) {
                    if (mTimeoutTimeMap.size() > 0) {
                        ArrayList<Long> removeList = new ArrayList<>();

                        for (Map.Entry<Long, ArrayList<OnFoundLeDeviceListener>> entrySet : mLeTimeoutTimeMap.entrySet()) {
                            ArrayList<OnFoundLeDeviceListener> listenerList = entrySet.getValue();
                            if (listenerList.size() > 0) {
                                for (OnFoundLeDeviceListener listener : listenerList) {
                                    listener.onFoundLeDevice(device);
                                }
                            } else {
                                removeList.add(entrySet.getKey());
                            }
                        }
                    }
                }
            }

            return true;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                LogUtil.d(TAG, "receive " + BluetoothDevice.ACTION_FOUND);

                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mFoundDeviceList.add(device);

                mMainLooperHandler.sendMessage(Message.obtain(mMainLooperHandler, MsgFindDevice, device));
            }
        }
    };

    private BluetoothAccessHelper.OnFoundLeDeviceListener mFoundLeDeviceListener = new BluetoothAccessHelper.OnFoundLeDeviceListener() {
        @Override
        public void onFoundLeDevice(BluetoothDevice device) {
            mFoundLeDeviceList.add(device);
            mMainLooperHandler.sendMessage(Message.obtain(mMainLooperHandler, MsgFindLeDevice, device));
        }
    };
}
