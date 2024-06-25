package com.example.bottomnavigationbarcomposeexample

import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.delay
import java.io.File
import java.util.EnumMap

private const val TAG = "IDK"
private var saveToLogFiles = true

private var dcDisposable: Disposable? = null
private var ecgDisposable: Disposable? = null
private var accDisposable: Disposable? = null
private var gyrDisposable: Disposable? = null
private var magDisposable: Disposable? = null
private var ppgDisposable: Disposable? = null
private var hrDisposable: Disposable? = null

data class FirstTimeStamps(val sensorID: String) {
    var sensorTimeStamp: Long = 0L
}

var firstTimeStamps: MutableList<FirstTimeStamps> = ArrayList()

fun getPolarDeviceIDFromName(name: String): String {
    return name.takeLast(8)  //Consider improving, especially if other polar device ID's are diff
}

fun saveToLogFiles(saveToFiles: Boolean) {
    saveToLogFiles = saveToFiles //this actually doesn't work I don't think b/c it's called in a subscribe which may or may not get an updated version of thsi variable
    //it's kinda a dumb variable anyway so get rid of it if you have time/think of something better
}

fun subscribeToAllPolarData(deviceIdArray: List<String>) {
    val isDisposed = dcDisposable?.isDisposed ?: true
    if (isDisposed) {
        for (deviceId in deviceIdArray) {
            if (deviceId == emptyPolarIDListString) {
                Log.d(TAG, "Polar device list was empty")
                return //If there's no polar devices, don't run any of this
            } else {
                Log.d(TAG, "Subscribe to polar device $deviceId called")
            }
            var timeStampInfo = FirstTimeStamps(deviceId)
            firstTimeStamps.add(timeStampInfo)
            val deviceType = getDeviceType(deviceId)
            Log.d("", "Subscribing to $deviceType sensor")
            //Trying to subscribe to unavailable streams was causing unpredictability
            //specifically, trying to subscribe to ECG with the pucks was causing the ACC to fail
            if (deviceType == "Chest") {
                subscribeToPolarHR(deviceId)
                subscribeToPolarACC(deviceId)
                subscribeToPolarECG(deviceId)
            } else {
                subscribeToPolarHR(deviceId)
                subscribeToPolarACC(deviceId)
                subscribeToPolarGYR(deviceId)
                subscribeToPolarMAG(deviceId)
                subscribeToPolarPPG(deviceId)
            }
        }

    } else {
        dcDisposable?.dispose()
    }
}


fun getDeviceType(deviceId: String): String { //I marked the physical sensors with letters corresponding to their type
    return if (deviceId == "C19E1A21") { "Head"
    } else if (deviceId == "C929ED29") { "Wrist"
    } else if (deviceId == "C929A121") { "Ankle"
    } else if (deviceId == "CA98A82D") { "Chest"
    } else { "Unknown"
    }
}

fun getDeviceIndexInTimestampArray(deviceIDforFunc: String): Int {
    var index = -1
    for (i in firstTimeStamps.indices) {
        if (firstTimeStamps[i].sensorID == deviceIDforFunc) {
            index = i
        }
    }
    return index
}

private fun subscribeToPolarHR(deviceId: String) {
    Log.d("", "HR stream for ${getDeviceType(deviceId)} is trying to start ")
    val header = "Phone timestamp;HR [bpm] \n"
        hrDisposable = api.startHrStreaming(deviceId)
            .observeOn(Schedulers.io())
            .subscribe(
                { hrData: PolarHrData ->
                    for (sample in hrData.samples) {
                        val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                        val fileString = "${adjustedPhoneTimeStamp};${sample.hr} \n"
                        if (saveToLogFiles) {
                            generateAndAppend("$deviceId-HRData.txt", fileString, header, getDeviceType(deviceId))
                        }
                        //Log.d(TAG, "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}")
                    }
                },
                { error: Throwable ->
                    Log.e(TAG, "HR stream failed. Reason $error")
                },
                { Log.d(TAG, "HR stream complete") }
            )
}

