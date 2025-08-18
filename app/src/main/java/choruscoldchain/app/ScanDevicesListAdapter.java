package choruscoldchain.app;

import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.minew.ble.mst03.bean.MST03Entity;
import com.minew.ble.mst03.frames.CombinationFrame;
import com.minew.ble.mst03.frames.DeviceStaticInfoFrame;
import com.minew.ble.mst03.frames.StringFrame;
import com.minew.ble.v3.enums.FrameType;

import java.util.List;

public class ScanDevicesListAdapter extends BaseQuickAdapter<MST03Entity, BaseViewHolder> {

    public interface OnConnectClickListener {
        void onConnectClick(MST03Entity device);
    }
    
    private OnConnectClickListener onConnectClickListener;
    
    public void setOnConnectClickListener(OnConnectClickListener listener) {
        
        
        this.onConnectClickListener = listener;
        
        
    }
    
    private boolean connectButtonsEnabled = true;
    
    public void setConnectButtonsEnabled(boolean enabled) {
        
        this.connectButtonsEnabled = enabled;
        notifyDataSetChanged();
    }

    public ScanDevicesListAdapter(int layoutResId, List<MST03Entity> data) {
        super(layoutResId,data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, MST03Entity mst03Entity)
    {
        DeviceStaticInfoFrame deviceStaticInfoFrame =null;
        CombinationFrame combinationFrame = null;
        StringFrame stringFrame = null;
        if(mst03Entity.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME)!=null){
            deviceStaticInfoFrame = (DeviceStaticInfoFrame) mst03Entity.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME);
            
            
            Log.d("Scan", "=== Device Static Info for " + mst03Entity.getMacAddress() + " ===");
            Log.d("Scan", "Battery Level: " + deviceStaticInfoFrame.getBattery() + "%");
            Log.d("Scan", "MAC Address: " + deviceStaticInfoFrame.getMacAddress());
            Log.d("Scan", "Firmware Version: " + deviceStaticInfoFrame.getFirmwareVersion());
            Log.d("Scan", "Frame Version: " + deviceStaticInfoFrame.getFrameVersion());
            Log.d("Scan", "Device Name: " + mst03Entity.getName());
            Log.d("Scan", "RSSI: " + mst03Entity.getRssi());
            Log.d("Scan", "==========================================");
        } else {
            Log.d("Scan", "No DeviceStaticInfoFrame available for " + mst03Entity.getMacAddress());
        }

