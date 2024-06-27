package com.example.bottomnavigationbarcomposeexample

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.*
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import com.google.android.gms.location.LocationRequest
import android.os.Build
import androidx.annotation.RequiresApi
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
import java.util.UUID
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

private const val PERMISSION_REQUEST_CODE = 1
private const val timeWindow = 50000L
var altitudeDifference = 0F
lateinit var api: PolarBleApi
lateinit var context: Context

class MainActivity : ComponentActivity() {

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //START THE MAIN SCREEN
        setContent {
            MainScreen()
        }

        //REQUEST PERMISSIONS
        requestPermissions(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), PERMISSION_REQUEST_CODE
        )

        //START THE POLAR API
        context = applicationContext
        api = getApi(applicationContext)
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                //SEE IF WE NEED THIS
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("", "adding polar device ${polarDeviceInfo.deviceId}  to home screen")
                if (firstConnectedDeviceFlag) {
                    deviceListForHomeScreen[0] = polarDeviceInfo.name
                    polarDeviceIdListForConnection[0] = polarDeviceInfo.deviceId
                    firstConnectedDeviceFlag = false
                } else {
                    deviceListForHomeScreen.add(polarDeviceInfo.name)
                    polarDeviceIdListForConnection.add(polarDeviceInfo.deviceId)
                }
                for (index in deviceListForDeviceScreen.indices){
                    Log.d("", "${deviceListForDeviceScreen[index].deviceName} (from list) should be = ${polarDeviceInfo.name} (from callback)")
                    val testList = getPolarDeviceIDFromName(deviceListForDeviceScreen[index].deviceName) //refactor
                    val testCallback = getPolarDeviceIDFromName(polarDeviceInfo.name) //add .deviceId to data class
                    if (testList == testCallback){
                        deviceListForDeviceScreen[index].connected = "Connected"
                        deviceListForDeviceScreen.add(AvailableDevices("none")) //This'll actually work. so adding something works but not changing. supposed to be somewhat fixable but idk. seems chill to me.
                        deviceListForDeviceScreen.remove(AvailableDevices("none")) //https://stackoverflow.com/questions/69718059/android-jetpack-compose-mutablestatelistof-not-doing-recomposition/69718724#69718724
                    }
                }
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {

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

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
            // //SEE IF WE NEED THIS
            }

        })

        //START THE LOCATION TRACKING
        LocationManager(context, 10L, 1.0F).startLocationTracking()

        //START THE BAROMETER MONITORING
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        SensorActivity().startBarometer(sensorManager, sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE))
    }

    class SensorActivity() : Activity(), SensorEventListener {
        //sean maybe delete this after barometer window is working
        //private lateinit var sensorManager: SensorManager
        //private var mPressure: Sensor? = null
/*        public override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            mPressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        }*/
        var baroQueueValues = ArrayDeque<Float>(50)
        var baroQueueTimeStamps = ArrayDeque<Long>(50)
        override fun onSensorChanged(event: SensorEvent){
            //Log.d("", "barometer reading: ${event.values[0]}")
            var timeStamp = 0L
            if (firstTimeStamps.size >= 1){
                timeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
            }else{
                timeStamp = System.currentTimeMillis()
            }
            baroQueueValues.addFirst(event.values[0])
            baroQueueTimeStamps.addFirst(timeStamp)

            var idxOfFiftySecondsAgo = 0
            for (value in baroQueueTimeStamps){
                val elapsedTime = timeStamp - value
                if (elapsedTime > timeWindow && idxOfFiftySecondsAgo == 0) { //if the window start index hasn't been set, set it.
                    idxOfFiftySecondsAgo = baroQueueTimeStamps.indexOf(value)
                }
                if (elapsedTime > 2 * timeWindow){ //after a super long time, start removing queue elements so it doesn't get too long
                    baroQueueTimeStamps.removeLast()
                    baroQueueValues.removeLast()
                    break
                }
            }

            altitudeDifference = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, baroQueueValues[idxOfFiftySecondsAgo]) - SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, baroQueueValues.first())
            generateAndAppend("BarometerData.txt","$timeStamp, ${event.values[0]} \n", "TimeStamp, pressure (hPa)")
        }

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            Log.d("", "The barometer accuracy changed")
        }

        fun startBarometer(sensorManager: SensorManager, mPressure: Sensor){
            sensorManager.registerListener(this, mPressure, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

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

        fun startLocationTracking() =
            locationClient.requestLocationUpdates(request, this, Looper.getMainLooper())

        fun stopLocationTracking() {
            locationClient.flushLocations()
            locationClient.removeLocationUpdates(this)
        }

        var speedQueueValues = ArrayDeque<Float>(50)
        var speedQueueTimeStamps = ArrayDeque<Long>(50)
        override fun onLocationResult(location: LocationResult) {
            var timeStamp = 0L
            if (firstTimeStamps.size >= 1){
                timeStamp = System.currentTimeMillis() - firstPhoneTimeStamp
            }else{
                timeStamp = System.currentTimeMillis()
            }
            speedQueueValues.addFirst(location.lastLocation!!.speed)
            speedQueueTimeStamps.addFirst(timeStamp)

            var idxOfFiftySecondsAgo = 0 //going to have to loop through the timestamp array to find the queue index of speed value that happened 50 seconds ago

            for (value in speedQueueTimeStamps){
                val elapsedTime = timeStamp - value
                //Log.d("","timestamp queue length: ${speedQueueTimeStamps.size} elapsed time: $elapsedTime") delete
                if (elapsedTime > timeWindow && idxOfFiftySecondsAgo == 0) { //if the window start index hasn't been set, set it. sean replace 50L with constant,
                    idxOfFiftySecondsAgo = speedQueueTimeStamps.indexOf(value) //sean you can break this for loop here when you're done
                }
                if (elapsedTime > 2 * timeWindow){ //after a super long time, start removing queue elements so it doesn't get too long
                    speedQueueTimeStamps.removeLast()
                    speedQueueValues.removeLast()
                    break
                }

            }
            val windowOfSpeeds = speedQueueValues.take(idxOfFiftySecondsAgo)
            val avgSpeedOverWindow = windowOfSpeeds.average()
            //Log.d("","avgSpeedOverWindow: $avgSpeedOverWindow") //delete

            calculatePower(avgSpeedOverWindow, altitudeDifference)

            val fileString = "$timeStamp, ${location.lastLocation!!.latitude}, ${location.lastLocation!!.longitude}, ${location.lastLocation!!.altitude} \n"
            generateAndAppend("LocationData.txt",fileString,"TimeStamp, Latitude, Longitude, Altitude \n")
        }

        private fun calculatePower(avgSpeedOverWindow: Double, altitudeDifference: Float) {
            Log.d("","power calc function inputs, speed: $avgSpeedOverWindow, altitude diff: $altitudeDifference")
            val distance = avgSpeedOverWindow / timeWindow //I'm assuming that speed is in m/s, but I'm not sure, may have to check this on the bus
            val slopeAngle = atan(altitudeDifference/distance)
            //COMPUTE DRAGF
            val Cd = 0.63; val area = 0.5089; val rho = 1.2041
            val dragF = 0.5 * Cd * area * rho * avgSpeedOverWindow * avgSpeedOverWindow
            //COMPUTE GRAVITYF
            val g = 9.8076; val mass = 75; //sean you'll have to enter the mass here. def needs to be changed
            val gravityF = mass * g * sin(slopeAngle)
            //COMPUTE ROLLRESISTANCEF
            val Crr = 0.005
            val rollResistanceF = Crr * mass * g * cos(slopeAngle)
            val power = dragF + gravityF + rollResistanceF
            Log.d("","resulting power was $power")
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            Log.d("","Location Availible")
        }

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
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION,
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