package com.example.bottomnavigationbarcomposeexample

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat.requestPermissions
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProvider
import android.location.Location
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.Builder
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val PERMISSION_REQUEST_CODE = 1
lateinit var api: PolarBleApi
lateinit var context: Context
lateinit var locationFile: File
var locationLogCreated = false

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }


        requestPermissions(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), PERMISSION_REQUEST_CODE
        )
/*        fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
            val location: Location? = task.result
            Log.d("", "Location found: Lat: ${location!!.latitude} Lon: ${location!!.longitude}")
            if (!locationLogCreated) {
                locationFile = generateNewFile("Location Data")
                locationFile.appendText("Time Stamp; Latitude; Longitude \n")
                locationLogCreated = true
            }
            val timeStamp = System.currentTimeMillis()
            locationFile.appendText("$timeStamp; ${location.latitude}; ${location.longitude}")
        }
        */

        @SuppressLint("MissingPermission")
        class LocationManager(
            context: Context,
            private var timeInterval: Long,
            private var minimalDistance: Float
        ) : LocationCallback() {

            private var request: LocationRequest
            private var locationClient: FusedLocationProviderClient

            init {
                // getting the location client
                locationClient = LocationServices.getFusedLocationProviderClient(context)
                request = createRequest()
            }

            private fun createRequest(): LocationRequest =
                // New builder
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, timeInterval).apply {
                    setMinUpdateDistanceMeters(minimalDistance)
                    setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    setWaitForAccurateLocation(true)
                }.build()

            fun changeRequest(timeInterval: Long, minimalDistance: Float) {
                this.timeInterval = timeInterval
                this.minimalDistance = minimalDistance
                createRequest()
                stopLocationTracking()
                startLocationTracking()
            }

            fun startLocationTracking() =
                locationClient.requestLocationUpdates(request, this, Looper.getMainLooper())


            fun stopLocationTracking() {
                locationClient.flushLocations()
                locationClient.removeLocationUpdates(this)
            }

            override fun onLocationResult(location: LocationResult) {
                // TODO: on location change - do something with new location
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // TODO: react on the availability change
            }

        }

        context = applicationContext
        api = getApi(applicationContext)
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                //SEE IF WE NEED THIS
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                //SEE IF WE NEED THIS
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                //SEE IF WE NEED THIS
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                //SEE IF WE NEED THIS
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                //SEE IF WE NEED THIS
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                //SEE IF WE NEED THIS
            }

            override fun hrNotificationReceived(
                identifier: String, data: PolarHrData.PolarHrSample
            ) {
                // //SEE IF WE NEED THIS
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun checkBT() {
        requestPermissions(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), PERMISSION_REQUEST_CODE
        )
    }

    class DeviceViewModel : ViewModel() {
        var deviceList = mutableStateListOf<String>("No Devices")
        var scanButtonText = mutableStateOf("Start Scan")
    }

}


fun getApi(context: Context): PolarBleApi {
    val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context, setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION
            )
        )
    }
    return api
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) },
        content = { padding ->
            Box(modifier = Modifier.padding(padding)) {
                Navigation(navController = navController)
            }
        },
        backgroundColor = colorResource(R.color.colorPrimaryDark) // Set background color to avoid the white flashing when you switch between screens
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}

@Composable
fun Navigation(navController: NavHostController) {
    NavHost(navController, startDestination = NavigationItem.Home.route) {
        composable(NavigationItem.Home.route) {
            HomeScreen()
        }
        composable(NavigationItem.Devices.route) {
            DevicesScreen()
        }
    }
}


@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        NavigationItem.Home,
        NavigationItem.Devices,
    )
    BottomNavigation(
        backgroundColor = colorResource(id = R.color.colorPrimary),
        contentColor = Color.White
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            BottomNavigationItem(
                icon = { Icon(painterResource(id = item.icon), contentDescription = item.title) },
                label = { Text(text = item.title) },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.White.copy(0.4f),
                alwaysShowLabel = true,
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) {
                                saveState = true
                            }
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BottomNavigationBarPreview() {
    // BottomNavigationBar()
}