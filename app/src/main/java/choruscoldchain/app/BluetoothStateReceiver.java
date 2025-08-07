package choruscoldchain.app;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BluetoothStateReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothStateReceiver";
    private BluetoothStateListener listener;

    public interface BluetoothStateListener {
        void onBluetoothTurnedOff();
        void onBluetoothTurnedOn();
    }

    public BluetoothStateReceiver() {
        // Default constructor for manifest registration (not used in our implementation)
        Log.d(TAG, "BluetoothStateReceiver initialized with default constructor");
    }

    public BluetoothStateReceiver(BluetoothStateListener listener) {
        this.listener = listener;
        Log.d(TAG, "BluetoothStateReceiver initialized with listener: " + (listener != null));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    Log.d(TAG, "Bluetooth turned OFF - notifying listener");
                    if (listener != null) {
                        try {
                            listener.onBluetoothTurnedOff();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in onBluetoothTurnedOff callback: " + e.getMessage(), e);
                        }
                    } else {
                        Log.w(TAG, "Bluetooth listener is null");
                    }
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.d(TAG, "Bluetooth turned ON - notifying listener");
                    if (listener != null) {
                        try {
                            listener.onBluetoothTurnedOn();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in onBluetoothTurnedOn callback: " + e.getMessage(), e);
                        }
                    } else {
                        Log.w(TAG, "Bluetooth listener is null");
                    }
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.d(TAG, "Bluetooth turning OFF");
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.d(TAG, "Bluetooth turning ON");
                    break;
                default:
                    Log.d(TAG, "Bluetooth state: " + state);
                    break;
            }
        }
    }
} 