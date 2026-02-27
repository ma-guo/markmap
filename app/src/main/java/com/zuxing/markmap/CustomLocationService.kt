package com.zuxing.markmap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class CustomLocationService(private val context: Context) {

    private var locationManager: LocationManager? = null
    private var locationListener: CustomLocationListener? = null
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var isRunning = false

    interface CustomLocationListener {
        fun onLocationChanged(location: Location)
        fun onLocationFailed(error: String)
    }

    fun start(locationListener: CustomLocationListener) {
        if (isRunning) return

        this.locationListener = locationListener
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        handler = Handler(Looper.getMainLooper())

        if (!hasLocationPermission()) {
            locationListener.onLocationFailed("缺少定位权限")
            return
        }

        val gpsProvider = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val glonassProvider = locationManager?.isProviderEnabled("glonass")

        if (gpsProvider != true && glonassProvider != true) {
            locationListener.onLocationFailed("GPS和北斗定位不可用")
            return
        }

        isRunning = true
        requestLocation()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocation() {
        try {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationListener?.onLocationChanged(location)
                    Logger.d("自定义定位成功: lat=${location.latitude}, lng=${location.longitude}")
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    locationListener?.onLocationFailed("定位提供者 $provider 已禁用")
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )

            locationManager?.requestLocationUpdates(
                "glonass",
                1000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )

            val lastKnownLocation = getLastKnownLocation()
            if (lastKnownLocation != null) {
                this.locationListener?.onLocationChanged(lastKnownLocation)
            }

        } catch (e: SecurityException) {
            locationListener?.onLocationFailed("安全异常: ${e.message}")
        } catch (e: Exception) {
            locationListener?.onLocationFailed("定位异常: ${e.message}")
        }
    }

    private fun getLastKnownLocation(): Location? {
        try {
            var location: Location? = null

            val gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLocation != null) {
                location = gpsLocation
            }

            val glonassLocation = locationManager?.getLastKnownLocation("glonass")
            if (glonassLocation != null) {
                if (location == null || glonassLocation.accuracy < location.accuracy) {
                    location = glonassLocation
                }
            }

            val networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (networkLocation != null) {
                if (location == null || networkLocation.accuracy < location.accuracy) {
                    location = networkLocation
                }
            }

            return location
        } catch (e: SecurityException) {
            return null
        }
    }

    fun stop() {
        isRunning = false
        try {
            locationManager?.removeUpdates { }
        } catch (e: Exception) {
            Logger.e("停止自定义定位失败: ${e.message}")
        }
        handler?.removeCallbacksAndMessages(null)
        handler = null
        locationManager = null
        locationListener = null
    }

    fun isLocationEnabled(): Boolean {
        val gpsProvider = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val glonassProvider = locationManager?.isProviderEnabled("glonass")
        return gpsProvider == true || glonassProvider == true
    }
}
