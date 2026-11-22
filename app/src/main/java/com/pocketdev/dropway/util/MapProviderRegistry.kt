package com.pocketdev.dropway.util

data class MapProvider(
    val id: String,
    val name: String,
    val category: String, // "satellite", "street", "topo", "special"
    val tileUrl: String,
    val subdomains: String = "abc",
    val maxZoom: Int = 19,
    val description: String = ""
)

object MapProviderRegistry {
    const val DEFAULT_PROVIDER_ID = "google_hybrid"

    val PROVIDERS: List<MapProvider> = listOf(
        // 1. Satellite Group
        MapProvider(
            id = "google_satellite",
            name = "Google Maps (ดาวเทียม)",
            category = "satellite",
            tileUrl = "https://mt{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}",
            subdomains = "0123",
            maxZoom = 20,
            description = "ภาพถ่ายดาวเทียมความละเอียดสูงจาก Google"
        ),
        MapProvider(
            id = "google_hybrid",
            name = "Google Maps (ไฮบริด / ดาวเทียม+ถนน)",
            category = "satellite",
            tileUrl = "https://mt{s}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}",
            subdomains = "0123",
            maxZoom = 20,
            description = "ภาพถ่ายดาวเทียมพร้อมชื่อถนนและสถานที่"
        ),
        MapProvider(
            id = "esri_satellite",
            name = "ESRI Satellite (World Imagery)",
            category = "satellite",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 19,
            description = "ภาพถ่ายดาวเทียมความคมชัดสูงระดับโลกจาก ESRI ArcGIS"
        ),

        // 2. Street & Road Group
        MapProvider(
            id = "google_road",
            name = "Google Maps (แผนที่ / ถนน)",
            category = "street",
            tileUrl = "https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}",
            subdomains = "0123",
            maxZoom = 20,
            description = "แผนที่ถนนมาตรฐาน Google ภาษาไทยชัดเจน"
        ),
        MapProvider(
            id = "google_traffic",
            name = "Google Maps (สภาพจราจร)",
            category = "street",
            tileUrl = "https://mt{s}.google.com/vt/lyrs=m,traffic&x={x}&y={y}&z={z}",
            subdomains = "0123",
            maxZoom = 20,
            description = "แผนที่ถนนพร้อมแสดงเส้นสีสภาพการจราจรแบบ Real-time"
        ),
        MapProvider(
            id = "osm_standard",
            name = "OpenStreetMap (OSM)",
            category = "street",
            tileUrl = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
            subdomains = "abc",
            maxZoom = 19,
            description = "แผนที่เปิดระดับโลก ฟรีและโอเพ่นซอร์ส 100%"
        ),
        MapProvider(
            id = "esri_street",
            name = "ESRI Street (World Street Map)",
            category = "street",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 19,
            description = "แผนที่โครงข่ายถนนคมชัดสะอาดตาจาก ESRI"
        ),
        MapProvider(
            id = "wikimedia",
            name = "Wikimedia / CartoDB Voyager",
            category = "street",
            tileUrl = "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
            subdomains = "abcd",
            maxZoom = 19,
            description = "แผนที่เวกเตอร์เรนเดอร์สบายตา โหลดเร็ว"
        ),

        // 3. Topo & Terrain Group
        MapProvider(
            id = "google_terrain",
            name = "Google Maps (ภูมิประเทศ)",
            category = "topo",
            tileUrl = "https://mt{s}.google.com/vt/lyrs=p&x={x}&y={y}&z={z}",
            subdomains = "0123",
            maxZoom = 20,
            description = "แผนที่แสดงเส้นชั้นความสูงและลักษณะทางกายภาพ"
        ),
        MapProvider(
            id = "esri_topo",
            name = "ESRI Topo (World Topo Map)",
            category = "topo",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 19,
            description = "แผนที่ภูมิประเทศพร้อมเฉดเงาภูเขาและเส้นชั้นความสูง"
        ),
        MapProvider(
            id = "esri_terrain",
            name = "ESRI Terrain (World Terrain Base)",
            category = "topo",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Terrain_Base/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 13,
            description = "แผนที่ฐานภูมิประเทศเน้นมวลพื้นดิน"
        ),
        MapProvider(
            id = "esri_relief",
            name = "ESRI Relief (World Shaded Relief)",
            category = "topo",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Shaded_Relief/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 13,
            description = "แผนที่เฉดเงาความสูงต่ำของเทือกเขาเสมือนจริง"
        ),
        MapProvider(
            id = "esri_physical",
            name = "ESRI Physical (World Physical Map)",
            category = "topo",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Physical_Map/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 8,
            description = "แผนที่กายภาพแสดงลักษณนามธรรมชาติ ป่าไม้ และแม่น้ำ"
        ),
        MapProvider(
            id = "esri_natgeo",
            name = "ESRI NatGeo (World Map)",
            category = "topo",
            tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/NatGeo_World_Map/MapServer/tile/{z}/{y}/{x}",
            maxZoom = 16,
            description = "แผนที่สไตล์คลาสสิกอันเป็นเอกลักษณ์ของ National Geographic"
        ),
        MapProvider(
            id = "open_topo_map",
            name = "OpenTopoMap",
            category = "topo",
            tileUrl = "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png",
            subdomains = "abc",
            maxZoom = 17,
            description = "แผนที่แสดงเส้นชั้นระดับความสูงสไตล์ยุโรป"
        ),

        // 4. Specialized Group
        MapProvider(
            id = "ign_france",
            name = "IGN Plan (France)",
            category = "special",
            tileUrl = "https://data.geopf.fr/wmts?REQUEST=GetTile&SERVICE=WMTS&VERSION=1.0.0&LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}",
            maxZoom = 18,
            description = "แผนที่ทางการของสถาบันภูมิศาสตร์แห่งชาติฝรั่งเศส (IGN)"
        )
    )

    fun getById(id: String): MapProvider {
        return PROVIDERS.find { it.id == id } ?: PROVIDERS.first { it.id == DEFAULT_PROVIDER_ID }
    }
}
