package com.example.bottomnavigationbarcomposeexample

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

var deviceList = arrayListOf<String>("No Devices")
var deviceIterator = 0

class MainViewModel : ViewModel(){
    var deviceList2 = arrayListOf<String>("No Devices")

    fun updateDeviceList(){
        deviceList2 = deviceList
    }
}

@Composable
fun HomeScreen(mainViewModel: MainViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.colorPrimaryDark))
            .wrapContentSize(Alignment.Center)
    ) {
        Text(
            text = "Home View",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center,
            fontSize = 25.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}



@Composable
fun DevicesScreen(mainViewModel: MainViewModel = viewModel()) {
    var text by remember { mutableStateOf("Start Scan") }
    var scanBLE by remember { mutableStateOf(false) }
    var derivedList by remember { mutableStateOf(deviceList) }
    //val derivedList by lazy(LazyThreadSafetyMode.PUBLICATION) { deviceList }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.colorPrimaryDark))
            .wrapContentSize(Alignment.TopCenter)
    ) {
        Text(
            text = "Devices View",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center,
            fontSize = 25.sp
        )
        Button(modifier = Modifier.padding(horizontal = 80.dp),
            onClick = {
            text = "Scanning..."
            Log.d("DD", "WORKING")
            scanBLE = true
        }) {
            Text(text)
        }
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp))
        {
            items(items = mainViewModel.deviceList2){name ->
                ListItem(name = name)
            }
        }
    }

    if(scanBLE){
        StartScan(System.currentTimeMillis())
        scanBLE = false
    }
}

@Composable
fun ListItem(name : String){
    Surface(color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)){

        Column(modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()){

            Row {
                Column(
                    modifier = Modifier
                        .weight(1f)
                ){
                    Text(text = "Course")
                    Text(text = name, style = MaterialTheme.typography.h4.copy(
                        fontWeight = FontWeight.ExtraBold
                    ))
                }

                OutlinedButton(onClick = { /*TODO*/ }) {
                    Text(text = "Show More")
                }
            }
        }

    }
}


@Composable
@SuppressLint("MissingPermission")
fun StartScan(startTime: Long, mainViewModel: MainViewModel = viewModel()){
    Log.d("DD","Now scanning")
    val context = LocalContext.current

    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    val listScanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if(result.device.name != null) {
                deviceList.add(result.device.name)
                mainViewModel.updateDeviceList()
                Log.d("DD", "Device Found: ${result.device.name}")
            }
            val elapsedTime = System.currentTimeMillis() - startTime

            if ((elapsedTime) > 1000){
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

