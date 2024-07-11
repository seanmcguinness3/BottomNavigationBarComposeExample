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
import kotlin.math.abs

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

    var data1527 = byteArrayOf(0)
    var data1528 = byteArrayOf(0)
    var data1527Recieved = false
    var data1528Recieved = false
    var data1527Time = 0L
    var data1528Time = 0L

    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        val uuidString = characteristic!!.uuid.toString()
        val rawDataString = characteristic.value

        //BUILD RAW DATA STRING
        var logString = ""
        var secondToLastIdx = 0
        for(i in 0..(rawDataString.size-2)){
            logString += "${rawDataString[i]}, "
            secondToLastIdx = i
        }
        val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
        logString += "${rawDataString[secondToLastIdx+1]}\n"
        Log.d(TAG, "$adjustedPhoneTimeStamp, Data: $logString")
        val file = File("${getSaveFolder().absolutePath}/${uuidString}.txt")
        file.appendText("$adjustedPhoneTimeStamp, $logString")

        //STATE MACHINE FOR RAW DATA CONVERSION
        if (uuidString.contains("1527")){
            data1527 = rawDataString
            data1527Recieved = true
            data1527Time = System.currentTimeMillis()
        }
        if (uuidString.contains("1528")){
            data1528 = rawDataString
            data1528Recieved = true
            data1528Time = System.currentTimeMillis()
        }
        if (data1527Recieved && data1528Recieved) {
            val dataTimeDiff = abs(data1527Time - data1528Time)
            if (dataTimeDiff in 1..99) { //if the two data points came in within 100ms of each other, then they can be used for the conversion
                val convertedVoData = convertRawVoData(rawDataString)
                generateAndAppend("VO2Data.txt",adjustedPhoneTimeStamp.toString() + "%.4f".format(convertedVoData) + "\n", "Timestamp, VO2_mL/min \n")
            }
            data1527Recieved = false
            data1528Recieved = false
        }

    }

    private fun convertRawVoData(rawData: ByteArray): Double {

        val veMlRawUnsigned = data1527[5].toUByte()
        val eqO2RawUnsigned = data1528[0].toUByte()
        val veMl = veMlRawUnsigned.toFloat() * 2.6425
        val eqO2 = eqO2RawUnsigned.toFloat()/11.0 + 11
        val vO2 = (veMl/eqO2) * 828.9037
        Log.d("DD", "Calculated VO2: ${vO2}")
        return vO2
    }

}