private fun subscribeToPolarACC(deviceId: String) {
    val header = "Phone timestamp;sensor timestamp [ns];X [mg];Y [mg];Z [mg] \n"
    accDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
        .flatMap { settings: PolarSensorSetting ->
            api.startAccStreaming(deviceId, settings)
        }

            .observeOn(Schedulers.io())
            .subscribe(
                { polarAccelerometerData: PolarAccelerometerData ->
                    val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                    var averagedTimeStampAdjust = 0.0
                    var numberOfSamples = 0
                    var calculateTimestampOffset = false //sean refactor i think you can get rid of this variable, should be easy but don't wanna do it now cause not about to test anything
                    for (data in polarAccelerometerData.samples) {
                        if (firstTimeStamps[deviceIdx].sensorTimeStamp == 0L) {
                            calculateTimestampOffset = true
                        }
                        if (calculateTimestampOffset) {      //if the first timestamp hasn't been set (still zero) then set it
                            val elapsedTime = System.currentTimeMillis() - firstPhoneTimeStamp //use elapsed time to account for time diff in sensor connection
                            averagedTimeStampAdjust += (data.timeStamp - (elapsedTime * 1e6)).toLong() //taking an average of the discrepancy between the phone and sensor time stamps
                            numberOfSamples++
                            Log.d("", "Elapsed time for $deviceId: $elapsedTime. averagedTimeStampAdjust = $averagedTimeStampAdjust, # of samples = $numberOfSamples")
                        }
                        val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                        val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                        val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.x};${data.y};${data.z}; \n"
                        if (saveToLogFiles) { generateAndAppend("$deviceId-ACCData.txt", fileString, header, getDeviceType(deviceId))
                        }
                        //Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
                    }
                    if (firstTimeStamps[deviceIdx].sensorTimeStamp == 0L) { //only want to set this once
                        firstTimeStamps[deviceIdx].sensorTimeStamp = (averagedTimeStampAdjust/numberOfSamples).toLong() //This made a good over the non averaging system
                        //sean you should make this be the trigger for when to actually start saving data, to avoid the garbo timestamp data. But, might require some though to not end up with spaghetti
                    }
                    calculateTimestampOffset = false
                    Log.d("","ACC for $deviceId, ${getDeviceType(deviceId)} is running")
                },
                { error: Throwable ->
                    Log.e(TAG, "ACC stream failed. Reason $error")
                },
                {
                    Log.d(TAG, "ACC stream complete")
                }
            )
}

private fun subscribeToPolarGYR(deviceId: String) {
    val header = "Phone timestamp;sensor timestamp [ns];X [dps];Y [dps];Z [dps] \n"
    gyrDisposable =
        requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.GYRO)
            .flatMap { settings: PolarSensorSetting ->
                api.startGyroStreaming(deviceId, settings)
            }
                .observeOn(Schedulers.io())
                .subscribe(
                    { polarGyroData: PolarGyroData ->
                        val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                        for (data in polarGyroData.samples) {
                            val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                            val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                            val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.x};${data.y};${data.z}; \n"
                            if (saveToLogFiles) { generateAndAppend("$deviceId-GYRData.txt", fileString, header, getDeviceType(deviceId)) }
                            //Log.d(TAG, "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "GYR stream failed. Reason $error")
                    },
                    { Log.d(TAG, "GYR stream complete") }
                )

}

private fun subscribeToPolarMAG(deviceId: String) {
    val header = "Phone timestamp;sensor timestamp [ns];X [G];Y [G];Z [G] \n"
    magDisposable =
        requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER)
            .flatMap { settings: PolarSensorSetting ->
                api.startMagnetometerStreaming(deviceId, settings)
            }
                .observeOn(Schedulers.io())
                .subscribe(
                    { polarMagData: PolarMagnetometerData ->
                        val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                        for (data in polarMagData.samples) {
                            val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                            val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                            val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.x};${data.y};${data.z}; \n"
                            if (saveToLogFiles) { generateAndAppend("$deviceId-MAGData.txt", fileString, header, getDeviceType(deviceId)) }
                            //Log.d(TAG, "MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "MAGNETOMETER stream failed. Reason $error")
                    },
                    { Log.d(TAG, "MAGNETOMETER stream complete") }
                )
}

