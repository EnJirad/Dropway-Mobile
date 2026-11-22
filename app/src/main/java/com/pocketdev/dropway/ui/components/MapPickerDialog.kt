@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.pocketdev.dropway.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.CoordinateHelper
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onConfirm: (lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current

    // Default to Mae Sot center if null
    val startLat = initialLat ?: 16.71667
    val startLng = initialLng ?: 98.56667
    val initialZoom = if (initialLat != null && initialLng != null) 18 else 14

    var currentLat by remember { mutableStateOf(startLat) }
    var currentLng by remember { mutableStateOf(startLng) }
    var isSatellite by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun moveToGpsLocation() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            Toast.makeText(context, "ระบบค้นหาตำแหน่งไม่พร้อมใช้งาน", Toast.LENGTH_SHORT).show()
            return
        }

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(context, "กรุณาเปิด GPS/Location ในเครื่องก่อนค้นหาตำแหน่ง", Toast.LENGTH_LONG).show()
            return
        }

        isLocating = true
        var lastLocation: Location? = null
        try {
            if (isGpsEnabled) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastLocation == null && isNetworkEnabled) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: SecurityException) {}

        isLocating = false
        if (lastLocation != null) {
            val lat = lastLocation.latitude
            val lng = lastLocation.longitude
            currentLat = lat
            currentLng = lng
            webViewRef?.evaluateJavascript("if (window.moveToLocation) { window.moveToLocation($lat, $lng, 18); }", null)
            Toast.makeText(context, "ย้ายไปยังตำแหน่งปัจจุบันแล้ว", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "กำลังค้นหาสัญญาณดาวเทียม กรุณารอสักครู่...", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            moveToGpsLocation()
        } else {
            Toast.makeText(context, "จำเป็นต้องได้รับสิทธิ์ตำแหน่งเพื่อเลื่อนไปยังจุดที่คุณอยู่", Toast.LENGTH_LONG).show()
        }
    }

    fun handleCurrentLocationClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            moveToGpsLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = SurfaceCard
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Header Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "เลือกพิกัดบนแผนที่",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                "เลื่อนแผนที่ให้หมุดตรงกับหลังคาบ้านที่ต้องการ",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(NeutralBackground, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "ปิด", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Divider(color = BorderOutline)

                    // 2. Interactive Map Container with Fixed Center Pin
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Leaflet WebView
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true

                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onMapMoved(lat: Double, lng: Double) {
                                            currentLat = lat
                                            currentLng = lng
                                        }
                                    }, "AndroidBridge")

                                    webViewClient = object : WebViewClient() {}

                                    val htmlContent = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <meta charset="utf-8" />
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                            <style>
                                                html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; }
                                                .leaflet-control-attribution { display: none !important; }
                                            </style>
                                        </head>
                                        <body>
                                            <div id="map"></div>
                                            <script>
                                                var osmLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 });
                                                var satLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19 });

                                                var map = L.map('map', {
                                                    center: [$startLat, $startLng],
                                                    zoom: $initialZoom,
                                                    zoomControl: false,
                                                    layers: [osmLayer]
                                                });

                                                map.on('move', function() {
                                                    var center = map.getCenter();
                                                    if (window.AndroidBridge) {
                                                        window.AndroidBridge.onMapMoved(center.lat, center.lng);
                                                    }
                                                });

                                                window.setMapType = function(isSatellite) {
                                                    if (isSatellite) {
                                                        map.removeLayer(osmLayer);
                                                        map.addLayer(satLayer);
                                                    } else {
                                                        map.removeLayer(satLayer);
                                                        map.addLayer(osmLayer);
                                                    }
                                                };

                                                window.moveToLocation = function(lat, lng, zoom) {
                                                    map.setView([lat, lng], zoom || 18, { animate: true });
                                                };
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()

                                    loadDataWithBaseURL("https://dropway.app", htmlContent, "text/html", "UTF-8", null)
                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Center Pin Overlay (stays precisely fixed at center of map)
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.offset(y = (-20).dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(8.dp, CircleShape)
                                        .background(TealDark, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "หมุดศูนย์กลาง",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                )
                            }
                        }

                        // Top Controls: Floating Layer Toggle & Coordinate HUD
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Coordinate Badge
                            Surface(
                                color = SurfaceCard.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 3.dp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.6f, %.6f", currentLat, currentLng),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        // Floating Right Controls: Map Layer Toggle & Current Location Button
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Layer Toggle (Standard / Satellite)
                            FloatingActionButton(
                                onClick = {
                                    val nextMode = !isSatellite
                                    isSatellite = nextMode
                                    webViewRef?.evaluateJavascript("if (window.setMapType) { window.setMapType($nextMode); }", null)
                                },
                                containerColor = SurfaceCard,
                                contentColor = if (isSatellite) TealDark else TextSecondary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = if (isSatellite) "สลับเป็นแผนที่ปกติ" else "สลับเป็นดาวเทียม",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Current GPS Location Button
                            FloatingActionButton(
                                onClick = { handleCurrentLocationClick() },
                                containerColor = SurfaceCard,
                                contentColor = if (isLocating) TealPrimary else TextSecondary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                if (isLocating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TealPrimary)
                                } else {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "ตำแหน่งปัจจุบันของฉัน",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Bottom Action Bar: Cancel & Confirm Button
                    Surface(
                        color = SurfaceCard,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ยกเลิก", color = TextSecondary)
                            }
                            Button(
                                onClick = { onConfirm(currentLat, currentLng) },
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ยืนยันพิกัดนี้", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
