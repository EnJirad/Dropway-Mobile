@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.pocketdev.dropway.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.http.SslError
import android.os.Bundle
import android.provider.Settings
import android.webkit.*
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
import com.pocketdev.dropway.util.MapAssetLoader
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
    var isGpsDisabled by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasAlertedForNavId by remember { mutableStateOf<String?>(null) }

    // Map layer cycle: Satellite -> Road -> Hybrid -> OSM
    var currentProviderIndex by remember {
        val defaultIdx = MapProviderRegistry.PROVIDERS.indexOfFirst { it.id == prefs.defaultMapLayer }
        mutableStateOf(if (defaultIdx >= 0) defaultIdx else 0)
    }
    val selectedProvider = MapProviderRegistry.PROVIDERS[currentProviderIndex]

    // Location permission state & launcher
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            hasLocationPermission = true
        } else {
            Toast.makeText(context, "กรุณาเปิดสิทธิ์ GPS เพื่อให้นำทางได้", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
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

    // Real-Time GPS Tracking Engine (Queries both GPS & Network for immediate accuracy)
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) return@DisposableEffect onDispose {}

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        isGpsDisabled = !isGpsEnabled && !isNetworkEnabled

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                driverLat = loc.latitude
                driverLng = loc.longitude
                isGpsDisabled = false
                val js = "if (window.updateDriverLocation) { window.updateDriverLocation(${loc.latitude}, ${loc.longitude}); }"
                webViewRef?.evaluateJavascript(js, null)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) { isGpsDisabled = false }
            override fun onProviderDisabled(provider: String) {
                val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val net = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                isGpsDisabled = !gps && !net
            }
        }

        // 1. Immediately read last known location from best available provider
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (p in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(p)
                if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                    bestLocation = loc
                }
            } catch (_: SecurityException) {}
        }

        if (bestLocation != null) {
            driverLat = bestLocation.latitude
            driverLng = bestLocation.longitude
            val js = "if (window.updateDriverLocation) { window.updateDriverLocation(${bestLocation.latitude}, ${bestLocation.longitude}); }"
            webViewRef?.evaluateJavascript(js, null)
        }

        // 2. Request active updates on both GPS and Network providers
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(p)) {
                    locationManager.requestLocationUpdates(p, 1000L, 0f, listener)
                }
            } catch (_: SecurityException) {}
        }

        onDispose {
            try {
                locationManager.removeUpdates(listener)
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

    LaunchedEffect(driverLat, driverLng) {
        val lat = driverLat
        val lng = driverLng
        if (lat != null && lng != null && webViewRef != null) {
            val js = "if (window.updateDriverLocation) { window.updateDriverLocation($lat, $lng); }"
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    // Bundled offline Leaflet assets
    val leafletCss = remember { MapAssetLoader.getLeafletCss(context) }
    val leafletJs = remember { MapAssetLoader.getLeafletJs(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // 1. Fullscreen Map (WebView with 100% Offline Bundled Leaflet)
        AndroidView(
            factory = { ctx ->
                ResizableMapWebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#E2E8F0"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowFileAccess = true
                        allowContentAccess = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.proceed()
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val lat = driverLat
                            val lng = driverLng
                            if (lat != null && lng != null) {
                                view?.evaluateJavascript("if (window.updateDriverLocation) { window.updateDriverLocation($lat, $lng); }", null)
                            }
                            refreshMapMarkers()
                            view?.evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
                            view?.postDelayed({
                                view.evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
                            }, 250)
                            view?.postDelayed({
                                view.evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
                            }, 600)
                        }
                    }

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
                            <style>
                                $leafletCss
                                html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #e2e8f0; }
                                .leaflet-control-attribution { display: none !important; }
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
                                .driver-marker-wrapper {
                                    position: relative;
                                    width: 24px;
                                    height: 24px;
                                }
                                .driver-dot {
                                    position: absolute;
                                    top: 2px;
                                    left: 2px;
                                    width: 20px;
                                    height: 20px;
                                    background: #0284c7;
                                    border: 3px solid #ffffff;
                                    border-radius: 50%;
                                    box-shadow: 0 2px 6px rgba(0,0,0,0.4);
                                    z-index: 10;
                                }
                                .driver-pulse {
                                    position: absolute;
                                    top: -10px;
                                    left: -10px;
                                    width: 44px;
                                    height: 44px;
                                    background: rgba(2, 132, 199, 0.35);
                                    border-radius: 50%;
                                    animation: driver-radar 1.8s infinite ease-out;
                                    z-index: 5;
                                }
                                @keyframes driver-radar {
                                    0% { transform: scale(0.4); opacity: 1; }
                                    100% { transform: scale(1.6); opacity: 0; }
                                }
                                @keyframes pulse {
                                    0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.8); }
                                    70% { box-shadow: 0 0 0 14px rgba(16, 185, 129, 0); }
                                    100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
                                }
                                @keyframes spin {
                                    to { transform: rotate(360deg); }
                                }
                            </style>
                        </head>
                        <body>
                            <div id="map"></div>
                            <div id="map-loader" style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 500; font-family: sans-serif; font-size: 14px; color: #64748b; font-weight: bold; text-align: center; pointer-events: none;">
                                <div style="width: 32px; height: 32px; border: 3px solid #cbd5e1; border-top-color: #0d9488; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 8px;"></div>
                                กำลังโหลดแผนที่...
                            </div>
                            <script>
                                $leafletJs
                            </script>
                            <script>
                                var currentTileLayer = L.tileLayer('$initUrl', {
                                    maxZoom: $initMaxZoom,
                                    subdomains: '$initSubs',
                                    errorTileUrl: 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png'
                                });

                                var map = L.map('map', {
                                    center: [$startLat, $startLng],
                                    zoom: 17,
                                    zoomControl: false,
                                    layers: [currentTileLayer]
                                });

                                map.whenReady(function() {
                                    var loader = document.getElementById('map-loader');
                                    if (loader) loader.style.display = 'none';
                                    map.invalidateSize();
                                });

                                setTimeout(function() { if (window.map) window.map.invalidateSize(); }, 150);
                                setTimeout(function() { if (window.map) window.map.invalidateSize(); }, 400);
                                setTimeout(function() { if (window.map) window.map.invalidateSize(); }, 1000);

                                var driverIcon = L.divIcon({
                                    className: 'driver-marker-wrapper',
                                    html: '<div class="driver-pulse"></div><div class="driver-dot"></div>',
                                    iconSize: [24, 24],
                                    iconAnchor: [12, 12]
                                });

                                var driverMarker = null;
                                var markersLayer = L.layerGroup().addTo(map);
                                var routeLine = null;

                                window.switchMapLayer = function(tileUrl, subdomains, maxZoom) {
                                    if (currentTileLayer) {
                                        map.removeLayer(currentTileLayer);
                                    }
                                    currentTileLayer = L.tileLayer(tileUrl, {
                                        maxZoom: maxZoom || 19,
                                        subdomains: subdomains || '0123',
                                        errorTileUrl: 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png'
                                    });
                                    map.addLayer(currentTileLayer);
                                    if (window.map) window.map.invalidateSize();
                                };

                                window.centerOnLocation = function(lat, lng) {
                                    if (window.map) {
                                        window.map.invalidateSize();
                                        window.map.setView([lat, lng], 17, { animate: true });
                                    }
                                };

                                window.fitAllMarkers = function() {
                                    if (window.map) window.map.invalidateSize();
                                    if (markersLayer.getLayers().length > 0) {
                                        var group = new L.featureGroup(markersLayer.getLayers());
                                        if (driverMarker) group.addLayer(driverMarker);
                                        map.fitBounds(group.getBounds().pad(0.15));
                                    }
                                };

                                window.updateDriverLocation = function(lat, lng) {
                                    var loader = document.getElementById('map-loader');
                                    if (loader) loader.style.display = 'none';
                                    if (!driverMarker) {
                                        driverMarker = L.marker([lat, lng], {
                                            icon: driverIcon,
                                            zIndexOffset: 1000
                                        }).addTo(map);
                                    } else {
                                        driverMarker.setLatLng([lat, lng]);
                                    }
                                    if (window.map) {
                                        window.map.invalidateSize();
                                    }
                                };

                                window.renderDeliveryStops = function(stops, centerLat, centerLng) {
                                    var loader = document.getElementById('map-loader');
                                    if (loader) loader.style.display = 'none';
                                    if (window.map) {
                                        window.map.invalidateSize();
                                    }
                                    markersLayer.clearLayers();
                                    var points = [];

                                    if (driverMarker) {
                                        points.push(driverMarker.getLatLng());
                                    } else if (centerLat && centerLng) {
                                        window.updateDriverLocation(centerLat, centerLng);
                                        points.push([centerLat, centerLng]);
                                    }

                                    stops.forEach(function(s) {
                                        var pinClass = 'stop-pin ';
                                        if (s.isTarget) pinClass += 'pin-target';
                                        else if (s.isNext) pinClass += 'pin-next';
                                        else if (s.status === 'delivered') pinClass += 'pin-delivered';
                                        else pinClass += 'pin-pending';

                                        var icon = L.divIcon({
                                            className: pinClass,
                                            html: '<span>' + s.order + '</span>',
                                            iconSize: s.isTarget ? [38, 38] : (s.isNext ? [32, 32] : [28, 28]),
                                            iconAnchor: s.isTarget ? [19, 19] : (s.isNext ? [16, 16] : [14, 14])
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
                                        routeLine = null;
                                    }
                                    if (points.length >= 2) {
                                        routeLine = L.polyline(points, {
                                            color: '#10b981',
                                            weight: 4,
                                            opacity: 0.85,
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

        // 2. Top Control Bar (Progress + Auto Route + Close)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            // Warning if GPS is disabled on the phone
            if (isGpsDisabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GPS ในเครื่องถูกปิดอยู่", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("เปิด GPS", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Glassmorphism HUD Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    // Back to list button
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

                    // Progress info
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (currentTarget != null) "จุดที่ ${currentTargetIndex + 1} จาก ${pendingStops.size}" else "ส่งครบทุกจุดแล้ว",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (distanceToTargetMeters != null) "อีก ${CoordinateHelper.formatDistance(distanceToTargetMeters)}"
                            else if (driverLat != null) "ตรวจพบตำแหน่งคนขับแล้ว"
                            else "กำลังค้นหาพิกัด GPS...",
                            fontSize = 12.sp,
                            color = if (distanceToTargetMeters != null && distanceToTargetMeters <= 70) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Auto Route Optimizer
                    IconButton(
                        onClick = {
                            onOptimizeRoute(driverLat, driverLng)
                            Toast.makeText(context, "จัดเรียงลำดับเส้นทางใกล้สุดสำเร็จ", Toast.LENGTH_SHORT).show()
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
        }

        // 3. Floating Map Controls (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // My Location Button (Centers map on driver)
            FloatingActionButton(
                onClick = {
                    if (driverLat != null && driverLng != null) {
                        val js = "if (window.centerOnLocation) { window.centerOnLocation(${driverLat}, ${driverLng}); }"
                        webViewRef?.evaluateJavascript(js, null)
                    } else {
                        Toast.makeText(context, "ยังไม่ได้รับพิกัด GPS ปัจจุบัน", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = Color(0xEE1E293B),
                contentColor = Color(0xFF38BDF8),
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Place, contentDescription = "ตำแหน่งฉัน", modifier = Modifier.size(22.dp))
            }

            // Layer Switcher Button (Toggles between Satellite, Road, Hybrid, OSM)
            FloatingActionButton(
                onClick = {
                    currentProviderIndex = (currentProviderIndex + 1) % MapProviderRegistry.PROVIDERS.size
                    val newProvider = MapProviderRegistry.PROVIDERS[currentProviderIndex]
                    prefs.defaultMapLayer = newProvider.id
                    val js = "if (window.switchMapLayer) { window.switchMapLayer('${newProvider.tileUrl}', '${newProvider.subdomains}', ${newProvider.maxZoom}); }"
                    webViewRef?.evaluateJavascript(js, null)
                    Toast.makeText(context, "เปลี่ยนแผนที่: ${newProvider.name}", Toast.LENGTH_SHORT).show()
                },
                containerColor = Color(0xEE1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "เปลี่ยนชั้นแผนที่", modifier = Modifier.size(20.dp))
            }

            // Fit All Markers Button
            FloatingActionButton(
                onClick = {
                    val js = "if (window.fitAllMarkers) { window.fitAllMarkers(); }"
                    webViewRef?.evaluateJavascript(js, null)
                },
                containerColor = Color(0xEE1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "ดูภาพรวมทุกจุด", modifier = Modifier.size(20.dp))
            }
        }

        // 4. Bottom Driver HUD Panel
        if (currentTarget != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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

                    // HUGE HOUSE NUMBER & RECIPIENT
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

                    // UPCOMING STOP PREVIEW (Shows next house so driver can pre-pick parcel)
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

                    // ACTION BUTTONS (Giant touch targets for driving)
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
            // When all stops delivered or empty queue
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
                        "ยังไม่มีจุดส่งที่รอดำเนินการ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "เพิ่มจุดส่งจากแท็บ 'คลังบ้าน' หรือสแกนใบปะหน้าก่อนเริ่มนำทาง",
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

private class ResizableMapWebView(context: Context) : WebView(context) {
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0) {
            post {
                evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
            }
            postDelayed({
                evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
            }, 200)
            postDelayed({
                evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
            }, 500)
        }
    }
}
