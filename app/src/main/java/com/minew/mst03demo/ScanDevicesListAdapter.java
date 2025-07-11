package com.minew.mst03demo;

import android.text.TextUtils;
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


    public ScanDevicesListAdapter(int layoutResId, List<MST03Entity> data) {
        super(layoutResId,data);

    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, MST03Entity mst03Entity)
    {
        DeviceStaticInfoFrame deviceStaticInfoFrame =null;
        CombinationFrame combinationFrame = null;
        StringFrame stringFrame = null;
        if(mst03Entity.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME)!=null){
            deviceStaticInfoFrame = (DeviceStaticInfoFrame) mst03Entity.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
        }

        if(mst03Entity.getMinewFrame(FrameType.COMBINATION_FRAME)!=null){
            combinationFrame = (CombinationFrame) mst03Entity.getMinewFrame(FrameType.COMBINATION_FRAME);
        }
        if(mst03Entity.getMinewFrame(FrameType.STRING_FRAME)!=null){
            stringFrame = (StringFrame) mst03Entity.getMinewFrame(FrameType.STRING_FRAME);
        }
        // Device ID
        if(stringFrame!=null && !TextUtils.isEmpty(stringFrame.getDeviceName())){
            baseViewHolder.setText(R.id.tv_scan_device_name, stringFrame.getDeviceName());
        }else{
            baseViewHolder.setText(R.id.tv_scan_device_name, mst03Entity.getName());
        }
        // Status badge
        TextView statusBadge = baseViewHolder.getView(R.id.tv_status_badge);
        if (combinationFrame != null && combinationFrame.getTemperature() > 8.0f) {
            statusBadge.setText("ALERT");
            statusBadge.setTextColor(Color.parseColor("#FF4B4B"));
            statusBadge.setBackgroundResource(R.drawable.bg_status_badge_alert);
        } else {
            statusBadge.setText("NORMAL");
            statusBadge.setTextColor(Color.parseColor("#1BC47D"));
            statusBadge.setBackgroundResource(R.drawable.bg_status_badge_normal);
        }
        // Sensor type
        baseViewHolder.setText(R.id.tv_sensor_type, "Temperature Sensor");
        // Set temperature text and color
        TextView tempText = baseViewHolder.getView(R.id.tv_scan_device_ht);
        float tempValue = combinationFrame != null ? combinationFrame.getTemperature() : Float.NaN;
        if (combinationFrame != null) {
            tempText.setText(String.format("%.1f°C", tempValue));
            if (tempValue > 8.0f) {
                tempText.setTextColor(tempText.getContext().getResources().getColor(R.color.error));
            } else {
                tempText.setTextColor(tempText.getContext().getResources().getColor(R.color.primary));
            }
        } else {
            tempText.setText("-");
            tempText.setTextColor(tempText.getContext().getResources().getColor(R.color.text_secondary));
        }

        // Set card shadow color based on excursion
        View cardView = baseViewHolder.itemView;
        if (cardView instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) cardView;
            if (combinationFrame != null && tempValue > 8.0f) {
                card.setCardBackgroundColor(card.getContext().getResources().getColor(R.color.card_background));
                card.setCardElevation(12f); // Increased shadow
                card.setOutlineSpotShadowColor(card.getContext().getResources().getColor(R.color.primary));
            } else {
                card.setCardBackgroundColor(card.getContext().getResources().getColor(R.color.card_background));
                card.setCardElevation(3f);
                card.setOutlineSpotShadowColor(card.getContext().getResources().getColor(R.color.border));
            }
        }
        // No excursion event message
        // Connect button (handled by item click in activity)
    }


}
