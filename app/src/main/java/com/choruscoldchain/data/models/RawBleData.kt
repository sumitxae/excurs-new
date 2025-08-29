package com.choruscoldchain.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RawBleData(
    val macAddress: String,
    val hexPayload: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable


