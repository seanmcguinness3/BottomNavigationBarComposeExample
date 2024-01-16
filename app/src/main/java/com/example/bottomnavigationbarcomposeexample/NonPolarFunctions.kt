package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.logging.Handler

private const val TAG = "TAG"

// THESE ARE FOR THE V02 MAX SENSOR
private const val VO2_SERVICE_UUID = "00001523-1212-EFDE-1523-785FEABCD123"
private const val CHAR_FOR_NOTIFY_UUID = "00001527-1212-EFDE-1523-785FEABCD123"
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

@SuppressLint("MissingPermission")
fun subscribeToVOMaster(device: BluetoothDevice, context: Context){
    device.connectGatt(context,false,gattCallback, BluetoothDevice.TRANSPORT_LE)
}

@SuppressLint("MissingPermission")
private fun subscribeToNotifications(
    characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt
){
    val cccDUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
    characteristic.getDescriptor(cccDUuid)?.let { cccDescriptor ->
        if (!gatt.setCharacteristicNotification(characteristic, true)){
            Log.d(TAG, "subscribeToNotifications failed for ${characteristic.uuid}")
            return
        }
        generateNewFile("${characteristic.uuid}.txt")
        cccDescriptor.value= BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(cccDescriptor)
    }
}


private val gattCallback = object :BluetoothGattCallback(){
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        val deviceAddress = gatt!!.device.address
        if (status == BluetoothGatt.GATT_SUCCESS){
            if(newState == BluetoothGatt.STATE_CONNECTED){
                Log.d(TAG,"connected to $deviceAddress")
                android.os.Handler(Looper.getMainLooper()).post {
                    gatt!!.discoverServices()
                }
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
            Log.d(TAG, "disconnected from $deviceAddress")
            gatt.close()
        } else {
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        Log.d(TAG, "onServicesDiscoverd services.count = ${gatt!!.services.size} status = $status")
        if (status == 129){
            Log.d(TAG, "ERROR 129")
            gatt.disconnect()
            return
        }
        val service = gatt.getService(UUID.fromString(VO2_SERVICE_UUID)) ?: run {
            Log.d(TAG,"onServicesDiscovered couldn't find $VO2_SERVICE_UUID")
            gatt.disconnect()
            return
        }
        subscribeToNotifications(service.getCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID)),gatt)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status) //sean implement this when you need to do multiple services from the v02 max sensor
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        val uuidString = characteristic!!.uuid.toString()
        val strValueForDebug = characteristic.value

        var logString = ""
        var secondToLastIdx = 0
        for(i in 0..(strValueForDebug.size-2)){
            logString += "${strValueForDebug[i]}, "
            secondToLastIdx = i
        }
        logString += "${strValueForDebug[secondToLastIdx+1]}\n"
        Log.d(TAG, "$uuidString Data: $logString")
        val file = File("${getSaveFolder().absolutePath}/${uuidString}.txt")
        file.appendText(logString)
    }
}
