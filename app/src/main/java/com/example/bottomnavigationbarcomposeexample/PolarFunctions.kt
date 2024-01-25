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
private var ppgDisposable: Disposable? = null
private var hrDisposable: Disposable? = null

data class FirstTimeStamps(val sensorID: String) {
    var phoneTimeStamp: Long = 0L
    var sensorTimeStamp: Long = 0L
}

fun getPolarDeviceIDFromName(name: String): String {
    return name.takeLast(8)  //Consider improving, especially if other polar device ID's are diff
}

private lateinit var hRFileName: File
private lateinit var aCCFileName: File
private lateinit var gYRFileName: File
private lateinit var mAGFileName: File
private lateinit var pPGFileName: File
private lateinit var eCGFileName: File
var firstTimeStamps: MutableList<FirstTimeStamps> = ArrayList()

fun saveToLogFiles(saveToFiles: Boolean) {
    saveToLogFiles = saveToFiles
}
//Last I looked it was having a hard time connectiong to either the ACC or PPG stream. Maybe low sensor power?
fun subscribeToAllPolarData(deviceIdArray: List<String>, api: PolarBleApi, printLogCat: Boolean) {
    val isDisposed = dcDisposable?.isDisposed ?: true
    if (isDisposed) {
        for (deviceId in deviceIdArray) {
            Log.d(TAG, "Subscribe to polar device $deviceId called")
            if (deviceId == emptyPolarIDListString) {
                Log.d(TAG,"Polar device list was empty")
                return //If there's no polar devices, don't run any of this
            }
            var timeStampInfo = FirstTimeStamps(deviceId)
            firstTimeStamps.add(timeStampInfo)
            subscribeToPolarHR(deviceId)
            subscribeToPolarACC(deviceId)
            subscribeToPolarGYR(deviceId)
            subscribeToPolarMAG(deviceId)
            subscribeToPolarPPG(deviceId)
            subscribeToPolarECG(deviceId)

            hRFileName = generateNewFile("$deviceId-HRData.txt")
            aCCFileName = generateNewFile("$deviceId-ACCData.txt")
            gYRFileName = generateNewFile("$deviceId-GYRData.txt")
            mAGFileName = generateNewFile("$deviceId-MAGData.txt")
            pPGFileName = generateNewFile("$deviceId-PPGData.txt")
            eCGFileName = generateNewFile("$deviceId-ECGData.txt")

            hRFileName.appendText("Phone timestamp;HR [bpm] \n")
            aCCFileName.appendText("Phone timestamp;sensor timestamp [ns];X [mg];Y [mg];Z [mg] \n")
            gYRFileName.appendText("Phone timestamp;sensor timestamp [ns];X [dps];Y [dps];Z [dps] \n")
            mAGFileName.appendText("Phone timestamp;sensor timestamp [ns];X [G];Y [G];Z [G] \n")
            pPGFileName.appendText("Phone timestamp;sensor timestamp [ns];channel 0;channel 1;channel 2;ambient \n")
            eCGFileName.appendText("Phone timestamp;sensor timestamp [ns];voltage \n")

        }

    } else {
        dcDisposable?.dispose()
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

private fun subscribeToPolarHROld(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {

    var newDisposable: Disposable = //LOOK INTO NOT NEEDING THIS
        api.startHrStreaming(deviceIDforFunc)
            .observeOn(Schedulers.io())
            .subscribe({ hrData: PolarHrData ->
                val deviceIdx = getDeviceIndexInTimestampArray(deviceIDforFunc)
                for (sample in hrData.samples) {
                    val adjustedPhoneTimeStamp =
                        System.currentTimeMillis() - firstPhoneTimeStamp
                    val logString =
                        "$deviceIDforFunc HR bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}"
                    Log.d(TAG, logString) //printing no matter what
                    val fileString = "${adjustedPhoneTimeStamp};${sample.hr} \n"
                    val file =
                        File("${getSaveFolder().absolutePath}/$deviceIDforFunc-HRData.txt")
                    if (saveToLogFiles) {
                        file.appendText(fileString)
                    }
                }
            }, { error: Throwable ->
                Log.e(TAG, "HR stream failed. Reason $error")
            }, { Log.d(TAG, "HR stream complete") })
}

private fun subscribeToPolarHR(deviceId: String){
    val isDisposed = hrDisposable?.isDisposed ?: true
    if (isDisposed) {
        hrDisposable = api.startHrStreaming(deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { hrData: PolarHrData ->
                    for (sample in hrData.samples) {
                        Log.d(TAG, "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}")
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


private fun subscribeToPolarACC(deviceId: String){
    val isDisposed = accDisposable?.isDisposed ?: true
    if (isDisposed) {
        accDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
            .flatMap { settings: PolarSensorSetting ->
                api.startAccStreaming(deviceId, settings)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { polarAccelerometerData: PolarAccelerometerData ->
                    for (data in polarAccelerometerData.samples) {
                        Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
                    }
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

private fun subscribeToPolarACCOld(deviceIDforFunc: String, api: PolarBleApi, printLogCat: Boolean) {
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
            val deviceIdx =
                getDeviceIndexInTimestampArray(deviceIDforFunc) //probably look for a refactor here
            //the whole point is to save the first time stamps so I can start at zero
            for (data in accData.samples) {
                if (firstTimeStamps[deviceIdx].sensorTimeStamp == 0L) {      //if the first timestamp hasn't been set (still zero) then set it
                    val elapsedTime = System.currentTimeMillis() - firstPhoneTimeStamp //use elapsed time to account for time diff in sensor connection
                    firstTimeStamps[deviceIdx].sensorTimeStamp = (data.timeStamp - (elapsedTime * 1e6)).toLong()
                    Log.d("","Elapsed time for $deviceIDforFunc: $elapsedTime. time stamp index: $deviceIdx")
                    firstTimeStamps[deviceIdx].phoneTimeStamp = System.currentTimeMillis() //won't be necessary soon
                }
                val adjustedPhoneTimeStamp =
                    System.currentTimeMillis() - firstPhoneTimeStamp
                val adjustedSensorTimeStamp =
                    data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                val logString =
                    "$deviceIDforFunc ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: $adjustedSensorTimeStamp"
                val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-ACCData.txt")
                val fileString =
                    "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.x};${data.y};${data.z}; \n"
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


private fun subscribeToPolarGYR(deviceId: String) {
    val isDisposed = gyrDisposable?.isDisposed ?: true
    if (isDisposed) {
        gyrDisposable =
            requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.GYRO)
                .flatMap { settings: PolarSensorSetting ->
                    api.startGyroStreaming(deviceId, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarGyroData: PolarGyroData ->
                        for (data in polarGyroData.samples) {
                            Log.d(TAG, "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
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
private fun subscribeToPolarMAG(deviceId: String){
    val isDisposed = magDisposable?.isDisposed ?: true
    if (isDisposed) {
        magDisposable =
            requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER)
                .flatMap { settings: PolarSensorSetting ->
                    api.startMagnetometerStreaming(deviceId, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarMagData: PolarMagnetometerData ->
                        for (data in polarMagData.samples) {
                            Log.d(TAG, "MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
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
    val isDisposed = ppgDisposable?.isDisposed ?: true
    if (isDisposed) {
        ppgDisposable =
            requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.PPG)
                .flatMap { settings: PolarSensorSetting ->
                    api.startPpgStreaming(deviceId, settings)
                }
                .subscribe(
                    { polarPpgData: PolarPpgData ->
                        val deviceIdx = getDeviceIndexInTimestampArray(deviceId)
                        if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                            for (data in polarPpgData.samples) {
                                val adjustedPhoneTimeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                                val adjustedSensorTimeStamp = data.timeStamp - firstTimeStamps[deviceIdx].sensorTimeStamp
                                val fileString = "${adjustedPhoneTimeStamp};${adjustedSensorTimeStamp};${data.channelSamples[0]};${data.channelSamples[1]};${data.channelSamples[2]};${data.channelSamples[3]} \n"
                                val file = File("${getSaveFolder().absolutePath}/$deviceId-PPGData.txt")
                                if (saveToLogFiles) { file.appendText(fileString) }
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

private fun subscribeToPolarECG(deviceId: String){
    val isDisposed = ecgDisposable?.isDisposed ?: true
    if (isDisposed) {
        ecgDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
            .flatMap { settings: PolarSensorSetting ->
                api.startEcgStreaming(deviceId, settings)
            }
            .subscribe(
                { polarEcgData: PolarEcgData ->
                    for (data in polarEcgData.samples) {
                        Log.d(TAG, "    yV: ${data.voltage} timeStamp: ${data.timeStamp}")
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

fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
    val availableSettings = api.requestStreamSettings(identifier, feature)
    val allSettings = api.requestFullStreamSettings(identifier, feature)
        .onErrorReturn { error: Throwable ->
            Log.w(TAG, "Full stream settings are not available for feature $feature. REASON: $error")
            PolarSensorSetting(emptyMap())
        }
    return Single.zip(availableSettings, allSettings) { available: PolarSensorSetting, all: PolarSensorSetting ->
        if (available.settings.isEmpty()) {
            throw Throwable("Settings are not available")
        } else {
            Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
            Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
            return@zip android.util.Pair(available, all)
        }
    }
        .observeOn(AndroidSchedulers.mainThread())
        .toFlowable()
        .flatMap { sensorSettings: android.util.Pair<PolarSensorSetting, PolarSensorSetting> ->
            showAllSettingsDialogNew(sensorSettings.first.settings).toFlowable()
        }
}

fun showAllSettingsDialogNew(available: Map<PolarSensorSetting.SettingType, Set<Int>>): Single<PolarSensorSetting> {
    return Single.create { e: SingleEmitter<PolarSensorSetting> ->
        val selected: MutableMap<PolarSensorSetting.SettingType, Int> = EnumMap(
            PolarSensorSetting.SettingType::class.java)
        setSettingToMax(selected, PolarSensorSetting.SettingType.SAMPLE_RATE, available)
        setSettingToMax(selected, PolarSensorSetting.SettingType.RESOLUTION, available)
        setSettingToMax(selected, PolarSensorSetting.SettingType.RANGE, available)
        setSettingToMax(selected, PolarSensorSetting.SettingType.CHANNELS, available)
        e.onSuccess(PolarSensorSetting(selected))
    }.subscribeOn(AndroidSchedulers.mainThread())
}

fun setSettingToMax(selected: MutableMap<PolarSensorSetting.SettingType, Int>, type: PolarSensorSetting.SettingType, availibleSettings: Map<PolarSensorSetting.SettingType, Set<Int>>){
    var maxValue = 0
    val availibleValuesForType = availibleSettings[type]?.toList()
    if (availibleValuesForType != null) {
        for (i in availibleValuesForType.indices){
            if (availibleValuesForType[i] > maxValue){
                maxValue = availibleValuesForType[i]
                Log.d("","Found value ${availibleValuesForType[i]} for setting $type")
            }
        }
    }
    selected[type] = maxValue
    Log.d("","Setting $type was autoset to $maxValue")
}