private fun subscribeToPolarPPG(deviceId: String) {
    val header = "Phone timestamp;sensor timestamp [ns];channel 0;channel 1;channel 2;ambient \n"
    ppgDisposable =
        requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.PPG)
            .flatMap { settings: PolarSensorSetting ->
                api.startPpgStreaming(deviceId, settings)
            }
                .observeOn(Schedulers.io())
                .subscribe(
                    { polarPpgData: PolarPpgData ->
                        val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                        if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                            for (data in polarPpgData.samples) {
                                val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                                val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                                val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.channelSamples[0]};${data.channelSamples[1]};${data.channelSamples[2]};${data.channelSamples[3]} \n"
                                if (saveToLogFiles) { generateAndAppend("$deviceId-PPGData.txt", fileString, header, getDeviceType(deviceId)) }
                            }
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "PPG stream failed. Reason $error")
                    },
                    { Log.d(TAG, "PPG stream complete") }
                )
}

private fun subscribeToPolarECG(deviceId: String) {
    val header = "Phone timestamp;sensor timestamp [ns];voltage \n"
    ecgDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
        .flatMap { settings: PolarSensorSetting ->
            api.startEcgStreaming(deviceId, settings)
        }
            .observeOn(Schedulers.io())
            .subscribe(
                { polarEcgData: PolarEcgData ->
                    val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                    for (data in polarEcgData.samples) {
                        val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                        val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                        val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.voltage}; \n"
                        if (saveToLogFiles) { generateAndAppend("$deviceId-ECGData.txt", fileString, header, getDeviceType(deviceId)) }
                        //Log.d(TAG, "    yV: ${data.voltage} timeStamp: ${data.timeStamp}")
                    }
                },
                { error: Throwable ->
                    Log.e(TAG, "ECG stream failed. Reason $error")
                },
                { Log.d(TAG, "ECG stream complete") }
            )
}

fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
    val availableSettings = api.requestStreamSettings(identifier, feature)
    val allSettings = api.requestFullStreamSettings(identifier, feature)
        .onErrorReturn { error: Throwable ->
            Log.w(
                TAG,
                "Full stream settings are not available for feature $feature. REASON: $error"
            )
            PolarSensorSetting(emptyMap())
        }
    return Single.zip(
        availableSettings,
        allSettings
    ) { available: PolarSensorSetting, all: PolarSensorSetting ->
        if (available.settings.isEmpty()) {
            throw Throwable("Settings are not available")
        } else {
            Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
            //Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
            return@zip android.util.Pair(available, all)
        }
    }
        .observeOn(Schedulers.io())
        .toFlowable()
        .flatMap { sensorSettings: android.util.Pair<PolarSensorSetting, PolarSensorSetting> ->
            setAllSettings(sensorSettings.first.settings).toFlowable()
        }
}

fun setAllSettings(available: Map<PolarSensorSetting.SettingType, Set<Int>>): Single<PolarSensorSetting> {
    return Single.create { e: SingleEmitter<PolarSensorSetting> ->
        val selected: MutableMap<PolarSensorSetting.SettingType, Int> = EnumMap(
            PolarSensorSetting.SettingType::class.java
        )
        setSettingToMax(selected, PolarSensorSetting.SettingType.SAMPLE_RATE, available)
        setSettingToMax(selected, PolarSensorSetting.SettingType.RESOLUTION, available)
        setSettingToMax(selected, PolarSensorSetting.SettingType.RANGE, available)
        setSettingToMax(selected, PolarSensorSetting.SettingType.CHANNELS, available)
        e.onSuccess(PolarSensorSetting(selected))
    }.subscribeOn(Schedulers.io())
}

fun setSettingToMax(selected: MutableMap<PolarSensorSetting.SettingType, Int>, type: PolarSensorSetting.SettingType, availibleSettings: Map<PolarSensorSetting.SettingType, Set<Int>>) {
    var maxValue = 0
    val availibleValuesForType = availibleSettings[type]?.toList()
    if (availibleValuesForType != null) {
        for (i in availibleValuesForType.indices) {
            if (availibleValuesForType[i] > maxValue) {
                maxValue = availibleValuesForType[i]
                //Log.d("", "Found value ${availibleValuesForType[i]} for setting $type")
            }
        }
        selected[type] = maxValue
    }
    Log.d("", "Setting $type was autoset to $maxValue")
}
