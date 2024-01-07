package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.content.Context

@SuppressLint("MissingPermission")
fun subscribeToVOMaster(device: BluetoothDevice, context: Context){
    device.connectGatt(context,false,gattCallback, BluetoothDevice.TRANSPORT_LE)
}

private val gattCallback = object :BluetoothGattCallback(){

}
