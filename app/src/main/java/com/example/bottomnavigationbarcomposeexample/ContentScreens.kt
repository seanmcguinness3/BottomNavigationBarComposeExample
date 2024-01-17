package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import kotlinx.coroutines.delay
import java.io.File
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

public const val emptyPolarIDListString = "No Connected ID's"

var firstDeviceFlag = true
var firstConnectedDeviceFlag = true
var deviceListForHomeScreen = mutableStateListOf<String>("No Connected Devices")
var polarDeviceIdListForConnection = mutableStateListOf<String>(emptyPolarIDListString)
lateinit var voMaster: BluetoothDevice
var lapTimeFileExists = false
var polarStreamsStarted = false

@Composable
fun HomeScreen() { //not going to pass this guy a view model because it doesn't contain any info anyway
    Log.d("DD", "Home screen composable called")
    var collectingData by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                saveToLogFiles(collectingData) //only save to log files when collecting data

                if (!polarStreamsStarted) {
                    if (::voMaster.isInitialized) {
                        subscribeToVOMaster(voMaster, context = context)
                    }
                    saveToLogFiles(true)
                    subscribeToAllPolarData(polarDeviceIdListForConnection.toList(), api, false)
                    polarStreamsStarted = true
                }
            }) {
            Text(if (collectingData) "Stop Data Collection" else "Start Data Collection")
        }
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp))
        {
            items(items = deviceListForHomeScreen) { name: String ->
                ListConnectedDevice(name = name)
            }
        }
        //LAP BUTTON
        Button(modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .fillMaxWidth(1.0f),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = colorResource(id = R.color.colorText),
                contentColor = colorResource(id = R.color.colorPrimaryDark)
            ),
            onClick = {
                if (!lapTimeFileExists) {
                    generateNewFile("Lap Times.txt")
                    lapTimeFileExists = true
                }
                val file = File("${getSaveFolder().absolutePath}/Lap Times.txt")
                val timeStamp = System.currentTimeMillis()
                file.appendText("Lap Time: $timeStamp \n")
            }) {
            Text("Lap Time Stamp")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                mainViewModel.scanButtonText = mutableStateOf("Scanning...")
                scanBLE = true
            }) {
            Text(text = mainViewModel.scanButtonText.value)
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
fun ListItem(name: String, mainViewModel: MainActivity.DeviceViewModel = viewModel()) {
    var buttonText by remember { mutableStateOf("Connect") }
    var changeButtonToConnected by remember { mutableStateOf(false) } //remove this variable once tri-state setup is working
    var connectToDeviceName by remember { mutableStateOf("none") }
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
                        connectToDeviceName = name  //at some point try deleting this variable, idk what it's doing, something with remember
                        changeButtonToConnected = !changeButtonToConnected  //this one gotta go to
                        buttonText = "Connecting"  //this is working, just have to delay the list addition using same logic as text changes

                        if (name.contains("Polar")){
                            val deviceID = getPolarDeviceIDFromName(name)
                            api.connectToDevice(deviceID)

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
                            if(firstConnectedDeviceFlag){
                                deviceListForHomeScreen[0] = name
                                firstConnectedDeviceFlag = false
                            } else {
                                deviceListForHomeScreen.add(name)
                            }
                        }
                    }) {
                    Text(//if (changeButtonToConnected) "Connected" else "Connect" // )
                    buttonText)
                }
                LaunchedEffect(Unit){
                    delay(5.seconds)
                    buttonText = "Connected"
                    Log.d("","Button text shoulda changed")
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
                    if (result.device.name.contains("VO2 Master")){
                        voMaster = result.device
                    }
                    Log.d("DD", "Device Found: ${result.device.name}")
                }
            }
            val elapsedTime = System.currentTimeMillis() - startTime

            if ((elapsedTime) > 1000) {
                mainViewModel.scanButtonText =
                    mutableStateOf("Restart Scan") //This should update the button text but it's not, don't know why
                //keep an eye out for a solution but moivng on for now
                Log.d("DD", "Current scan button text = ${mainViewModel.scanButtonText.value}")
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

