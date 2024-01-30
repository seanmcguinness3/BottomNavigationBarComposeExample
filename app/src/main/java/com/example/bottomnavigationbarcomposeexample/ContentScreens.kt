package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.seconds

public const val emptyPolarIDListString = "No Connected ID's"

var firstDeviceFlag = true
var firstConnectedDeviceFlag = true
var deviceListForHomeScreen = mutableStateListOf<String>("No Connected Devices")
var polarDeviceIdListForConnection = mutableStateListOf<String>(emptyPolarIDListString)
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
                        //subscribeToAllPolarData(polarDeviceIdListForConnection.toList())
                    }
                }) {
                Text(dataCollectButtonText)
            }
            LaunchedEffect(key1 = dataCollectButtonText){
                if (dataCollectButtonText == "Starting..."){
                    Log.d("","data collect launched effect called")
                    subscribeToAllPolarData(polarDeviceIdListForConnection.toList())
                    delay(15000)  //See if there's a way to get the above function to return an on finished
                    dataCollectButtonText = "Data Collection Started"
                }

            }
            LazyColumn(modifier = Modifier.padding(vertical = 4.dp))
            {
                items(items = deviceListForHomeScreen) { name: String ->
                    ListConnectedDevice(name = name)
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
fun ListConnectedDevice(name: String) {
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
                    Text(text = "Device", color = colorResource(id = R.color.colorText))
                    Text(
                        text = name, style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.ExtraBold
                        ), color = colorResource(id = R.color.colorText)
                    )
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
fun DevicesScreen(mainViewModel: MainActivity.DeviceViewModel = viewModel()) {
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
            items(items = mainViewModel.deviceList) { name ->
                ListItem(name = name)
            }
        }
    }

    if (scanBLE) {
        StartScan(System.currentTimeMillis())
        scanBLE = false
    }
}

@Composable
fun ListItem(name: String) {
    var buttonText by remember { mutableStateOf("Connect") }
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
                        buttonText = "Connecting..." // NEXT TIME U MESS WITH THIS DO A VARIABLE
                    })
                { Text(buttonText) }
                LaunchedEffect(key1 = buttonText) {
                    Log.d("", "Launched effect running")
                    if (buttonText == "Connecting...") {
                        Log.d("","Connecting to device in launchedEffect")
                        val deviceID = getPolarDeviceIDFromName(name)
                        if (name.contains("Polar")) {
                            api.connectToDevice(deviceID)
                        }
                        delay(10.seconds)  //SEAN REFACTOR There's actually an override function (deviceConnected(polarDeviceInfo: PolarDeviceInfo) )
                        //I think you could probably use it to add the device to the list, and change the text to connected. That way you don't need this delay in the
                        //launched effect, and if you execute the below code in the override it might not matter what screen you're on.
                        if (name.contains("Polar")) {
                            Log.d("", "adding polar device $deviceID to home screen")
                            if (firstConnectedDeviceFlag) {
                                deviceListForHomeScreen[0] = name
                                polarDeviceIdListForConnection[0] = getPolarDeviceIDFromName(name)
                                firstConnectedDeviceFlag = false
                            } else {
                                deviceListForHomeScreen.add(name)
                                polarDeviceIdListForConnection.add(getPolarDeviceIDFromName(name))
                            }
                        } else {
                            //voMaster = name
                            //^^this would be the ideal structure, but need to figure out how to go from
                            //name string back to BluetoothDevice in order for this to work
                            //for now voMaster is set during the scan callback
                            if (firstConnectedDeviceFlag) {
                                deviceListForHomeScreen[0] = name
                                firstConnectedDeviceFlag = false
                            } else {
                                deviceListForHomeScreen.add(name)
                            }
                        }
                        buttonText = "Connected"
                    }

                }
            }
        }

    }
}


@Composable
@SuppressLint("MissingPermission")
fun StartScan(startTime: Long, mainViewModel: MainActivity.DeviceViewModel = viewModel()) {
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
            for (deviceInListName in mainViewModel.deviceList) {
                if (result.device.name == deviceInListName) notInList = false
            }
            if (result.device.name != null) {
                //sean voMaster this is were to set the voMaster variable
                if ((result.device.name.contains("Polar") || result.device.name.contains("VO2 Master")) && notInList) {
                    if (firstDeviceFlag) {
                        mainViewModel.deviceList[0] = result.device.name
                        firstDeviceFlag = false
                    } else {
                        mainViewModel.deviceList.add(result.device.name)
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

