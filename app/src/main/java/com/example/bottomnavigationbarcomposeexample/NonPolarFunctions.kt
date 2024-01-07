package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.os.Looper
import java.util.logging.Handler

@SuppressLint("MissingPermission")
fun subscribeToVOMaster(device: BluetoothDevice, context: Context){
    device.connectGatt(context,false,gattCallback, BluetoothDevice.TRANSPORT_LE)
}

private val gattCallback = object :BluetoothGattCallback(){
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS){
            if(newState == BluetoothGatt.STATE_CONNECTED){
                android.os.Handler(Looper.getMainLooper()).post {
                    gatt.discoverServices()
                }
            }
        }
    }


}
