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
private const val CHAR_FOR_NOTIFY_UUID_1526 = "00001526-1212-EFDE-1523-785FEABCD123"
private const val CHAR_FOR_NOTIFY_UUID_1527 = "00001527-1212-EFDE-1523-785FEABCD123"
private const val CHAR_FOR_NOTIFY_UUID_1528 = "00001528-1212-EFDE-1523-785FEABCD123"
private const val CHAR_FOR_NOTIFY_UUID_1529 = "00001529-1212-EFDE-1523-785FEABCD123"
private val CHAR_ARRAY_FOR_NOTIFY_UUID = arrayOf(CHAR_FOR_NOTIFY_UUID_1526, CHAR_FOR_NOTIFY_UUID_1527, CHAR_FOR_NOTIFY_UUID_1528, CHAR_FOR_NOTIFY_UUID_1529)
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

private var notifyIterator = 1

@SuppressLint("MissingPermission")
fun subscribeToVOMaster(device: BluetoothDevice, context: Context){
    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
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
        val vo2MasterFile = generateNewFile("${characteristic.uuid}.txt")
        vo2MasterFile.appendText("Phone timestamp; Data \n")
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
        subscribeToNotifications(service.getCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID_1526)),gatt)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status) //implement this when you need to do multiple services from the v02 max sensor
        if (notifyIterator <= 3){
            Log.d("DD","notifyIterator = $notifyIterator, connecting to ${CHAR_ARRAY_FOR_NOTIFY_UUID[notifyIterator]}")
            val service = gatt!!.getService(UUID.fromString(VO2_SERVICE_UUID))
            subscribeToNotifications(service.getCharacteristic(UUID.fromString(CHAR_ARRAY_FOR_NOTIFY_UUID[notifyIterator])),gatt)
            notifyIterator++
        }

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
        val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
        logString += "${strValueForDebug[secondToLastIdx+1]}\n"
        Log.d(TAG, "$adjustedPhoneTimeStamp; Data: $logString")
        val file = File("${getSaveFolder().absolutePath}/${uuidString}.txt")
        file.appendText("$adjustedPhoneTimeStamp; $logString")
    }
}
