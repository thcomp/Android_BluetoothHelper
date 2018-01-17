package jp.co.thcomp.ble_sample;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.co.thcomp.bluetoothhelper.BleCentral;
import jp.co.thcomp.bluetoothhelper.BlePeripheral;
import jp.co.thcomp.bluetoothhelper.FoundLeDevice;
import jp.co.thcomp.util.RuntimePermissionUtil;
import jp.co.thcomp.util.ToastUtil;

public class MainActivity extends AppCompatActivity {
    private static final int ViewTagCharacteristicList = "ViewTagCharacteristicList".hashCode();
    private static final boolean ModePeripheral = false;
    private static final boolean ModeCentral = true;

    private BlePeripheral mBlePeripheral;
    private BleCentral mBleCentral;
    private PeripheralListAdapter mPeripheralListAdapter;
    private ServiceListAdapter mServiceListAdapter;
    private Map<BluetoothDevice, FoundLeDevice> mFoundLeDeviceMap = new HashMap<>();
    private Map<FoundLeDevice, List<BluetoothGattService>> mDeviceServiceMap = new HashMap<>();
    private List<BluetoothGattService> mPeripheralServiceList = new ArrayList<>();
    private Map<BluetoothGattCharacteristic, BluetoothGattService> mCharacteristicServiceMap = new HashMap<>();
    private ExpandableListView mPeripheralExListView;
    private ExpandableListView mServiceExListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        ((SwitchCompat) findViewById(R.id.swWorkMode)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//            @Override
//            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
//                changeBLEMode(((SwitchCompat) findViewById(R.id.swWorkSwitch)).isChecked());
//            }
//        });
        ((SwitchCompat) findViewById(R.id.swWorkSwitch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton compoundButton, boolean checked) {
                if (checked) {
                    boolean workMode = ((SwitchCompat) findViewById(R.id.swWorkMode)).isChecked();
                    if (workMode == ModeCentral) {
                        RuntimePermissionUtil.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, new RuntimePermissionUtil.OnRequestPermissionsResultListener() {
                            @Override
                            public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
                                boolean allGrants = true;

                                for (int grantResult : grantResults) {
                                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                                        allGrants = false;
                                        break;
                                    }
                                }

                                if (allGrants) {
                                    startBleCentral();
                                } else {
                                    ToastUtil.showToast(MainActivity.this, R.string.cannot_start_central, Toast.LENGTH_LONG);
                                    compoundButton.setChecked(false);
                                }
                            }
                        });
                    } else {
                        startBlePeripheral();
                    }
                }

