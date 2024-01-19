package com.example.bottomnavigationbarcomposeexample

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.RadioGroup
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Calendar
import java.util.Date
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.util.EnumMap
import java.util.UUID

private const val TAG = "IDK"
private var saveToLogFiles = false

private var dcDisposable: Disposable? = null
private var ecgDisposable: Disposable? = null
private var accDisposable: Disposable? = null
private var gyrDisposable: Disposable? = null
private var magDisposable: Disposable? = null
private var ppgDisposable: Disposable? = null//LOOK INTO NOT NEEDING THESE

fun getPolarDeviceIDFromName(name: String): String {
    return name.takeLast(8)  //Consider improving, especially if other polar device ID's are diff
}

private lateinit var hRFileName: File
private lateinit var aCCFileName: File
private lateinit var gYRFileName: File
private lateinit var mAGFileName: File
private lateinit var pPGFileName: File

fun saveToLogFiles(saveToFiles: Boolean) {
    saveToLogFiles = saveToFiles
}

fun subscribeToAllPolarData(deviceIdArray: List<String>, api: PolarBleApi, printLogCat: Boolean) {
    val isDisposed = dcDisposable?.isDisposed ?: true
    if (isDisposed) {

        for (deviceId in deviceIdArray) {
            if (deviceId == emptyPolarIDListString) {
                return //If there's no polar devices, don't run any of this
            }
            setTimeStamp(deviceId, api)
            Log.d(TAG, deviceId)
            //subscribeToPolarHR(deviceId, api, printLogCat)
            subscribeToPolarACC(deviceId, api, printLogCat)
            //subscribeToPolarGYR(deviceId, api, printLogCat)
            //subscribeToPolarMAG(deviceId, api, printLogCat)
            //subscribeToPolarPPG(deviceId, api, printLogCat)


            hRFileName = generateNewFile("$deviceId-HRData.txt")
            aCCFileName = generateNewFile("$deviceId-ACCData.txt")
            gYRFileName = generateNewFile("$deviceId-GYRData.txt")
            mAGFileName = generateNewFile("$deviceId-MAGData.txt")
            pPGFileName = generateNewFile("$deviceId-PPGData.txt")

            hRFileName.appendText("Phone timestamp;HR [bpm] \n")
            aCCFileName.appendText("Phone timestamp;sensor timestamp [ns];X [mg];Y [mg];Z [mg] \n")
            gYRFileName.appendText("Phone timestamp;sensor timestamp [ns];X [dps];Y [dps];Z [dps] \n")
            mAGFileName.appendText("Phone timestamp;sensor timestamp [ns];X [G];Y [G];Z [G] \n")
            pPGFileName.appendText("Phone timestamp;sensor timestamp [ns];channel 0;channel 1;channel 2;ambient \n")

        }

    } else {
        dcDisposable?.dispose()
    }
}

private fun setTimeStamp(deviceIDforFunc: String, api: PolarBleApi) {
    val rightNow = Calendar.getInstance()
    rightNow.time = Date()
    api.setLocalTime(deviceIDforFunc, rightNow)
        .observeOn(Schedulers.io())
        //.observeOn(AndroidSchedulers.mainThread())
        .subscribe({
            val timeSetString = "time ${rightNow.time} set to device"
            Log.d("", timeSetString)
        },
            { error: Throwable -> Log.e("", "set time failed: $error") }
        )
    Log.d("", "Trying to set time stamp at $rightNow")
}

