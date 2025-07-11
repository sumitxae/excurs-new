package com.minew.mst03demo;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.kongzue.dialogx.dialogs.WaitDialog;
import com.minew.ble.mst03.bean.MST03Entity;
import com.minew.ble.mst03.manager.MST03SensorBleManager;
import com.minew.ble.v3.enums.BleConnectionState;
import com.minew.ble.v3.interfaces.OnConnStateListener;
import com.minew.ble.v3.interfaces.OnScanDevicesResultListener;
import com.minew.ble.v3.utils.BLETool;
import com.minew.mst03demo.databinding.ActivityScanDevicesBinding;
import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.ExplainReasonCallback;
import com.permissionx.guolindev.callback.ForwardToSettingsCallback;
import com.permissionx.guolindev.callback.RequestCallback;
import com.permissionx.guolindev.request.ExplainScope;
import com.permissionx.guolindev.request.ForwardScope;

import java.util.Comparator;
import java.util.List;

public class ScanDevicesListActivity extends BaseActivity {

    private ActivityScanDevicesBinding binding;

    private ScanDevicesListAdapter mDevicesListAdapter;
    private ObjectAnimator mObjectAnimator;

    private MST03SensorBleManager mBleManager;

    private MST03Entity mst03Entity;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanDevicesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Set status bar icons/text to dark
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        initRefresh();
        initRecyclerView();
        initAnimator();
        initBleManager();
        initBlePermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setBleManagerListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeBleManagerListener();
    }

    private void initRefresh(){
        binding.swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                stopScan();
                startScan();
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void initRecyclerView(){
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(binding.recyclerView.getContext()));
        mDevicesListAdapter = new ScanDevicesListAdapter(R.layout.item_scan_device,null);

        binding.recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayout.VERTICAL));
        mDevicesListAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull BaseQuickAdapter<?, ?> adapter, @NonNull View view, int position) {
                mst03Entity =  mDevicesListAdapter.getItem(position);
                stopScan();
                setKey(mst03Entity.getMacAddress());
                connectedSensor();
            }
        });

        binding.recyclerView.setAdapter(mDevicesListAdapter);
    }

    private void initAnimator(){
        mObjectAnimator = ObjectAnimator.ofFloat(binding.ibHomeScan, "rotation", 0f, 360f);
        mObjectAnimator.setDuration(1500);
        //无限循环
        mObjectAnimator.setRepeatCount(ValueAnimator.INFINITE);
    }

    private void initBleManager(){
        mBleManager = MST03SensorBleManager.getInstance();

    }
    private void setBleManagerListener(){
        mBleManager.setOnConnStateListener(mConnStateListener);
    }
    private void removeBleManagerListener(){
        mBleManager.setOnConnStateListener(null);
    }

    private OnConnStateListener mConnStateListener = new OnConnStateListener() {
        @Override
        public void onUpdateConnState(String s, BleConnectionState mSensorConnectionState) {
            switch (mSensorConnectionState){
                case Connecting:
                    Log.d("TAG","Connecting");
                    break;
                case Connected:
                    Log.d("TAG","Connected");
                    break;
                case ConnectComplete:
                    WaitDialog.dismiss();
                    Log.d("TAG","ConnectComplete");
                    Intent intent = new Intent(ScanDevicesListActivity.this,DeviceConnectedCompleteActivity.class);
                    intent.putExtra("mac", mst03Entity.getMacAddress());
                    startActivity(intent);

                    break;
                case Disconnect:
                    WaitDialog.dismiss();
                    Log.d("TAG","Disconnect");
                    break;
                default:
                    break;
            }
        }
    };

    private void initBlePermission(){
        String[] requestPermissionList;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionList = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION};
        } else {
            requestPermissionList = new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION};
        }
        PermissionX.init(this).permissions(requestPermissionList)
                .onExplainRequestReason(new ExplainReasonCallback() {
                    @Override
                    public void onExplainReason(@NonNull ExplainScope scope, @NonNull List<String> deniedList) {
                        scope.showRequestReasonDialog(deniedList,getString(R.string.need_permission_continue),"Ok","Cancel");
                    }
                })
                .onForwardToSettings(new ForwardToSettingsCallback() {
                    @Override
                    public void onForwardToSettings(@NonNull ForwardScope scope, @NonNull List<String> deniedList) {
                        scope.showForwardToSettingsDialog(deniedList,getString(R.string.allow_permission_in_settings),"Ok","Cancel");
                    }
                })
                .request(new RequestCallback() {
                    @Override
                    public void onResult(boolean allGranted, @NonNull List<String> grantedList, @NonNull List<String> deniedList) {
                        if(allGranted){
                            checkoutBluetooth();
                        }else{
                            Toast.makeText(ScanDevicesListActivity.this, "The following permissions are denied", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }


    private void checkoutBluetooth(){
        switch (BLETool.checkBluetooth(this)){
            case BLE_NOT_SUPPORT:
                Toast.makeText(this, "Not Support BLE", Toast.LENGTH_SHORT).show();
                break;
            case BLUETOOTH_ON:
                startScan();
                break;
            case BLUETOOTH_OFF:
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, 4);
                break;
        }

    }



    private void startScan(){
        mDevicesListAdapter.setList(null);
        mBleManager.startScan(this, 5 * 60 * 1000, new OnScanDevicesResultListener<MST03Entity>() {

            @Override
            public void onScanResult(List<MST03Entity> list) {
                list.sort(new Comparator<MST03Entity>() {
                    @Override
                    public int compare(MST03Entity o1, MST03Entity o2) {
                        return o2.getRssi() - o1.getRssi();
                    }
                });
                mDevicesListAdapter.setList(list);
            }

            @Override
            public void onStopScan(List<MST03Entity> list) {

            }

        });
        mObjectAnimator.start();
    }

    private void stopScan(){
        mBleManager.stopScan(this);
        mObjectAnimator.cancel();
    }


    private void setKey(String mac){
        String key = "minewtech1234567";
        mBleManager.setSecretKey(mac,key);
    }
    private void connectedSensor(){
        WaitDialog.show(R.string.loading);
        mBleManager.connect(this,mst03Entity);
    }
}