                changeViewStatus(checked);
            }
        });

        mPeripheralListAdapter = new PeripheralListAdapter();
        mServiceListAdapter = new ServiceListAdapter();
        (mServiceExListView = findViewById(R.id.elvServiceList)).setAdapter(mServiceListAdapter);
        (mPeripheralExListView = findViewById(R.id.elvPeripheralList)).setAdapter(mPeripheralListAdapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBlePeripheral != null) {
            mBlePeripheral.stop();
            mBlePeripheral = null;
        }
        if (mBleCentral != null) {
            mBleCentral.stop();
            mBleCentral = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        RuntimePermissionUtil.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void changeViewStatus(boolean startBle) {
        SwitchCompat swWorkMode = findViewById(R.id.swWorkMode);

        // WorkMode用のSwitchを表示切替
        swWorkMode.setEnabled(!startBle);

        // BLEのモード向けに表示を切り替える
        if (startBle) {
            if (swWorkMode.isChecked() == ModeCentral) {
                findViewById(R.id.flCentralArea).setVisibility(View.VISIBLE);
                findViewById(R.id.flPeripheralArea).setVisibility(View.GONE);
            } else {
                findViewById(R.id.flCentralArea).setVisibility(View.GONE);
                findViewById(R.id.flPeripheralArea).setVisibility(View.VISIBLE);
            }
        } else {
            if (swWorkMode.isChecked() == ModeCentral) {
                findViewById(R.id.flCentralArea).setVisibility(View.GONE);
            } else {
                findViewById(R.id.flPeripheralArea).setVisibility(View.GONE);
            }
        }
    }

//    private void changeBLEMode(boolean started) {
//        if (started) {
//            SwitchCompat swWorkMode = findViewById(R.id.swWorkMode);
//
//            if (swWorkMode.isChecked() == ModeCentral) {
//                startBleCentral();
//            } else {
//                startBlePeripheral();
//            }
//        } else {
//            if (mBlePeripheral != null) {
//                mBlePeripheral.stop();
//                mBlePeripheral = null;
//            }
//            if (mBleCentral != null) {
//                mBleCentral.stop();
//                mBleCentral = null;
//            }
//        }
//    }

    private void startBleCentral() {
        if (mBleCentral == null) {
            mBleCentral = new BleCentral(MainActivity.this);
            mBleCentral.setOnBluetoothStatusListener(new BleCentral.OnBluetoothStatusListener() {
                @Override
                public void onStatusChange(int status, int scanMode) {
                    switch (status) {
                        case BleCentral.StatusNoSupportBluetooth:
                            ToastUtil.showToast(MainActivity.this, R.string.nosupport_bluetooth, Toast.LENGTH_LONG);
                            finish();
                            break;
                        case BleCentral.StatusDisableBluetooth:
                            ToastUtil.showToast(MainActivity.this, R.string.disable_bluetooth, Toast.LENGTH_LONG);
                            finish();
                            break;
                        case BleCentral.StatusStartBluetooth:
                            mBleCentral.startScanPeripheral(0);
                            break;
                        default:
                            break;
                    }
                }
            });
            mBleCentral.setOnFoundLeDeviceListener(new BleCentral.OnFoundLeDeviceListener() {
                @Override
                public void onFoundLeDevice(FoundLeDevice device) {
                    mFoundLeDeviceMap.put(device.getDevice(), device);
                    mBleCentral.discoverServices(device.getDevice(), new BleCentral.OnServicesDiscoveredListener() {
                        @Override
                        public void onServicesDiscovered(BluetoothDevice device, List<BluetoothGattService> serviceList) {
                            FoundLeDevice foundLeDevice = mFoundLeDeviceMap.get(device);
                            if (foundLeDevice != null) {
                                mDeviceServiceMap.put(foundLeDevice, serviceList);
                                mPeripheralListAdapter.dataSetChange();
                            }
                        }
                    });
                }

                @Override
                public void onTimeoutFindLeDevice() {

                }
            });
            mBleCentral.start();
        }
    }

    private void startBlePeripheral() {
        if (mBlePeripheral == null) {
            mBlePeripheral = new BlePeripheral(this);
            mBlePeripheral.setOnBluetoothStatusListener(new BlePeripheral.OnBluetoothStatusListener() {
                @Override
                public void onStatusChange(int status, int scanMode) {
                    switch (status) {
                        case BlePeripheral.StatusNoSupportBluetooth:
                            ToastUtil.showToast(MainActivity.this, R.string.nosupport_bluetooth, Toast.LENGTH_LONG);
                            finish();
                            break;
                        case BlePeripheral.StatusDisableBluetooth:
                            ToastUtil.showToast(MainActivity.this, R.string.disable_bluetooth, Toast.LENGTH_LONG);
                            finish();
                            break;
                        case BlePeripheral.StatusDisableBleAdvertising:
                            ToastUtil.showToast(MainActivity.this, R.string.disable_ble_advertising, Toast.LENGTH_LONG);
                            finish();
                            break;
                        case BlePeripheral.StatusStartBleAdvertising:
                            break;
                        default:
                            break;
                    }
                }
            });
            mBlePeripheral.setOnServiceChangedListener(new BlePeripheral.OnServiceChangedListener() {
                @Override
                public boolean onPreCharacteristicChanged(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                    boolean ret = false;

                    if (mBlePeripheral != null) {
                        BluetoothGattService service = mCharacteristicServiceMap.get(characteristic);
                        if (service != null) {
                            for (BluetoothGattCharacteristic tempCharacteristic : service.getCharacteristics()) {
                                if (characteristic.getUuid().equals(tempCharacteristic.getUuid())) {
                                    tempCharacteristic.setValue(value);
                                    mServiceListAdapter.dataSetChange();
                                    ret = true;
                                    break;
                                }
                            }
                        }
                    }

                    return ret;
                }

                @Override
                public void onCharacteristicChanged(BluetoothGattCharacteristic changedCharacteristic) {
                    // 処理なし
                }
            });
            mBlePeripheral.start();
        }
    }

    private abstract class LocalExpandableListAdapter implements ExpandableListAdapter {
        protected DataSetObserver mObserver;

        @Override
        public void registerDataSetObserver(DataSetObserver dataSetObserver) {
            mObserver = dataSetObserver;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
            if (mObserver != null && mObserver.equals(dataSetObserver)) {
                mObserver = null;
            }
        }

        public void dataSetChange() {
            if (mObserver != null) {
                mObserver.onChanged();
            }
        }

        @Override
        public long getGroupId(int i) {
            return i;
        }

        @Override
        public long getChildId(int i, int i1) {
            return (((long) i) << Integer.SIZE) | i1;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int i, int i1) {
            return false;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public void onGroupExpanded(int i) {

        }

        @Override
        public void onGroupCollapsed(int i) {

        }

        @Override
        public long getCombinedChildId(long l, long l1) {
            return (l << Integer.SIZE) | (l1 & 0x00000000FFFFFFFF);
        }

        @Override
        public long getCombinedGroupId(long l) {
            return l;
        }
    }

    // for Central
    private class PeripheralListAdapter extends LocalExpandableListAdapter {
        private FoundLeDevice[] mFoundLeDeviceArray = new FoundLeDevice[0];

        @Override
        public void dataSetChange() {
            if (mBleCentral != null) {
                mFoundLeDeviceArray = mBleCentral.getFoundLeDeviceList();
            } else {
                mFoundLeDeviceArray = new FoundLeDevice[0];
            }

            super.dataSetChange();
        }

        @Override
        public int getGroupCount() {
            return mFoundLeDeviceArray.length;
        }

        @Override
        public int getChildrenCount(int i) {
            FoundLeDevice foundLeDevice = (FoundLeDevice) getGroup(i);
            List<BluetoothGattService> serviceList = foundLeDevice.getServiceList();
            return serviceList != null ? serviceList.size() : 0;
        }

        @Override
        public Object getGroup(int i) {
            return mFoundLeDeviceArray[i];
        }

        @Override
        public Object getChild(int i, int i1) {
            BluetoothGattService service = null;
            FoundLeDevice foundLeDevice = (FoundLeDevice) getGroup(i);

            if (foundLeDevice != null) {
                List<BluetoothGattService> serviceList = foundLeDevice.getServiceList();

                if (serviceList != null && serviceList.size() > i1) {
                    service = serviceList.get(i1);
                }
            }

            return service;
        }

        @Override
        public View getGroupView(int i, boolean b, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_peripheral_list, parent, false);
            }

            if (mBleCentral != null) {
                FoundLeDevice foundLeDevice = mFoundLeDeviceArray[i];
                BluetoothDevice device = foundLeDevice.getDevice();
                StringBuilder contentBuilder = new StringBuilder();

                String content = device.getName();
                if (content != null && content.length() > 0) {
                    contentBuilder.append(getString(R.string.name)).append(": ").append(content).append("\n");
                }
                content = device.getAddress();
                if (content != null && content.length() > 0) {
                    contentBuilder.append(getString(R.string.address)).append(": ").append(content).append("\n");
                }
                ((TextView) convertView.findViewById(R.id.tvServiceInformation)).setText(content);
            }

            return convertView;
        }

        @Override
        public View getChildView(int i, int i1, boolean b, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_peripheral_list, parent, false);
            } else {
                ((ViewGroup) convertView.findViewById(R.id.llCharacteristicList)).removeAllViews();
            }

            if (mBleCentral != null) {
                final LinearLayout fLlCharacteristicListLayout = convertView.findViewById(R.id.llCharacteristicList);
                FoundLeDevice foundLeDevice = (FoundLeDevice) getGroup(i);
                List<BluetoothGattService> serviceList = foundLeDevice.getServiceList();
                BluetoothDevice device = foundLeDevice.getDevice();
                BluetoothGattService service = (BluetoothGattService) getChild(i, i1);

                if (service != null) {
                    List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
                    if (characteristicList != null) {
                        fLlCharacteristicListLayout.setTag(ViewTagCharacteristicList, characteristicList);

                        for (BluetoothGattCharacteristic characteristic : characteristicList) {
                            mBleCentral.readCharacteristic(device, characteristic, new BleCentral.OnCharacteristicReadListener() {
                                @Override
                                public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                                    List<BluetoothGattCharacteristic> characteristicList = (List<BluetoothGattCharacteristic>) fLlCharacteristicListLayout.getTag(ViewTagCharacteristicList);
                                    if (characteristicList != null && characteristicList.contains(characteristic)) {
                                        View characteristicView = getLayoutInflater().inflate(R.layout.item_characteristic_list, fLlCharacteristicListLayout, false);
                                        final EditText fEtCharacteristic = characteristicView.findViewById(R.id.etCharacteristic);
                                        Button btnUpdateCharacteristic = characteristicView.findViewById(R.id.btnUpdateCharacteristic);

                                        fEtCharacteristic.setText(new String(characteristic.getValue()));
                                        if ((characteristic.getPermissions() & BluetoothGattCharacteristic.PERMISSION_WRITE) == BluetoothGattCharacteristic.PERMISSION_WRITE) {
                                            fEtCharacteristic.setEnabled(true);
                                            btnUpdateCharacteristic.setEnabled(true);
                                            btnUpdateCharacteristic.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    mBleCentral.writeCharacteristic(gatt.getDevice(), characteristic.getService().getUuid(), characteristic.getUuid(), fEtCharacteristic.getText().toString().getBytes());
                                                }
                                            });
                                        } else {
                                            fEtCharacteristic.setEnabled(false);
                                            btnUpdateCharacteristic.setEnabled(false);
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            }

            return convertView;
        }

        @Override
        public boolean isEmpty() {
            boolean isEmpty = true;

            if (mBleCentral != null) {
                FoundLeDevice[] foundLeDeviceArray = mBleCentral.getFoundLeDeviceList();
                isEmpty = ((foundLeDeviceArray == null) || (foundLeDeviceArray.length == 0));
            }

            return isEmpty;
        }
    }

    // for Peripheral
    private class ServiceListAdapter extends LocalExpandableListAdapter {
        @Override
        public int getGroupCount() {
            return mPeripheralServiceList.size() + 1/* for add new service */;
        }

        @Override
        public int getChildrenCount(int i) {
            BluetoothGattService service = (BluetoothGattService) getGroup(i);
            int ret = 1/* for add new characteristic */;

            if (service != null) {
                ret += service.getCharacteristics().size();
            }

            return ret;
        }

        @Override
        public Object getGroup(int i) {
            Object ret = null;

            if (i > 0) {
                i--;    // 「add new service」分減算
                ret = mPeripheralServiceList.get(i);
            }

            return ret;
        }

        @Override
        public Object getChild(int i, int i1) {
            Object ret = null;
            BluetoothGattService service = (BluetoothGattService) getGroup(i);

            if (service != null) {
                if (i1 > 0) {
                    i1--;   // 「add new characteristic」分減算
                    List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
                    ret = characteristicList.get(i1);
                }
            }

            return ret;
        }

        public View getGroupView(int i, boolean b, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_peripheral_list, parent, false);
            }

            if (mBlePeripheral != null) {
                if (i == 0) {
                    // for add new service
                    convertView.findViewById(R.id.llAddNewServiceArea).setVisibility(View.VISIBLE);
                    convertView.findViewById(R.id.llServiceInformationArea).setVisibility(View.GONE);

                    convertView.findViewById(R.id.btnAddNewService).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mBlePeripheral != null) {
                                BluetoothGattService service = new BluetoothGattService(UUID.randomUUID(), BlePeripheral.GattServiceType.Primary.getValue());
                                mBlePeripheral.addService(service);
                                mPeripheralServiceList.add(service);
                                ServiceListAdapter.this.dataSetChange();
                            }
                        }
                    });
                } else {
                    // for service information
                    convertView.findViewById(R.id.llAddNewServiceArea).setVisibility(View.GONE);
                    convertView.findViewById(R.id.llServiceInformationArea).setVisibility(View.VISIBLE);

                    BluetoothGattService service = (BluetoothGattService) getGroup(i);
                    StringBuilder contentBuilder = new StringBuilder();

                    String content = service.getUuid().toString();
                    if (content != null && content.length() > 0) {
                        contentBuilder.append(getString(R.string.uuid)).append(": ").append(content).append("\n");
                    }
                    contentBuilder.append(getString(R.string.type)).append(": ").append(service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY ? getString(R.string.primary) : getString(R.string.secondary)).append("\n");
                    ((TextView) convertView.findViewById(R.id.tvServiceInformation)).setText(content);
                }
            }

            return convertView;
        }

        @Override
        public View getChildView(int i, int i1, boolean b, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_characteristic_list, parent, false);
            }

            if (mBlePeripheral != null) {
                Button btnUpdateCharacteristic = convertView.findViewById(R.id.btnUpdateCharacteristic);
                Button btnAddNewCharacteristic = convertView.findViewById(R.id.btnAddNewCharacteristic);

                final BluetoothGattService fService = (BluetoothGattService) getGroup(i);
                final BluetoothGattCharacteristic fCharacteristic = (BluetoothGattCharacteristic) getChild(i, i1);
                final EditText fEtCharacteristic = convertView.findViewById(R.id.etCharacteristic);
                final SwitchCompat fSwWritable = convertView.findViewById(R.id.swWritable);

                if (i1 == 0) {
                    // for add new characteristic
                    convertView.findViewById(R.id.llUpdateCharacteristicArea).setVisibility(View.GONE);
                    convertView.findViewById(R.id.llAddNewCharacteristicArea).setVisibility(View.VISIBLE);

                    btnAddNewCharacteristic.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mBlePeripheral != null) {
                                String characteristicData = fEtCharacteristic.getText().toString();
                                if (characteristicData != null && characteristicData.length() > 0) {
                                    if (fSwWritable.isChecked()) {
                                        mBlePeripheral.addCharacteristicWritable(fService.getUuid(), UUID.randomUUID(), characteristicData.getBytes(), true);
                                    } else {
                                        mBlePeripheral.addCharacteristicReadOnly(fService.getUuid(), UUID.randomUUID(), characteristicData.getBytes(), true);
                                    }

                                    mCharacteristicServiceMap.put(fCharacteristic, fService);
                                    fEtCharacteristic.setText("");
                                    mServiceListAdapter.dataSetChange();
                                    ToastUtil.showToast(MainActivity.this, R.string.add_new_characteristic, Toast.LENGTH_LONG);
                                }
                            }
                        }
                    });
                } else {
                    // for characteristic information
                    convertView.findViewById(R.id.llUpdateCharacteristicArea).setVisibility(View.VISIBLE);
                    convertView.findViewById(R.id.llAddNewCharacteristicArea).setVisibility(View.GONE);

                    fEtCharacteristic.setText(new String(fCharacteristic.getValue()));
                    btnUpdateCharacteristic.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mBlePeripheral != null) {
                                byte[] newData = fEtCharacteristic.getText().toString().getBytes();
                                byte[] oldData = fCharacteristic.getValue();

                                if (!Arrays.equals(oldData, newData)) {
                                    mBlePeripheral.updateCharacteristic(fService.getUuid(), fCharacteristic.getUuid(), newData, true);
                                    ToastUtil.showToast(MainActivity.this, R.string.notify_characteristic_changed, Toast.LENGTH_LONG);
                                }
                            }
                        }
                    });
                }
            }

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int i, int i1) {
            return true;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            boolean isEmpty = true;

            if (mBleCentral != null) {
                FoundLeDevice[] foundLeDeviceArray = mBleCentral.getFoundLeDeviceList();
                isEmpty = ((foundLeDeviceArray == null) || (foundLeDeviceArray.length == 0));
            }

            return isEmpty;
        }
    }
}
