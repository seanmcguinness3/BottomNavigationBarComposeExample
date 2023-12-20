package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
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

var firstDeviceFlag = true
var firstConnectedDeviceFlag = true
var deviceListForHomeScreen = mutableStateListOf<String>("No Connected Devices")

@Composable
fun HomeScreen() { //not going to pass this guy a view model because it doesn't contain any info anyway
    Log.d("DD","Home screen composable called")

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
            onClick = { SubscribeToAllPolarData(arrayOf("C19E1A21", "C929ED29"),api) }) {
            Text(text = "Start Data Collection")
        }
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp))
        {
            items(items = deviceListForHomeScreen){name: String ->
                ListConnectedDevice(name = name)
            }
        }
    }
}

@Composable
fun ListConnectedDevice(name: String) {
    Surface(
        color = colorResource(id = R.color.colorPrimary),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ){
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ){
            Row{
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
    var changeButtonToConnected by remember { mutableStateOf(false)}
    var connectToDeviceName by remember { mutableStateOf("none")}
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
                        connectToDeviceName = name
                        changeButtonToConnected = true
                        val deviceID = GetPolarDeviceIDFromName(name)
                        api.connectToDevice(deviceID)
                        if (firstConnectedDeviceFlag){
                            mainViewModel.connectedDeviceList[0] = name
                            deviceListForHomeScreen[0] = name
                            firstConnectedDeviceFlag = false
                        } else {
                            mainViewModel.connectedDeviceList.add(name)
                            deviceListForHomeScreen.add(name)
                        }
                    }) {
                    Text(if (changeButtonToConnected) "Connected" else "Connect")
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
            if (result.device.name != null) {
                if (firstDeviceFlag){
                    mainViewModel.deviceList[0] = result.device.name
                    firstDeviceFlag = false 
                }else {
                    mainViewModel.deviceList.add(result.device.name)
                }

                Log.d("DD", "Device Found: ${result.device.name}")
            }
            val elapsedTime = System.currentTimeMillis() - startTime

            if ((elapsedTime) > 1000) {
                mainViewModel.scanButtonText = mutableStateOf("Restart Scan") //This should update the button text but it's not, don't know why
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

