package com.example.healthmanager.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * GPS 定位追踪器
 *
 * 使用 FusedLocationProviderClient 持续获取 GPS 位置，
 * 实时计算累计运动距离（公里），用于骑行/游泳等无需计步的运动。
 *
 * 使用方式：
 * 1. startTracking() 开始追踪（需要已获取定位权限）
 * 2. 观察 distanceKm StateFlow 获取实时距离
 * 3. stopTracking() 停止追踪并返回总距离
 */
class LocationTracker(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /** 实时累计距离（公里） */
    private val _distanceKm = MutableStateFlow(0f)
    val distanceKm: StateFlow<Float> = _distanceKm

    /** 是否正在追踪 */
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    /** 上一个有效的位置点 */
    private var lastLocation: Location? = null

    /** 累计距离（米） */
    private var totalDistanceMeters = 0f

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000L  // 每3秒请求一次位置更新
    ).apply {
        setMinUpdateDistanceMeters(5f)  // 至少移动5米才触发更新，过滤噪声
        setMinUpdateIntervalMillis(2000L)
    }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return

            // 过滤精度太差的定位（>30米不采用）
            if (location.accuracy > 30f) {
                Log.d(TAG, "精度过低(${location.accuracy}m)，跳过")
                return
            }

            lastLocation?.let { prev ->
                val delta = prev.distanceTo(location)
                // 过滤异常跳跃（单次移动超过100米可能是GPS漂移）
                if (delta < 100f && delta > 1f) {
                    totalDistanceMeters += delta
                    _distanceKm.value = totalDistanceMeters / 1000f
                    Log.d(TAG, "距离更新: +${String.format("%.1f", delta)}m, 总计: ${String.format("%.2f", _distanceKm.value)}km")
                }
            }

            lastLocation = location
        }
    }

    /**
     * 开始 GPS 追踪
     * 调用前必须确保已获取 ACCESS_FINE_LOCATION 权限
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (_isTracking.value) return

        totalDistanceMeters = 0f
        _distanceKm.value = 0f
        lastLocation = null
        _isTracking.value = true

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        Log.d(TAG, "GPS 追踪已启动")
    }

    /**
     * 停止 GPS 追踪
     * @return 本次追踪的总距离（公里）
     */
    fun stopTracking(): Float {
        if (!_isTracking.value) return 0f

        _isTracking.value = false
        fusedClient.removeLocationUpdates(locationCallback)
        lastLocation = null
        Log.d(TAG, "GPS 追踪已停止，总距离: ${String.format("%.2f", _distanceKm.value)}km")
        return _distanceKm.value
    }

    companion object {
        private const val TAG = "LocationTracker"
    }
}
