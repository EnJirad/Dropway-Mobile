package com.pocketdev.dropway.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.NavigationHouse
import com.pocketdev.dropway.data.remote.NeonClient
import com.pocketdev.dropway.ui.components.ConfirmActionDialog
import com.pocketdev.dropway.ui.components.DeliveryStatusBadge
import com.pocketdev.dropway.ui.components.EditDriverNoteDialog
import com.pocketdev.dropway.ui.components.ReportProblemDialog
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.DriverActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    neonClient: NeonClient,
    prefs: PreferencesManager,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stops by remember { mutableStateOf<List<NavigationHouse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("pending") } // "all", "pending", "delivered", "reported"
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var reportingStop by remember { mutableStateOf<NavigationHouse?>(null) }
    var editingNoteStop by remember { mutableStateOf<NavigationHouse?>(null) }
    var deletingStop by remember { mutableStateOf<NavigationHouse?>(null) }
    var showClearDeliveredConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    fun loadStops() {
        if (!prefs.isConfigured()) {
            errorMessage = "กรุณาตั้งค่า Neon Database URL ในหน้าตั้งค่าก่อนใช้งาน"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val res = neonClient.getNavigationStops(prefs.userId)
            isLoading = false
            res.onSuccess {
                stops = it
            }.onFailure {
                errorMessage = "ดึงข้อมูลคิวนำทางไม่สำเร็จ: ${it.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStops()
    }

    val filteredStops = remember(stops, selectedFilter, searchQuery) {
        stops.filter { stop ->
            val matchesFilter = when (selectedFilter) {
                "pending" -> stop.deliveryStatus.lowercase() == "pending"
                "delivered" -> stop.deliveryStatus.lowercase() == "delivered"
                "reported" -> stop.deliveryStatus.lowercase() == "reported"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                (stop.fullName?.lowercase()?.contains(q) == true) ||
                (stop.phone?.contains(q) == true) ||
                (stop.address?.lowercase()?.contains(q) == true) ||
                (stop.trackingNumber?.lowercase()?.contains(q) == true) ||
                (stop.note?.lowercase()?.contains(q) == true) ||
                (stop.driverNote?.lowercase()?.contains(q) == true)
            }
            matchesFilter && matchesSearch
        }
    }

    val totalCount = stops.size
    val deliveredCount = stops.count { it.deliveryStatus.lowercase() == "delivered" }
    val reportedCount = stops.count { it.deliveryStatus.lowercase() == "reported" }
    val pendingCount = stops.count { it.deliveryStatus.lowercase() == "pending" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("คิวจัดส่งและนำทาง", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "โหมดคนขับ: ${if (prefs.handMode == "left") "มือซ้าย" else "มือขวา"}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadStops() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช")
                    }
                    IconButton(onClick = { showClearDeliveredConfirm = true }) {
                        Icon(Icons.Default.Check, contentDescription = "ล้างที่ส่งแล้ว", tint = StatusDelivered)
                    }
                    IconButton(onClick = { showClearAllConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "ล้างคิวทั้งหมด", tint = StatusReported)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NeutralBackground)
        ) {
            // Stats summary bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ทั้งหมด", fontSize = 11.sp, color = TextMuted)
                        Text("$totalCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    }
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = BorderOutline)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("รอดำเนินการ", fontSize = 11.sp, color = StatusPending)
                        Text("$pendingCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusPending)
                    }
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = BorderOutline)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ส่งแล้ว", fontSize = 11.sp, color = StatusDelivered)
                        Text("$deliveredCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusDelivered)
                    }
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = BorderOutline)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("มีปัญหา", fontSize = 11.sp, color = StatusReported)
                        Text("$reportedCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusReported)
                    }
                }
            }

            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    "pending" to "รอดำเนินการ ($pendingCount)",
                    "all" to "ทั้งหมด ($totalCount)",
                    "delivered" to "ส่งแล้ว ($deliveredCount)",
                    "reported" to "มีปัญหา ($reportedCount)"
                )
                filters.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TealPrimary else SurfaceCard)
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("ค้นหาในคิว (ชื่อ, เบอร์, เลขพัสดุ, ที่อยู่)...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "ล้าง")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Content
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary)
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusReported, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage ?: "", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToSettings) {
                            Text("ไปหน้าตั้งค่า Neon")
                        }
                    }
                }
            } else if (filteredStops.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "ไม่พบรายการที่ตรงกับ '$searchQuery'"
                            else if (selectedFilter == "pending") "ไม่มีพัสดุค้างส่ง เยี่ยมมาก!"
                            else "ยังไม่มีจุดส่งในคิว",
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("เพิ่มจุดส่งจากแท็บ 'คลังบ้าน' หรือ 'สแกน' ได้ทันที", fontSize = 12.sp, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredStops, key = { _, item -> item.navId }) { index, stop ->
                        DeliveryStopCard(
                            stop = stop,
                            stopIndex = index + 1,
                            handMode = prefs.handMode,
                            onNavigate = {
                                DriverActions.openMapNavigation(
                                    context,
                                    stop.lat,
                                    stop.lng,
                                    stop.address,
                                    stop.fullName
                                )
                            },
                            onCall = { DriverActions.openPhoneDialer(context, stop.phone) },
                            onMarkDelivered = {
                                scope.launch {
                                    val res = neonClient.updateDeliveryStatus(stop.navId, "delivered")
                                    res.onSuccess {
                                        Toast.makeText(context, "บันทึก: ส่งสำเร็จแล้ว", Toast.LENGTH_SHORT).show()
                                        loadStops()
                                    }.onFailure {
                                        Toast.makeText(context, "อัปเดตไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onOpenReportDialog = { reportingStop = stop },
                            onRevertStatus = {
                                scope.launch {
                                    val res = neonClient.updateDeliveryStatus(stop.navId, "pending")
                                    res.onSuccess {
                                        Toast.makeText(context, "คืนสถานะเป็น: รอดำเนินการ", Toast.LENGTH_SHORT).show()
                                        loadStops()
                                    }.onFailure {
                                        Toast.makeText(context, "อัปเดตไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onEditNote = { editingNoteStop = stop },
                            onDelete = { deletingStop = stop }
                        )
                    }
                }
            }
        }
    }

    // Report problem dialog
    reportingStop?.let { stop ->
        ReportProblemDialog(
            onDismiss = { reportingStop = null },
            onConfirm = { reason, note ->
                reportingStop = null
                scope.launch {
                    val res = neonClient.updateDeliveryStatus(stop.navId, "reported", deliveryNote = note, reportReason = reason)
                    res.onSuccess {
                        Toast.makeText(context, "บันทึกปัญหาเรียบร้อย", Toast.LENGTH_SHORT).show()
                        loadStops()
                    }.onFailure {
                        Toast.makeText(context, "บันทึกไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Edit note dialog
    editingNoteStop?.let { stop ->
        EditDriverNoteDialog(
            currentNote = stop.driverNote ?: "",
            onDismiss = { editingNoteStop = null },
            onSave = { newNote ->
                editingNoteStop = null
                scope.launch {
                    val res = neonClient.updateDriverNote(stop.navId, newNote)
                    res.onSuccess {
                        Toast.makeText(context, "บันทึกโน้ตแล้ว", Toast.LENGTH_SHORT).show()
                        loadStops()
                    }.onFailure {
                        Toast.makeText(context, "บันทึกไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Delete stop dialog
    deletingStop?.let { stop ->
        ConfirmActionDialog(
            title = "ลบจุดส่งออกจากคิว",
            message = "ต้องการนำ ${stop.fullName ?: stop.address ?: "จุดนี้"} ออกจากคิวนำทางใช่หรือไม่?",
            confirmText = "ลบออก",
            isDestructive = true,
            onDismiss = { deletingStop = null },
            onConfirm = {
                deletingStop = null
                scope.launch {
                    val res = neonClient.deleteNavigationStop(stop.navId)
                    res.onSuccess {
                        Toast.makeText(context, "ลบจุดส่งแล้ว", Toast.LENGTH_SHORT).show()
                        loadStops()
                    }.onFailure {
                        Toast.makeText(context, "ลบไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Clear delivered confirm
    if (showClearDeliveredConfirm) {
        ConfirmActionDialog(
            title = "ล้างรายการที่ส่งสำเร็จแล้ว",
            message = "ต้องการลบจุดส่งที่บันทึก 'ส่งแล้ว' ทั้งหมดออกจากคิวนำทางหรือไม่? (ข้อมูลในคลังบ้านยังคงอยู่)",
            confirmText = "ล้างรายการที่ส่งแล้ว",
            onDismiss = { showClearDeliveredConfirm = false },
            onConfirm = {
                showClearDeliveredConfirm = false
                scope.launch {
                    val res = neonClient.clearDeliveredStops(prefs.userId)
                    res.onSuccess {
                        Toast.makeText(context, "ล้างรายการสำเร็จแล้ว", Toast.LENGTH_SHORT).show()
                        loadStops()
                    }.onFailure {
                        Toast.makeText(context, "ทำรายการไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Clear all confirm
    if (showClearAllConfirm) {
        ConfirmActionDialog(
            title = "ล้างคิวจัดส่งทั้งหมด",
            message = "ต้องการล้างจุดจัดส่งทั้งหมดในคิววันนี้หรือไม่? (ข้อมูลในคลังบ้านยังคงอยู่)",
            confirmText = "ล้างทั้งหมด",
            isDestructive = true,
            onDismiss = { showClearAllConfirm = false },
            onConfirm = {
                showClearAllConfirm = false
                scope.launch {
                    val res = neonClient.clearAllStops(prefs.userId)
                    res.onSuccess {
                        Toast.makeText(context, "ล้างคิวทั้งหมดแล้ว", Toast.LENGTH_SHORT).show()
                        loadStops()
                    }.onFailure {
                        Toast.makeText(context, "ทำรายการไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun DeliveryStopCard(
    stop: NavigationHouse,
    stopIndex: Int,
    handMode: String,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onMarkDelivered: () -> Unit,
    onOpenReportDialog: () -> Unit,
    onRevertStatus: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // If Hand Mode is LEFT -> Action Rail is on the LEFT!
            if (handMode == "left") {
                DriverActionRail(
                    stop = stop,
                    onNavigate = onNavigate,
                    onCall = onCall,
                    onMarkDelivered = onMarkDelivered,
                    onOpenReportDialog = onOpenReportDialog,
                    onRevertStatus = onRevertStatus
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Center details content
            Column(modifier = Modifier.weight(1f)) {
                // Header with index and status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(TealDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$stopIndex", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stop.fullName ?: "ไม่มีชื่อผู้รับ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }
                    DeliveryStatusBadge(status = stop.deliveryStatus)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Phone
                if (!stop.phone.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clickable { onCall() }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stop.phone, fontSize = 13.sp, color = StatusDelivered, fontWeight = FontWeight.Medium)
                    }
                }

                // Address
                if (!stop.address.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clickable { onNavigate() }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stop.address, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                    }
                }

                // Tracking numbers
                if (!stop.trackingNumber.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📦 เลขพัสดุ: ${stop.trackingNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealDark,
                            modifier = Modifier
                                .background(TealContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // House note (จุดสังเกตเจ้าของบ้าน)
                if (!stop.note.isNullOrBlank()) {
                    Text(
                        text = "🏠 สังเกต: ${stop.note}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }

                // Driver note
                if (!stop.driverNote.isNullOrBlank()) {
                    Text(
                        text = "📝 คนขับ: ${stop.driverNote}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = StatusPending,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }

                // Report reason if reported
                if (stop.deliveryStatus.lowercase() == "reported" && !stop.reportReason.isNullOrBlank()) {
                    Text(
                        text = "⚠ ปัญหา: ${stop.reportReason} ${stop.deliveryNote ?: ""}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusReported,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }

                // Secondary actions row (Edit Note, Delete)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEditNote, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("โน้ต", fontSize = 11.sp)
                    }
                    TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("ลบ", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            // If Hand Mode is RIGHT -> Action Rail is on the RIGHT!
            if (handMode != "left") {
                Spacer(modifier = Modifier.width(10.dp))
                DriverActionRail(
                    stop = stop,
                    onNavigate = onNavigate,
                    onCall = onCall,
                    onMarkDelivered = onMarkDelivered,
                    onOpenReportDialog = onOpenReportDialog,
                    onRevertStatus = onRevertStatus
                )
            }
        }
    }
}

@Composable
fun DriverActionRail(
    stop: NavigationHouse,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onMarkDelivered: () -> Unit,
    onOpenReportDialog: () -> Unit,
    onRevertStatus: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Map Navigate Button (Prominent)
        FilledIconButton(
            onClick = onNavigate,
            modifier = Modifier.size(46.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TealPrimary)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "นำทาง", tint = Color.White)
        }

        // Phone Button
        FilledTonalIconButton(
            onClick = onCall,
            modifier = Modifier.size(38.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = StatusDeliveredBg,
                contentColor = StatusDelivered
            )
        ) {
            Icon(Icons.Default.Phone, contentDescription = "โทร")
        }

        // Delivery Status action
        if (stop.deliveryStatus.lowercase() == "delivered") {
            // Already delivered: allow reverting
            FilledTonalIconButton(
                onClick = onRevertStatus,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = StatusPendingBg,
                    contentColor = StatusPending
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "คืนสถานะ")
            }
        } else {
            // Not delivered yet: Mark Delivered
            FilledTonalIconButton(
                onClick = onMarkDelivered,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = StatusDeliveredBg,
                    contentColor = StatusDelivered
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "ส่งแล้ว")
            }
        }

        // Report problem button
        if (stop.deliveryStatus.lowercase() != "reported") {
            FilledTonalIconButton(
                onClick = onOpenReportDialog,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = StatusReportedBg,
                    contentColor = StatusReported
                )
            ) {
                Icon(Icons.Default.Warning, contentDescription = "แจ้งปัญหา")
            }
        } else {
            // If already reported, allow reverting to pending
            FilledTonalIconButton(
                onClick = onRevertStatus,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = StatusPendingBg,
                    contentColor = StatusPending
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "คืนสถานะ")
            }
        }
    }
}
