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

fun getPolarDeviceIDFromName(name: String): String {
    return name.takeLast(8)  //Consider improving, especially if other polar device ID's are diff
}

var firstTimeStamps: MutableList<FirstTimeStamps> = ArrayList()

fun saveToLogFiles(saveToFiles: Boolean) {
    saveToLogFiles = saveToFiles //this actuall doesn't work I don't think b/c it's called in a subscribe which may or may not get an updated version of thsi variable
    //it's kinda a dumb variable anyway so get rid of it if you have time/think of something better
}
fun subscribeToAllPolarDataSingle(deviceId: String) {
    val isDisposed = dcDisposable?.isDisposed ?: true
    if (isDisposed) {
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

    } else {
        dcDisposable?.dispose()
    }
}

suspend fun subscribeToAllPolarData(deviceIdArray: List<String>) {
//fun subscribeToAllPolarData(deviceIdArray: List<String>) {
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
            //This seems like it works but it actually stops logging
            //going to re-implement the old version and just hardcode the settings
            //if it's still a problem, search for a real reactivX solution
            //I think requesting the settings stops the streams. not sure.
            //Log.d("","About to sleep")
            //Thread.sleep(5000)
            //delay(10000) //this might be about as good as it gets without using a reactive programming setup
            //Log.d("","Just Woke")
        }

    } else {
        dcDisposable?.dispose()
    }
}


fun getDeviceType(deviceId: String): String { //I marked the physical sensors with letters corresponding to their type
    return if (deviceId == "C19E1A21") {
        "Head"
    } else if (deviceId == "C929ED29") {
        "Wrist"
    } else if (deviceId == "C929A121") {
        "Ankle"
    } else if (deviceId == "CA98A82D") {
        "Chest"
    } else {
        "Unknown"
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
    val isDisposed = hrDisposable?.isDisposed ?: true
    if (isDisposed) {
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
    } else {
        // NOTE dispose will stop streaming if it is "running"
        hrDisposable?.dispose()
    }
}

private fun subscribeToPolarACC(deviceId: String) {
    val accSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    accSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
    accSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
    accSettingsMap[PolarSensorSetting.SettingType.RANGE] = 8
    accSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
    val accSettings = PolarSensorSetting(accSettingsMap)
    val header = "Phone timestamp;sensor timestamp [ns];X [mg];Y [mg];Z [mg] \n"
    val isDisposed = accDisposable?.isDisposed ?: true
    if (isDisposed) {
        accDisposable = api.startAccStreaming(deviceId,accSettings)
            .observeOn(Schedulers.io())
            .subscribe(
                { polarAccelerometerData: PolarAccelerometerData ->
                    val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                    for (data in polarAccelerometerData.samples) {
                        if (firstTimeStamps[deviceIdx].sensorTimeStamp == 0L) {      //if the first timestamp hasn't been set (still zero) then set it
                            val elapsedTime = System.currentTimeMillis() - firstPhoneTimeStamp //use elapsed time to account for time diff in sensor connection
                            firstTimeStamps[deviceIdx].sensorTimeStamp = (data.timeStamp - (elapsedTime * 1e6)).toLong()
                            Log.d("", "Elapsed time for $deviceId: $elapsedTime. time stamp index: $deviceIdx")
                        }
                        val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                        val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                        val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.x};${data.y};${data.z}; \n"
                        if (saveToLogFiles) { generateAndAppend("$deviceId-ACCData.txt", fileString, header, getDeviceType(deviceId))
                        }
                        //Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
                    }
                    Log.d("","ACC for $deviceId, ${getDeviceType(deviceId)} is running")
                },
                { error: Throwable ->
                    Log.e(TAG, "ACC stream failed. Reason $error")
                },
                {
                    Log.d(TAG, "ACC stream complete")
                }
            )
    } else {
        // NOTE dispose will stop streaming if it is "running"
        accDisposable?.dispose()
    }
}

private fun subscribeToPolarGYR(deviceId: String) {
    val gyrSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    gyrSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
    gyrSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
    gyrSettingsMap[PolarSensorSetting.SettingType.RANGE] = 2000
    gyrSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
    val gyrSettings = PolarSensorSetting(gyrSettingsMap)
    val header = "Phone timestamp;sensor timestamp [ns];X [dps];Y [dps];Z [dps] \n"
    val isDisposed = gyrDisposable?.isDisposed ?: true
    if (isDisposed) {
        gyrDisposable = api.startGyroStreaming(deviceId, gyrSettings)
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
    } else {
        // NOTE dispose will stop streaming if it is "running"
        gyrDisposable?.dispose()
    }

}

private fun subscribeToPolarMAG(deviceId: String) {
    val magSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    magSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 20
    magSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
    magSettingsMap[PolarSensorSetting.SettingType.RANGE] = 50
    magSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
    val magSettings = PolarSensorSetting(magSettingsMap)
    val header = "Phone timestamp;sensor timestamp [ns];X [G];Y [G];Z [G] \n"
    val isDisposed = magDisposable?.isDisposed ?: true
    if (isDisposed) {
        magDisposable = api.startMagnetometerStreaming(deviceId, magSettings)
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
    } else {
        // NOTE dispose will stop streaming if it is "running"
        magDisposable!!.dispose()
    }
}

private fun subscribeToPolarPPG(deviceId: String) {
    val ppgSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    if (getDeviceType(deviceId) == "Head"){
        ppgSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 176
    } else {
        ppgSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 176
    }
    ppgSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 22
    ppgSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 4
    val magSettings = PolarSensorSetting(ppgSettingsMap)
    val header = "Phone timestamp;sensor timestamp [ns];channel 0;channel 1;channel 2;ambient \n"
    val isDisposed = ppgDisposable?.isDisposed ?: true
    if (isDisposed) {
        ppgDisposable = api.startPpgStreaming(deviceId, settings)
                //.observeOn(Schedulers.io())
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
    } else {
        // NOTE dispose will stop streaming if it is "running"
        ppgDisposable?.dispose()
    }
}

private fun subscribeToPolarECG(deviceId: String) {
    val ecgSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
        EnumMap(PolarSensorSetting.SettingType::class.java)
    ecgSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 130
    ecgSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 14
    val ecgSettings = PolarSensorSetting(ecgSettingsMap)
    val header = "Phone timestamp;sensor timestamp [ns];voltage \n"
    val isDisposed = ecgDisposable?.isDisposed ?: true
    if (isDisposed) {
        ecgDisposable = api.startEcgStreaming(deviceId, ecgSettings)
            //.observeOn(Schedulers.io())
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
    } else {
        // NOTE stops streaming if it is "running"
        ecgDisposable?.dispose()
    }
}

fun requestStreamSettings(
    identifier: String,
    feature: PolarBleApi.PolarDeviceDataType
): Flowable<PolarSensorSetting> {
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
            Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
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

fun setSettingToMax(
    selected: MutableMap<PolarSensorSetting.SettingType, Int>,
    type: PolarSensorSetting.SettingType,
    availibleSettings: Map<PolarSensorSetting.SettingType, Set<Int>>
) {
    var maxValue = 0
    val availibleValuesForType = availibleSettings[type]?.toList()
    if (availibleValuesForType != null) {
        for (i in availibleValuesForType.indices) {
            if (availibleValuesForType[i] > maxValue) {
                maxValue = availibleValuesForType[i]
                Log.d("", "Found value ${availibleValuesForType[i]} for setting $type")
            }
        }
    }
    selected[type] = maxValue
    Log.d("", "Setting $type was autoset to $maxValue")
}
