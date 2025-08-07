package choruscoldchain.app;

import android.util.Log;
import com.minew.ble.mst03.bean.MST03Entity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DeviceDiscoveryManager {
    private static final String TAG = "DeviceDiscoveryManager";
    private static DeviceDiscoveryManager instance;

    private final CopyOnWriteArrayList<MST03Entity> discoveredDevices = new CopyOnWriteArrayList<>();
    private final List<OnDevicesUpdatedListener> listeners = new ArrayList<>();

    public interface OnDevicesUpdatedListener {
        void onDevicesUpdated(List<MST03Entity> devices);
    }

    private DeviceDiscoveryManager() {
    }

    public static synchronized DeviceDiscoveryManager getInstance() {
        if (instance == null) {
            instance = new DeviceDiscoveryManager();
        }
        return instance;
    }

    public void addListener(OnDevicesUpdatedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);

            if (!discoveredDevices.isEmpty()) {
                listener.onDevicesUpdated(new ArrayList<>(discoveredDevices));
            }
        }
    }

    public void removeListener(OnDevicesUpdatedListener listener) {
        listeners.remove(listener);
    }

    public void updateDevices(List<MST03Entity> newDevices) {
        Log.d(TAG, "Updating devices: " + newDevices.size() + " new devices");

        for (MST03Entity newDevice : newDevices) {

            com.minew.ble.mst03.frames.CombinationFrame comboFrame = (com.minew.ble.mst03.frames.CombinationFrame) newDevice
                    .getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            if (comboFrame != null) {
                Log.d(TAG, "[FrameLog] CombinationFrame available for " + newDevice.getMacAddress() +
                        " - Temp: " + comboFrame.getTemperature() + "°C");
            } else {
                Log.d(TAG, "[FrameLog] CombinationFrame not yet available for " + newDevice.getMacAddress());
            }

            boolean found = false;
            for (int i = 0; i < discoveredDevices.size(); i++) {
                MST03Entity existingDevice = discoveredDevices.get(i);
                if (existingDevice.getMacAddress().equals(newDevice.getMacAddress())) {

                    discoveredDevices.set(i, newDevice);
                    found = true;
                    break;
                }
            }
            if (!found) {
                discoveredDevices.add(newDevice);
            }
        }

        List<MST03Entity> sortedDevices = new ArrayList<>(discoveredDevices);
        sortedDevices.sort(new Comparator<MST03Entity>() {
            @Override
            public int compare(MST03Entity o1, MST03Entity o2) {
                return o2.getRssi() - o1.getRssi();
            }
        });

        discoveredDevices.clear();
        discoveredDevices.addAll(sortedDevices);

        Log.d(TAG, "Total devices after update: " + discoveredDevices.size());

        notifyListeners();
    }

    public List<MST03Entity> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }

    public void clearDevices() {
        discoveredDevices.clear();
        notifyListeners();
    }

    private void notifyListeners() {
        List<MST03Entity> devicesCopy = new ArrayList<>(discoveredDevices);
        for (OnDevicesUpdatedListener listener : listeners) {
            try {
                listener.onDevicesUpdated(devicesCopy);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener: " + e.getMessage());
            }
        }
    }
}