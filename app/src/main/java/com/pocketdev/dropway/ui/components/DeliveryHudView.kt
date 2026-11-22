@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.pocketdev.dropway.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.core.content.ContextCompat
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.NavigationHouse
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.CoordinateHelper
import com.pocketdev.dropway.util.DriverActions
import com.pocketdev.dropway.util.MapProviderRegistry
import com.pocketdev.dropway.util.SoundFeedback
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeliveryHudView(
    stops: List<NavigationHouse>,
    onCloseHud: () -> Unit,
    onMarkDelivered: (NavigationHouse) -> Unit,
    onReportProblem: (NavigationHouse) -> Unit,
    onOptimizeRoute: (currentLat: Double?, currentLng: Double?) -> Unit,
    onSelectStop: (NavigationHouse) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    // Active pending stops
    val pendingStops = remember(stops) {
        stops.filter { it.deliveryStatus.lowercase() == "pending" }
    }

    var selectedNavId by remember { mutableStateOf<String?>(null) }

    val currentTarget = remember(pendingStops, selectedNavId) {
        if (selectedNavId != null) {
            pendingStops.firstOrNull { it.navId == selectedNavId } ?: pendingStops.firstOrNull()
        } else {
            pendingStops.firstOrNull()
        }
    }

    val currentTargetIndex = remember(pendingStops, currentTarget) {
        if (currentTarget == null) -1 else pendingStops.indexOf(currentTarget)
    }

    val nextTarget = remember(pendingStops, currentTargetIndex) {
        if (currentTargetIndex >= 0 && currentTargetIndex + 1 < pendingStops.size) {
            pendingStops[currentTargetIndex + 1]
        } else null
    }

    // Driver GPS Location state
    var driverLat by remember { mutableStateOf<Double?>(null) }
    var driverLng by remember { mutableStateOf<Double?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasAlertedForNavId by remember { mutableStateOf<String?>(null) }

    val selectedProvider = remember {
        MapProviderRegistry.getById(prefs.defaultMapLayer)
    }

    // Distance calculation to current target
    val distanceToTargetMeters = remember(driverLat, driverLng, currentTarget) {
        if (driverLat != null && driverLng != null && currentTarget?.lat != null && currentTarget.lng != null) {
            CoordinateHelper.distanceMeters(driverLat!!, driverLng!!, currentTarget.lat, currentTarget.lng)
        } else null
    }

    // Proximity Alert: Beep when within 70 meters of destination
    LaunchedEffect(distanceToTargetMeters, currentTarget?.navId) {
        val dist = distanceToTargetMeters
        val navId = currentTarget?.navId
        if (dist != null && navId != null && dist <= 70.0 && hasAlertedForNavId != navId) {
            SoundFeedback.playProximityAlert(context)
            hasAlertedForNavId = navId
        }
    }

    // GPS Location Listener
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                driverLat = loc.latitude
                driverLng = loc.longitude
                // Update map center and driver pin
                val js = "if (window.updateDriverLocation) { window.updateDriverLocation(${loc.latitude}, ${loc.longitude}); }"
                webViewRef?.evaluateJavascript(js, null)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (hasPermission && locationManager != null) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
                    val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (last != null) {
                        driverLat = last.latitude
                        driverLng = last.longitude
                    }
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener)
                    val last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (last != null) {
                        driverLat = last.latitude
                        driverLng = last.longitude
                    }
                }
            } catch (_: SecurityException) {}
        }

        onDispose {
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    // Push updated stops and markers to map WebView
    fun refreshMapMarkers() {
        val stopsJsonArray = JSONArray()
        stops.forEachIndexed { index, stop ->
            if (stop.lat != null && stop.lng != null) {
                val obj = JSONObject().apply {
                    put("navId", stop.navId)
                    put("lat", stop.lat)
                    put("lng", stop.lng)
                    put("label", stop.fullName ?: stop.address ?: "จุดส่ง ${index + 1}")
                    put("status", stop.deliveryStatus.lowercase())
                    put("order", index + 1)
                    put("isTarget", stop.navId == currentTarget?.navId)
                    put("isNext", stop.navId == nextTarget?.navId)
                }
                stopsJsonArray.put(obj)
            }
        }
        val currentLatVal = driverLat ?: currentTarget?.lat ?: 16.71667
        val currentLngVal = driverLng ?: currentTarget?.lng ?: 98.56667
        val js = "if (window.renderDeliveryStops) { window.renderDeliveryStops(${stopsJsonArray.toString()}, $currentLatVal, $currentLngVal); }"
        webViewRef?.evaluateJavascript(js, null)
    }

    LaunchedEffect(stops, currentTarget?.navId) {
        refreshMapMarkers()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // 1. Fullscreen Map (WebView)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onStopClicked(navId: String) {
                            val stop = stops.firstOrNull { it.navId == navId }
                            if (stop != null) {
                                selectedNavId = navId
                                onSelectStop(stop)
                            }
                        }
                    }, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            refreshMapMarkers()
                        }
                    }

                    val initUrl = selectedProvider.tileUrl
                    val initSubs = selectedProvider.subdomains
                    val initMaxZoom = selectedProvider.maxZoom
                    val startLat = driverLat ?: currentTarget?.lat ?: 16.71667
                    val startLng = driverLng ?: currentTarget?.lng ?: 98.56667

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <style>
                                html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #0f172a; }
                                .leaflet-control-attribution { display: none !important; }
                                .driver-icon {
                                    background: #0ea5e9;
                                    border: 3px solid #ffffff;
                                    border-radius: 50%;
                                    box-shadow: 0 0 16px rgba(14, 165, 233, 0.9);
                                }
                                .stop-pin {
                                    border-radius: 50%;
                                    color: white;
                                    font-weight: bold;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    box-shadow: 0 3px 8px rgba(0,0,0,0.6);
                                    border: 2px solid #ffffff;
                                }
                                .pin-target {
                                    background: #10b981;
                                    width: 38px !important;
                                    height: 38px !important;
                                    margin-left: -19px !important;
                                    margin-top: -19px !important;
                                    font-size: 17px;
                                    animation: pulse 1.5s infinite;
                                }
                                .pin-next {
                                    background: #f59e0b;
                                    width: 32px !important;
                                    height: 32px !important;
                                    margin-left: -16px !important;
                                    margin-top: -16px !important;
                                    font-size: 14px;
                                }
                                .pin-pending {
                                    background: #3b82f6;
                                    width: 28px !important;
                                    height: 28px !important;
                                    margin-left: -14px !important;
                                    margin-top: -14px !important;
                                    font-size: 13px;
                                }
                                .pin-delivered {
                                    background: #64748b;
                                    width: 24px !important;
                                    height: 24px !important;
                                    margin-left: -12px !important;
                                    margin-top: -12px !important;
                                    font-size: 11px;
                                    opacity: 0.7;
                                }
                                @keyframes pulse {
                                    0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.8); }
                                    70% { box-shadow: 0 0 0 14px rgba(16, 185, 129, 0); }
                                    100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
                                }
                            </style>
                        </head>
                        <body>
                            <div id="map"></div>
                            <script>
                                var map = L.map('map', {
                                    center: [$startLat, $startLng],
                                    zoom: 17,
                                    zoomControl: false
                                });
                                L.tileLayer('$initUrl', {
                                    maxZoom: $initMaxZoom,
                                    subdomains: '$initSubs'
                                }).addTo(map);

                                var driverMarker = null;
                                var markersLayer = L.layerGroup().addTo(map);
                                var routeLine = null;

                                window.updateDriverLocation = function(lat, lng) {
                                    if (!driverMarker) {
                                        driverMarker = L.circleMarker([lat, lng], {
                                            radius: 10,
                                            fillColor: '#0ea5e9',
                                            color: '#ffffff',
                                            weight: 3,
                                            opacity: 1,
                                            fillOpacity: 0.9
                                        }).addTo(map);
                                    } else {
                                        driverMarker.setLatLng([lat, lng]);
                                    }
                                };

                                window.renderDeliveryStops = function(stops, centerLat, centerLng) {
                                    markersLayer.clearLayers();
                                    var points = [];

                                    if (driverMarker) {
                                        points.push(driverMarker.getLatLng());
                                    }

                                    stops.forEach(function(s) {
                                        var pinClass = 'stop-pin ';
                                        if (s.isTarget) pinClass += 'pin-target';
                                        else if (s.isNext) pinClass += 'pin-next';
                                        else if (s.status === 'delivered') pinClass += 'pin-delivered';
                                        else pinClass += 'pin-pending';

                                        var icon = L.divIcon({
                                            className: pinClass,
                                            html: '<span>' + s.order + '</span>'
                                        });

                                        var marker = L.marker([s.lat, s.lng], { icon: icon });
                                        marker.on('click', function() {
                                            if (window.AndroidBridge) {
                                                window.AndroidBridge.onStopClicked(s.navId);
                                            }
                                        });
                                        markersLayer.addLayer(marker);

                                        if (s.isTarget || s.isNext) {
                                            points.push([s.lat, s.lng]);
                                        }
                                    });

                                    if (routeLine) {
                                        map.removeLayer(routeLine);
                                    }
                                    if (points.length >= 2) {
                                        routeLine = L.polyline(points, {
                                            color: '#10b981',
                                            weight: 4,
                                            opacity: 0.8,
                                            dashArray: '8, 8'
                                        }).addTo(map);
                                    }

                                    if (centerLat && centerLng) {
                                        map.panTo([centerLat, centerLng], { animate: true });
                                    }
                                };
                            </script>
                        </body>
                        </html>
                    """.trimIndent()

                    loadDataWithBaseURL("https://dropway.app", html, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Navigation Bar (Floating Glassmorphism Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 28.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit HUD Button
                IconButton(
                    onClick = onCloseHud,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF334155), CircleShape)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "กลับหน้ารายการ",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Progress Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (currentTarget != null) "จุดที่ ${currentTargetIndex + 1} จาก ${pendingStops.size}" else "ส่งครบทุกจุดแล้ว",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (distanceToTargetMeters != null) "อีก ${CoordinateHelper.formatDistance(distanceToTargetMeters)}" else "กำลังจับสัญญาณ GPS...",
                        fontSize = 12.sp,
                        color = if (distanceToTargetMeters != null && distanceToTargetMeters <= 70) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Auto Route Optimize Button
                IconButton(
                    onClick = {
                        onOptimizeRoute(driverLat, driverLng)
                        Toast.makeText(context, "จัดเรียงเส้นทางใกล้สุดเรียบร้อย", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(TealDark, CircleShape)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "จัดเรียงเส้นทางอัตโนมัติ",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 3. Bottom Driver HUD Panel
        if (currentTarget != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFA0F172A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981)
                        ) {
                            Text(
                                text = "จุดส่งปัจจุบัน #${currentTargetIndex + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (distanceToTargetMeters != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (distanceToTargetMeters <= 70) Color(0xFF059669) else Color(0xFF334155)
                            ) {
                                Text(
                                    text = "ระยะห่าง: ${CoordinateHelper.formatDistance(distanceToTargetMeters)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HUGE HOUSE NUMBER & RECIPIENT (No need to memorize)
                    Text(
                        text = currentTarget.fullName ?: currentTarget.address ?: "บ้านเลขที่เป้าหมาย",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 30.sp
                    )

                    if (!currentTarget.phone.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentTarget.phone,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Driver Note & Warning
                    val warningNote = currentTarget.driverNote ?: currentTarget.note
                    if (!warningNote.isNullOrBlank()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF422006))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = warningNote,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // UPCOMING STOP PREVIEW (The user's dream feature: know next house in advance)
                    if (nextTarget != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "หลังต่อไป (จุดที่ ${currentTargetIndex + 2}):",
                                        fontSize = 11.sp,
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = nextTarget.fullName ?: nextTarget.address ?: "จุดส่งถัดไป",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ACTION BUTTONS (Giant Touch Targets for Riders)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. One-Tap Phone Call
                        if (!currentTarget.phone.isNullOrBlank()) {
                            Button(
                                onClick = { DriverActions.openPhoneDialer(context, currentTarget.phone) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "โทร", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("โทร", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // 2. Open External Google Maps
                        Button(
                            onClick = {
                                DriverActions.openMapNavigation(
                                    context,
                                    currentTarget.lat,
                                    currentTarget.lng,
                                    currentTarget.address,
                                    currentTarget.fullName
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = "แผนที่", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("แผนที่", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // 3. Giant Delivered Button
                        Button(
                            onClick = { onMarkDelivered(currentTarget) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(2f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "ส่งสำเร็จ", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ส่งสำเร็จ", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }
                    }
                }
            }
        } else {
            // When all stops delivered
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "ส่งพัสดุครบทุกจุดแล้ว!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ยอดเยี่ยมมาก ปิดงานส่งของสำหรับรอบนี้เรียบร้อย",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onCloseHud,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("กลับหน้ารายการคิว")
                    }
                }
            }
        }
    }
}
