package com.example.bottomnavigationbarcomposeexample

sealed class NavigationItem(var route: String, var icon: Int, var title: String) {
    object Home : NavigationItem("home", R.drawable.ic_home, "Home")
    object Devices : NavigationItem("devices", R.drawable.ic_music, "Devices")
}