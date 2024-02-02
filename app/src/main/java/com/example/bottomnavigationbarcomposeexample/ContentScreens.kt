package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.seconds

const val emptyPolarIDListString = "No Connected ID's"

data class AvailableDevices(var deviceName: String){
    var deviceId: String = "none"
    var connected: String = "Connect"
}

data class ConnectedDevices(var deviceName: String){
    var deviceId: String = "none" //some refactoring can be done
    var accValue: Int = 0
}

var firstDeviceFlag = true
var firstConnectedDeviceFlag = true
//var deviceListForHomeScreen = mutableStateListOf<String>("No Connected Devices")
var deviceListForHomeScreen = mutableStateListOf(ConnectedDevices("No Connected Devices"))
var deviceListForDeviceScreen = mutableStateListOf(AvailableDevices(emptyPolarIDListString))
var polarDeviceIdListForConnection = mutableStateListOf(emptyPolarIDListString)
lateinit var voMaster: BluetoothDevice
var lapTimeFileExists = false
var bleStreamsStarted = false
var firstPhoneTimeStamp = System.currentTimeMillis()

@Composable
fun HomeScreen() {
    Log.d("DD", "Home screen composable called")
    var collectingData by remember { mutableStateOf(false) }
    var dataCollectButtonText by remember { mutableStateOf("Start Data Collection")}
    Column(
        Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f, false)
                .background(colorResource(id = R.color.colorPrimaryDark))
                .wrapContentSize(Alignment.TopCenter)
        ) {
            Button(modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .fillMaxWidth(1.0f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorResource(id = R.color.colorText),
                    contentColor = colorResource(id = R.color.colorPrimaryDark)
                ),
                onClick = {
                    collectingData = !collectingData
                    dataCollectButtonText = "Starting..."
                    saveToLogFiles(collectingData) //only save to log files when collecting data
                    firstPhoneTimeStamp = System.currentTimeMillis()//this will be the only place phone time stamp gets set

                    if (!bleStreamsStarted) {
                        if (::voMaster.isInitialized) {
                            subscribeToVOMaster(voMaster, context = context)
                        }
                        saveToLogFiles(true)
                        bleStreamsStarted = true
                        subscribeToAllPolarData(polarDeviceIdListForConnection.toList())
                    }
                }) {
                Text(dataCollectButtonText)
            }
            LaunchedEffect(key1 = dataCollectButtonText){
                if (dataCollectButtonText == "Starting..."){
                    Log.d("","data collect launched effect called")
                    //SEAN refactor this is another re-work, not super important though
                    delay(15000)  //See if there's a way to get the above function to return an on finished
                    dataCollectButtonText = "Data Collection Started"
                }

            }
            LazyColumn(modifier = Modifier.padding(vertical = 4.dp))
            {
                items(items = deviceListForHomeScreen) { connectedDevices: ConnectedDevices ->
                    ListConnectedDevice(name = connectedDevices.deviceName, acc = connectedDevices.accValue)
                }
            }
        }
        //LAP BUTTON
        val list = listOf("Run", "Walk", "Stairs up", "Stairs down")
        val expanded = remember { mutableStateOf(false) }
        val currentValue = remember { mutableStateOf(list[0]) }
        val startText = remember { mutableStateOf("Start") }
        Row {
            Button(modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorResource(id = R.color.colorText),
                    contentColor = colorResource(id = R.color.colorPrimaryDark)
                ),
                onClick = {
                    if (!lapTimeFileExists) {
                        generateNewFile("Lap Times.txt")
                        lapTimeFileExists = true
                    }
                    startText.value = if (startText.value == "Start"){ "End" } else { "Start" }
                    val file = File("${getSaveFolder().absolutePath}/Lap Times.txt")
                    val timeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
                    file.appendText("${startText.value} of ${currentValue.value}: $timeStamp \n")
                }) {
                Text("${startText.value} ${currentValue.value}")
            }
            Row(modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .fillMaxWidth()
                .clickable {
                    expanded.value = !expanded.value
                }
                .background(colorResource(id = R.color.colorText))
            ) {
                Text(text = currentValue.value)
                Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)

                DropdownMenu(expanded = expanded.value, onDismissRequest = {
                    expanded.value = false
                }) {
                    list.forEach {
                        DropdownMenuItem(onClick = {
                            currentValue.value = it
                            expanded.value = false
                        }) {

                            Text(text = it)

                        }
                    }
                }

            }
        }
    }
}

@Composable
fun ListConnectedDevice(name: String, acc: Int) {
    Surface(
        color = colorResource(id = R.color.colorPrimary),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ) {
            Row {
                Column(modifier = Modifier.weight(1f))
                {
                    Text(
                        text = name, style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.ExtraBold
                        ), color = colorResource(id = R.color.colorText)
                    )
                    Text(text = "ACC: $acc", color = colorResource(id = R.color.colorText))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}


@Composable
fun DevicesScreen() {
    var scanBLE by remember { mutableStateOf(false) }
    var scanButtonText by remember { mutableStateOf("Start Scan") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.colorPrimaryDark))
            .wrapContentSize(Alignment.TopCenter)
    ) {
        //SCAN BUTTON
        Button(modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .fillMaxWidth(1.0f),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = colorResource(id = R.color.colorText),
                contentColor = colorResource(id = R.color.colorPrimaryDark)
            ),
            onClick = {
                scanButtonText = "Scanning..."
                scanBLE = true
            }) {
            Text(text = scanButtonText)
        }
        LaunchedEffect(key1 = scanButtonText) {
            if (scanButtonText == "Scanning...") {
                delay(5.seconds)
                scanButtonText = "Restart Scan"
            }
        }
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp))
        {
            items(items = deviceListForDeviceScreen) { availableDevices: AvailableDevices ->
                ListAvailableDevices(name = availableDevices.deviceName, id = availableDevices.deviceId, connected = availableDevices.connected)
            }
        }
    }
    if (scanBLE) {
        StartScan(System.currentTimeMillis())
        scanBLE = false
    }
}