private fun subscribeToPolarHR(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {
    var newDisposable: Disposable = //LOOK INTO NOT NEEDING THIS
        api.startHrStreaming(deviceIDforFunc).observeOn(Schedulers.io())
            .subscribe({ hrData: PolarHrData ->
                for (sample in hrData.samples) {
                    val logString =
                        "$deviceIDforFunc HR bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}"
                    Log.d(TAG, logString) //printing no matter what
                    val fileString = "${System.currentTimeMillis()};${sample.hr} \n"
                    val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-HRData.txt")
                    if (saveToLogFiles) {
                        file.appendText(fileString)
                    }
                }
            }, { error: Throwable ->
                Log.e(TAG, "HR stream failed. Reason $error")
            }, { Log.d(TAG, "HR stream complete") })
}

private fun subscribeToPolarACC(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {
    val accSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    accSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
    accSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
    accSettingsMap[PolarSensorSetting.SettingType.RANGE] = 8
    accSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
    val accSettings = PolarSensorSetting(accSettingsMap)
    accDisposable = api.startAccStreaming(deviceIDforFunc, accSettings)
        .observeOn(Schedulers.io())
        .subscribe({ accData: PolarAccelerometerData ->
            for (data in accData.samples) {
                val logString =
                    "$deviceIDforFunc ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-ACCData.txt")
                val fileString =
                    "${System.currentTimeMillis()};${data.timeStamp};${data.x};${data.y};${data.z}; \n"
                if (saveToLogFiles) {
                    file.appendText(fileString)
                }
                if (printLogCat) {
                    Log.d(TAG, logString)
                }
            }
        }, { error: Throwable ->
            Log.e(TAG, "Acc stream failed because $error")
        }, { Log.d(TAG, "acc stream complete") })
}

private fun subscribeToPolarGYR(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {
    val gyrSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    gyrSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
    gyrSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
    gyrSettingsMap[PolarSensorSetting.SettingType.RANGE] = 2000
    gyrSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
    val gyrSettings = PolarSensorSetting(gyrSettingsMap)
    gyrDisposable =
        api.startGyroStreaming(deviceIDforFunc, gyrSettings).observeOn(Schedulers.io())
            .subscribe({ gyrData: PolarGyroData ->
                for (data in gyrData.samples) {
                    val logString =
                        "$deviceIDforFunc GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                    if (printLogCat) {
                        Log.d(TAG, logString)
                    }
                    val fileString =
                        "${System.currentTimeMillis()};${data.timeStamp};${data.x};${data.y};${data.z} \n"
                    val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-GYRData.txt")
                    if (saveToLogFiles) {
                        file.appendText(fileString)
                    }
                }
            }, { error: Throwable ->
                Log.e(TAG, "GYR stream failed. Reason $error")
            }, { Log.d(TAG, "GYR stream complete") })
}

private fun subscribeToPolarMAG(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {
    val magSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    magSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 20
    magSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
    magSettingsMap[PolarSensorSetting.SettingType.RANGE] = 50
    magSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
    val magSettings = PolarSensorSetting(magSettingsMap)
    magDisposable = api.startMagnetometerStreaming(deviceIDforFunc, magSettings)
        .observeOn(Schedulers.io())
        .subscribe({ polarMagData: PolarMagnetometerData ->
            for (data in polarMagData.samples) {
                val logString =
                    "$deviceIDforFunc MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                if (printLogCat) {
                    Log.d(TAG, logString)
                }
                val fileString =
                    "${System.currentTimeMillis()};${data.timeStamp};${data.x};${data.y};${data.z} \n"
                val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-MAGData.txt")
                if (saveToLogFiles) {
                    file.appendText(fileString)
                }
            }
        }, { error: Throwable ->
            Log.e(TAG, "MAGNETOMETER stream failed. Reason $error")
        }, { Log.d(TAG, "MAGNETOMETER stream complete") })
}

private fun subscribeToPolarPPG(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {

    //SEAN REFACTOR This is an example of how you can get the available device settings.
    //If you run into a situation where you are considering hardcoding settings based on device ID's
    //do this instead (this might happen for the H10 heart rate monitor b/c it has different sensors)
    val ppgAvailableSettingsSingle =
        api.requestStreamSettings(deviceIDforFunc, PolarBleApi.PolarDeviceDataType.PPG)
    val ppgAvailableSampleRateSetting =
        ppgAvailableSettingsSingle.blockingGet().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]

    val ppgSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    ppgSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] =
        ppgAvailableSampleRateSetting!!.toIntArray()[0] //if you had multiple options, you could probably pull the
    //max value from this here int array, that'll get you the highest sample rate
    //sensors appear to have different sample rates for ppg.
    //only one sample rate is availible when sdk mode is turned off. I haven't messed with sdk mode yet, so hoping to keep it off for now.
    ppgSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 22
    ppgSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 4
    val ppgSettings = PolarSensorSetting(ppgSettingsMap)
    ppgDisposable =
        api.startPpgStreaming(deviceIDforFunc, ppgSettings)
            .observeOn(Schedulers.io())
            .subscribe({ polarPpgData: PolarPpgData ->
                if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                    for (data in polarPpgData.samples) {
                        val logString =
                            "$deviceIDforFunc PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}"
                        if (printLogCat) {
                            Log.d(TAG, logString)
                        }
                        val fileString =
                            "${System.currentTimeMillis()};${data.timeStamp};${data.channelSamples[0]};${data.channelSamples[1]};${data.channelSamples[2]};${data.channelSamples[3]} \n"
                        val file =
                            File("${getSaveFolder().absolutePath}/$deviceIDforFunc-PPGData.txt")
                        if (saveToLogFiles) {
                            file.appendText(fileString)
                        }
                    }
                }
            }, { error: Throwable ->
                Log.e(TAG, "PPG stream failed. Reason $error")
            }, { Log.d(TAG, "PPG stream complete") })
}

