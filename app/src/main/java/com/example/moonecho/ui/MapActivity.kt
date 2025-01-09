package com.example.moonecho.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.moonecho.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MapActivity : AppCompatActivity(), AMapLocationListener {

    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var progressBar: ProgressBar

    private lateinit var locationClient: AMapLocationClient
    private lateinit var locationOption: AMapLocationClientOption

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserUID: String? = null
    private var friendUID: String? = null

    private var currentUserMarker: Marker? = null
    private var friendMarker: Marker? = null

    private var isCurrentUserLocationReady = false
    private var isFriendLocationReady = false

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // 调用隐私合规接口
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        mapView = findViewById(R.id.mapView)
        progressBar = findViewById(R.id.progressBar)

        mapView.onCreate(savedInstanceState)
        aMap = mapView.map

        // 设置地图加载完成监听器
        aMap.setOnMapLoadedListener {
            // 地图加载完成后再调整摄像头
            if (isCurrentUserLocationReady && isFriendLocationReady) {
                adjustCamera()
            }
        }

        currentUserUID = auth.currentUser?.uid
        if (currentUserUID == null) {
            Toast.makeText(this, "未获取到当前用户的UID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 获取好友的 UID
        currentUserUID.let { uid ->
            db.collection("users").document(uid!!).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        friendUID = document.getString("friendUid")
                        if (friendUID != null) {
                            // 首先获取对方的最后位置
                            getOtherUserLastLocation(friendUID!!)
                            // 然后监听对方的位置更新
                            listenOtherUserLocation(friendUID!!)
                        } else {
                            Log.d("MapActivity", "未找到好友的UID")
                        }
                    } else {
                        Log.d("MapActivity", "当前用户文档不存在")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MapActivity", "获取好友UID失败: ${e.message}")
                }
        }

        if (hasLocationPermission()) {
            initLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })) {
                initLocation()
            } else {
                Toast.makeText(this, "需要位置权限才能使用此功能", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initLocation() {
        progressBar.visibility = ProgressBar.VISIBLE

        locationClient = AMapLocationClient(applicationContext)
        locationOption = AMapLocationClientOption()
        locationOption.isOnceLocation = true // 单次定位，可根据需要修改为连续定位
        locationClient.setLocationOption(locationOption)
        locationClient.setLocationListener(this)
        locationClient.startLocation()
    }

    private fun getOtherUserLastLocation(friendUID: String) {
        db.collection("users")
            .document(friendUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    if (latitude != null && longitude != null) {
                        Log.d("MapActivity", "对方最后位置：$latitude, $longitude")
                        addOrUpdateMarker(latitude, longitude, "对方位置", false)
                    } else {
                        Log.d("MapActivity", "对方位置数据为空")
                    }
                } else {
                    Log.d("MapActivity", "对方位置文档不存在")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MapActivity", "获取对方最后位置失败: ${e.message}")
            }
    }

    private fun listenOtherUserLocation(friendUID: String) {
        db.collection("users")
            .document(friendUID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MapActivity", "监听对方位置失败: ${error.message}")
                    Toast.makeText(this, "监听对方位置失败: ${error.message}", Toast.LENGTH_SHORT)
                        .show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val latitude = snapshot.getDouble("latitude")
                    val longitude = snapshot.getDouble("longitude")
                    if (latitude != null && longitude != null) {
                        Log.d("MapActivity", "对方位置更新：$latitude, $longitude")
                        runOnUiThread {
                            addOrUpdateMarker(latitude, longitude, "对方位置", false)
                        }
                    }
                } else {
                    Log.d("MapActivity", "对方位置文档不存在")
                }
            }
    }

    private fun addOrUpdateMarker(
        latitude: Double,
        longitude: Double,
        title: String,
        isCurrentUser: Boolean
    ) {
        val latLng = LatLng(latitude, longitude)

        if (isCurrentUser) {
            if (currentUserMarker == null) {
                val markerOptions = MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                currentUserMarker = aMap.addMarker(markerOptions)
            } else {
                currentUserMarker?.position = latLng
            }
            isCurrentUserLocationReady = true
        } else {
            if (friendMarker == null) {
                val markerOptions = MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                friendMarker = aMap.addMarker(markerOptions)
            } else {
                friendMarker?.position = latLng
            }
            isFriendLocationReady = true
        }

        Log.d("MapActivity", "更新标记：$title at $latitude, $longitude")

        // 当地图加载完成且两个位置都已准备好时，调整摄像头
        if (isCurrentUserLocationReady && isFriendLocationReady && aMap != null) {
            adjustCamera()
        }
    }

    private fun adjustCamera() {
        if (currentUserMarker == null || friendMarker == null) {
            return
        }

        val currentPosition = currentUserMarker!!.position
        val friendPosition = friendMarker!!.position

        // 计算中点
        val midLat = (currentPosition.latitude + friendPosition.latitude) / 2
        val midLng = (currentPosition.longitude + friendPosition.longitude) / 2
        val midLatLng = LatLng(midLat, midLng)

        // 计算两点间的距离
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            currentPosition.latitude, currentPosition.longitude,
            friendPosition.latitude, friendPosition.longitude,
            results
        )
        val distance = results[0]

        // 根据距离设置缩放级别，距离越大，缩放级别越小
        val zoomLevel = getZoomLevel(distance)

        // 移动摄像头到中点并设置缩放级别
        val cameraUpdate = com.amap.api.maps2d.CameraUpdateFactory.newLatLngZoom(midLatLng, zoomLevel)
        aMap.animateCamera(cameraUpdate)
    }

    // 根据距离获取合适的缩放级别
    private fun getZoomLevel(distance: Float): Float {
        return when {
            distance < 1000 -> 19f
            distance < 5000 -> 17f
            distance < 10000 -> 15f
            distance < 50000 -> 13f
            distance < 100000 -> 11f
            distance < 150000 -> 9f
            else -> 6f
        }
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (location?.errorCode == 0) {
            val latitude = location.latitude
            val longitude = location.longitude

            Log.d("MapActivity", "定位结果：$latitude, $longitude")

            currentUserUID?.let { uid ->
                val userDocRef = db.collection("users").document(uid)
                val data = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
                userDocRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("MapActivity", "位置更新成功")
                        addOrUpdateMarker(latitude, longitude, "我的位置", true)
                        progressBar.visibility = ProgressBar.GONE
                    }
                    .addOnFailureListener { e ->
                        Log.e("MapActivity", "更新位置失败: ${e.message}")
                        Toast.makeText(this, "更新位置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = ProgressBar.GONE
                    }
            }
        } else {
            Log.e("MapActivity", "定位失败: ${location?.errorInfo}")
            Toast.makeText(this, "定位失败: ${location?.errorInfo}", Toast.LENGTH_SHORT).show()
            progressBar.visibility = ProgressBar.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationClient.onDestroy()
    }
}