@Composable
fun ListAvailableDevices(name: String, id: String, connected: String) {
    var buttonTextState by remember { mutableStateOf("Connect") }
    Surface(
        color = colorResource(id = R.color.colorPrimary),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {

        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ) {

            Row {
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(text = "Device", color = colorResource(id = R.color.colorText))
                    Text(
                        text = name, style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.ExtraBold
                        ), color = colorResource(id = R.color.colorText)
                    )
                }
                //SINGLE DEVICE CONNECT BUTTON
                OutlinedButton(modifier = Modifier
                    .padding(horizontal = 20.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorResource(R.color.colorText),
                        contentColor = colorResource(
                            id = R.color.colorPrimaryDark
                        )
                    ),
                    onClick = {
                        Log.d("DD", "Tapped on $name")
                        for (index in deviceListForDeviceScreen.indices){
                            deviceListForDeviceScreen[index].deviceId
                            if (deviceListForDeviceScreen[index].deviceId == id){
                                deviceListForDeviceScreen[index].connected = "Connecting..."
                                buttonTextState = "Connecting..."
                                deviceListForDeviceScreen.add(AvailableDevices("none")) //This'll actually work. so adding something works but not changing. supposed to be somewhat fixable but idk. seems chill to me.
                                deviceListForDeviceScreen.remove(AvailableDevices("none")) //https://stackoverflow.com/questions/69718059/android-jetpack-compose-mutablestatelistof-not-doing-recomposition/69718724#69718724
                            }
                        }
                    })
                //{ Text(buttonText) }
                { Text(connected) }
                LaunchedEffect(key1 = buttonTextState) {
                    Log.d("", "Launched effect running")
                    if (buttonTextState == "Connecting...") {
                        Log.d("","Connecting to device in launchedEffect")
                        val deviceID = getPolarDeviceIDFromName(name)
                        if (name.contains("Polar")) {
                            api.connectToDevice(deviceID)
                        }
                        delay(10.seconds) //REFACTOR
                        if (!name.contains("Polar")) {
                            //voMaster = name ^^this would be the ideal structure, but need to figure out how to go from name string back to BluetoothDevice in order for this to work for now voMaster is set during the scan callback
                            if (firstConnectedDeviceFlag) {
                                deviceListForHomeScreen[0].deviceName = name
                                firstConnectedDeviceFlag = false
                            } else {
                                deviceListForHomeScreen.add(ConnectedDevices(name))
                            }
                        }
                    }

                }
            }
        }

    }
}


@Composable
@SuppressLint("MissingPermission")
fun StartScan(startTime: Long) {
    Log.d("DD", "Now scanning")
    val context = LocalContext.current

    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    val listScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            var notInList = true
            for (deviceInListName in deviceListForDeviceScreen) {
                if (result.device.name == deviceInListName.deviceName) notInList = false
            }
            if (result.device.name != null) {
                if ((result.device.name.contains("Polar") || result.device.name.contains("VO2 Master")) && notInList) {
                    if (firstDeviceFlag) {
                        deviceListForDeviceScreen[0].deviceName = result.device.name
                        if (result.device.name.contains("Polar")){
                            deviceListForDeviceScreen[0].deviceId = getPolarDeviceIDFromName(result.device.name)
                        }
                        firstDeviceFlag = false
                    } else {
                        deviceListForDeviceScreen.add(AvailableDevices(deviceName = result.device.name))
                        if (result.device.name.contains("Polar")){
                            val idx = deviceListForDeviceScreen.indexOf(AvailableDevices(result.device.name))
                            deviceListForDeviceScreen[idx].deviceId = getPolarDeviceIDFromName(result.device.name)

                        }
                    }

                    //ISSUE HERE: I'm directly setting this variable on the scan, for non polar device.
                    //This is because for polar devices, the api allows you to get the BluetoothDevice from the name
                    //I don't know how to do that for non polar devices, but If i figure that out i can get rid of this
                    if (result.device.name.contains("VO2 Master")) {
                        voMaster = result.device
                    }
                    Log.d("DD", "Device Found: ${result.device.name}")
                }
            }
            val elapsedTime = System.currentTimeMillis() - startTime
            if ((elapsedTime) > 5000) {
                Log.d("DD", "Scan Stopped")
                bleScanner.stopScan(this)
            }
        }
    }
    bleScanner.startScan(listScanCallback)
}

@Preview(showBackground = true)
@Composable
fun DevicesScreenPreview() {
    DevicesScreen()
}