        if(mst03Entity.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME)!=null){
            combinationFrame = (CombinationFrame) mst03Entity.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME);
            Log.d("Scan", "=== Combination Frame for " + mst03Entity.getMacAddress() + " ===");
            Log.d("Scan", "Temperature: " + combinationFrame.getTemperature() + "°C");
            Log.d("Scan", "Battery: " + combinationFrame.getBattery() + "%");
            Log.d("Scan", "Current Timestamp: " + combinationFrame.getCurrentTimestamp());
            Log.d("Scan", "Temp Event Timestamp: " + combinationFrame.getTempEventTimestamp());
            Log.d("Scan", "Frame Version: " + combinationFrame.getFrameVersion());
            Log.d("Scan", "Company ID: " + combinationFrame.getCompanyId());
            Log.d("Scan", "Google ID: " + combinationFrame.getGoogleId());
            Log.d("Scan", "==========================================");
        } else {
            Log.d("Scan", "No CombinationFrame available for " + mst03Entity.getMacAddress());
            // Try to log what frames are available
            Log.d("Scan", "Available frames for " + mst03Entity.getMacAddress() + ":");
            for (com.minew.ble.v3.enums.FrameType frameType : com.minew.ble.v3.enums.FrameType.values()) {
                if (mst03Entity.getMinewFrame(frameType) != null) {
                    Log.d("Scan", "  - " + frameType.name() + ": " + mst03Entity.getMinewFrame(frameType).toString());
                }
            }
        }
        if(mst03Entity.getMinewFrame(FrameType.STRING_FRAME)!=null){
            stringFrame = (StringFrame) mst03Entity.getMinewFrame(FrameType.STRING_FRAME);
        }
        
        baseViewHolder.setText(R.id.tv_scan_device_name, mst03Entity.getMacAddress());
        
        
        float tempValue = Float.NaN;
        
        
        if (combinationFrame != null) {
            tempValue = combinationFrame.getTemperature();
            Log.d("Scan", "Temperature from CombinationFrame: " + tempValue + "°C");
        }
        
        // If no valid temperature from device, try to get from cache
        if (Float.isNaN(tempValue) || tempValue == 0.0f) {
            // Try to get cached temperature from ScanDevicesListActivity
            if (getContext() instanceof ScanDevicesListActivity) {
                ScanDevicesListActivity activity = (ScanDevicesListActivity) getContext();
                String macAddress = mst03Entity.getMacAddress(); // Assuming macAddress is available here
                Float cachedTemp = activity.getCachedTemperature(macAddress);
                if (cachedTemp != null) {
                    tempValue = cachedTemp;
                    Log.d("Scan", "Using cached temperature for " + macAddress + ": " + tempValue + "°C");
                }
            }
        }
        
        
        if (tempValue == 0.0f) {
            Log.d("Scan", "Warning: Temperature is 0°C for device: " + mst03Entity.getMacAddress());
        }
        
        
        TextView statusBadge = baseViewHolder.getView(R.id.tv_status_badge);
        if (!Float.isNaN(tempValue) && tempValue != 0.0f && (tempValue > 8.0f || tempValue < 2.0f)) {
            statusBadge.setText("ALERT");
            statusBadge.setTextColor(Color.parseColor("#FF4B4B"));
            statusBadge.setBackgroundResource(R.drawable.bg_status_badge_alert);
        } else {
            statusBadge.setText("NORMAL");
            statusBadge.setTextColor(Color.parseColor("#1BC47D"));
            statusBadge.setBackgroundResource(R.drawable.bg_status_badge_normal);
        }
        
        
        baseViewHolder.setText(R.id.tv_sensor_type, "Temperature Sensor");
        
        
        TextView tempText = baseViewHolder.getView(R.id.tv_scan_device_ht);
        
        
        if (!Float.isNaN(tempValue) && tempValue != 0.0f) {
            tempText.setText(String.format("%.1f°C", tempValue));
            if (tempValue > 8.0f || tempValue < 2.0f) {
                tempText.setTextColor(tempText.getContext().getResources().getColor(R.color.error));
            } else {
                tempText.setTextColor(tempText.getContext().getResources().getColor(R.color.primary));
            }
        } else {
            tempText.setText("--°C");
            tempText.setTextColor(tempText.getContext().getResources().getColor(R.color.text_secondary));
        }

        
        View cardView = baseViewHolder.itemView;
        if (cardView instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) cardView;
            if (combinationFrame != null && (tempValue > 8.0f || tempValue < 2.0f)) {
                // Excursion cards - orange shadow
                card.setCardBackgroundColor(card.getContext().getResources().getColor(R.color.card_background));
                card.setCardElevation(12f); 
                card.setOutlineSpotShadowColor(card.getContext().getResources().getColor(R.color.primary));
            } else {
                // Normal temperature cards - blue shadow
                card.setCardBackgroundColor(card.getContext().getResources().getColor(R.color.card_background));
                card.setCardElevation(8f); // Slightly higher elevation for normal cards
                card.setOutlineSpotShadowColor(card.getContext().getResources().getColor(R.color.normal_temp_shadow));
            }
        }
        
        android.widget.Button btnConnect = baseViewHolder.getView(R.id.btn_connect);
        btnConnect.setText("Connect");
        boolean showConnect = false;
        if (!Float.isNaN(tempValue) && tempValue != 0.0f) {
            // Only show if outside normal range
            if (tempValue < 2.0f || tempValue > 8.0f) {
                showConnect = true;
            }
        }
        btnConnect.setVisibility(showConnect && connectButtonsEnabled ? View.VISIBLE : View.GONE);
        btnConnect.setOnClickListener(null);
        if (showConnect && connectButtonsEnabled && onConnectClickListener != null) {
            btnConnect.setOnClickListener(v -> onConnectClickListener.onConnectClick(mst03Entity));
        }
        
        
    }


}
