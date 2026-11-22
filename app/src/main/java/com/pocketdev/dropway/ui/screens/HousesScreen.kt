package com.pocketdev.dropway.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.repository.LocalHouseRepository
import com.pocketdev.dropway.data.sharing.SharingManager
import com.pocketdev.dropway.data.sharing.VaultMetadata
import com.pocketdev.dropway.ui.components.AddEditHouseDialog
import com.pocketdev.dropway.ui.components.AddToQueueDialog
import com.pocketdev.dropway.ui.components.ConfirmActionDialog
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.DriverActions
import com.pocketdev.dropway.util.SearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousesScreen(
    houseRepo: LocalHouseRepository,
    sharingManager: SharingManager,
    prefs: PreferencesManager,
    onNavigateToSettings: () -> Unit,
    onNavigateToNavigation: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var houses by remember { mutableStateOf<List<House>>(emptyList()) }
    var displayedHouses by remember { mutableStateOf<List<House>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isFiltering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDistrict by remember { mutableStateOf("ทั้งหมด") }

    // Dialog states
    var isAddingHouse by remember { mutableStateOf(false) }
    var editingHouse by remember { mutableStateOf<House?>(null) }
    var deletingHouse by remember { mutableStateOf<House?>(null) }
    var addingToQueueHouse by remember { mutableStateOf<House?>(null) }
    var pendingPaywallMeta by remember { mutableStateOf<VaultMetadata?>(null) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun loadHouses() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                // Initialize local database & seed from bundled assets if first launch
                houseRepo.initialize()
                val list = houseRepo.getHouses()
                houses = list
            } catch (e: Exception) {
                errorMessage = "ไม่สามารถอ่านคลังบ้านจากฐานข้อมูลในเครื่อง: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHouses()
    }

    val dwvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val meta = houseRepo.inspectDwv(bytes)
                        if (meta == null) {
                            Toast.makeText(context, "ไฟล์ไม่ใช่รูปแบบ Dropway Vault (.dwv) ที่ถูกต้อง", Toast.LENGTH_LONG).show()
                        } else if (!prefs.isSubscribed) {
                            pendingPaywallMeta = meta
                            pendingImportBytes = bytes
                        } else {
                            val imp = houseRepo.importDwvVault(bytes)
                            imp.onSuccess {
                                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                                loadHouses()
                            }.onFailure {
                                Toast.makeText(context, "นำเข้าไม่สำเร็จ: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Districts list (computed asynchronously in background for large datasets)
    var districts by remember { mutableStateOf<List<String>>(listOf("ทั้งหมด")) }

    LaunchedEffect(houses) {
        if (houses.isEmpty()) {
            districts = listOf("ทั้งหมด")
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val list = houses.mapNotNull { SearchMatcher.extractDistrict(it.address) }.distinct().sorted()
            withContext(Dispatchers.Main) {
                districts = listOf("ทั้งหมด") + list
            }
        }
    }

    // Optimized background filtering, debouncing, and ranking (offloaded to Dispatchers.Default for 60fps performance)
    LaunchedEffect(houses, searchQuery, selectedDistrict) {
        isFiltering = true
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(250) // Debounce rapid keystrokes
        }
        withContext(Dispatchers.Default) {
            var list = houses

            // District filter
            if (selectedDistrict != "ทั้งหมด") {
                list = list.filter {
                    SearchMatcher.extractDistrict(it.address) == selectedDistrict
                }
            }

            // Search query with ranking
            val filtered = if (searchQuery.isNotBlank()) {
                val scored = list.mapNotNull { house ->
                    val score = SearchMatcher.matchHouseWithSearch(house, searchQuery)
                    if (score > 0) house to score else null
                }
                scored.sortedByDescending { it.second }.map { it.first }
            } else {
                list
            }
            withContext(Dispatchers.Main) {
                displayedHouses = filtered
                isFiltering = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("คลังบ้าน", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (houses.isNotEmpty()) {
                                Surface(
                                    color = TealContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${displayedHouses.size} / ${houses.size} หลัง",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TealDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "ค้นหาตามเลขที่บ้าน, ชื่อ, เบอร์โทร, ตำบล/อำเภอ",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    // Export .dwv (Encrypted Vault)
                    IconButton(
                        onClick = {
                            scope.launch {
                                val res = houseRepo.exportDwvVault("คลังบ้าน ${prefs.driverName}")
                                res.onSuccess { file ->
                                    try {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "แชร์คลังบ้านแบบเข้ารหัส (.dwv)"))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "สร้างไฟล์ .dwv สำเร็จ: ${file.name}", Toast.LENGTH_LONG).show()
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "ส่งออกไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "แชร์คลังบ้าน (.dwv)", tint = TextSecondary)
                    }
                    // Import .dwv (Encrypted Vault)
                    IconButton(onClick = { dwvPickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "นำเข้าคลังบ้าน (.dwv)", tint = TextSecondary)
                    }
                    IconButton(onClick = { loadHouses() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช", tint = TextSecondary)
                    }
                    Button(
                        onClick = { isAddingHouse = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("เพิ่มบ้าน", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("ค้นหาบ้าน เช่น 5, 1442, ศรุตา, แม่ตาว...", fontSize = 13.sp) },
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

            // District Chips
            if (districts.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(districts) { district ->
                        val isSelected = selectedDistrict == district
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) TealPrimary else SurfaceCard)
                                .clickable { selectedDistrict = district }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = district,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TealPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("กำลังโหลดคลังบ้านในเครื่อง...", fontSize = 12.sp, color = TextMuted)
                    }
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
                            Text("ไปหน้าตั้งค่า")
                        }
                    }
                }
            } else if (displayedHouses.isEmpty()) {
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
                            if (searchQuery.isNotEmpty()) "ไม่พบบ้านที่ตรงกับ '$searchQuery'"
                            else "ยังไม่มีข้อมูลบ้านในระบบ",
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { isAddingHouse = true }) {
                                Text("+ เพิ่มบ้านใหม่")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        val count = houseRepo.initialize()
                                        isLoading = false
                                        Toast.makeText(context, "โหลดข้อมูลแม่สอด $count จุดแล้ว", Toast.LENGTH_SHORT).show()
                                        loadHouses()
                                    }
                                }
                            ) {
                                Text("ใส่ข้อมูลตัวอย่างแม่สอด")
                            }
                        }
                    }
                }
            } else {
                // High-performance virtualized list: Only visible items are rendered
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = displayedHouses,
                        key = { it.id },
                        contentType = { "house_item" }
                    ) { house ->
                        HouseCard(
                            house = house,
                            isSubscribed = prefs.isSubscribed,
                            onAddToQueue = { addingToQueueHouse = house },
                            onNavigate = {
                                if ((house.sourceType == "shared" || house.isShared) && !prefs.isSubscribed) {
                                    Toast.makeText(context, "พิกัดนี้ถูกล็อกโดยระบบ Subscription กรุณาสมัครเพื่อดูพิกัด", Toast.LENGTH_LONG).show()
                                } else {
                                    DriverActions.openMapNavigation(
                                        context,
                                        house.lat,
                                        house.lng,
                                        house.address,
                                        house.fullName
                                    )
                                }
                            },
                            onCall = { DriverActions.openPhoneDialer(context, house.phone) },
                            onEdit = { editingHouse = house },
                            onDelete = { deletingHouse = house }
                        )
                    }
                }
            }
        }
    }

    // Add House Dialog
    if (isAddingHouse) {
        AddEditHouseDialog(
            initialHouse = null,
            onDismiss = { isAddingHouse = false },
            onSave = { newHouse ->
                isAddingHouse = false
                scope.launch {
                    val res = houseRepo.saveHouse(newHouse)
                    res.onSuccess {
                        Toast.makeText(context, "เพิ่มบ้านสำเร็จแล้ว", Toast.LENGTH_SHORT).show()
                        loadHouses()
                    }.onFailure {
                        Toast.makeText(context, "เพิ่มไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Edit House Dialog
    editingHouse?.let { house ->
        AddEditHouseDialog(
            initialHouse = house,
            onDismiss = { editingHouse = null },
            onSave = { updatedHouse ->
                editingHouse = null
                scope.launch {
                    val res = houseRepo.saveHouse(updatedHouse)
                    res.onSuccess {
                        Toast.makeText(context, "อัปเดตข้อมูลบ้านสำเร็จ", Toast.LENGTH_SHORT).show()
                        loadHouses()
                    }.onFailure {
                        Toast.makeText(context, "อัปเดตไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Delete House Dialog (guarded by confirmation dialog)
    deletingHouse?.let { house ->
        val houseDisplayName = house.fullName?.takeIf { it.isNotBlank() }
            ?: house.address?.takeIf { it.isNotBlank() }
            ?: "หลังที่เลือก"
        ConfirmActionDialog(
            title = "ยืนยันการลบบ้าน",
            message = "คุณต้องการลบข้อมูลบ้าน '$houseDisplayName' ออกจากคลังบ้านอย่างถาวรหรือไม่?\nการดำเนินการนี้ไม่สามารถยกเลิกได้",
            confirmText = "ยืนยันลบบ้าน",
            isDestructive = true,
            onDismiss = { deletingHouse = null },
            onConfirm = {
                val targetId = house.id
                deletingHouse = null
                scope.launch {
                    val res = houseRepo.deleteHouse(targetId)
                    res.onSuccess {
                        Toast.makeText(context, "ลบข้อมูลบ้านสำเร็จแล้ว", Toast.LENGTH_SHORT).show()
                        loadHouses()
                    }.onFailure {
                        Toast.makeText(context, "ลบไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Add to Navigation Queue Dialog
    addingToQueueHouse?.let { house ->
        AddToQueueDialog(
            houseName = house.fullName ?: house.address,
            onDismiss = { addingToQueueHouse = null },
            onConfirm = { trackingNumber ->
                addingToQueueHouse = null
                scope.launch {
                    val res = houseRepo.addToNavigation(
                        house = house,
                        trackingNumber = trackingNumber,
                        navNote = null
                    )
                    res.onSuccess {
                        Toast.makeText(context, "เพิ่มเข้าคิวจัดส่งเรียบร้อยแล้ว!", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "เพิ่มไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Paywall Dialog for Shared Vault Import
    pendingPaywallMeta?.let { meta ->
        AlertDialog(
            onDismissRequest = {
                pendingPaywallMeta = null
                pendingImportBytes = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = StatusReported)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ปลดล็อกคลังบ้าน (.dwv)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "คลังบ้าน: ${meta.vaultTitle} (${meta.recordCount} หลัง)",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "ผู้สร้างและแชร์: ${meta.creatorName}",
                        fontSize = 13.sp,
                        color = TealDark
                    )
                    Divider(color = BorderOutline)
                    Text(
                        "ไฟล์นี้ถูกเข้ารหัสความปลอดภัยระดับชิป เพื่อคุ้มครองข้อมูลของพนักงานส่งของ จำเป็นต้องมีแพ็กเกจ Dropway Active Subscription เพื่อปลดล็อกและนำเข้าพิกัด",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingPaywallMeta = null
                        pendingImportBytes = null
                        onNavigateToSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("สมัครสมาชิก / ใส่รหัส License")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingPaywallMeta = null
                        pendingImportBytes = null
                    }
                ) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}

@Composable
fun HouseCard(
    house: House,
    isSubscribed: Boolean = true,
    onAddToQueue: () -> Unit,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val houseNo = remember(house.address) { SearchMatcher.extractHouseNo(house.address) }
    val district = remember(house.address) { SearchMatcher.extractDistrict(house.address) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header Row: House Number Badge + District Badge + Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Shared badge with Subscription Lock indication
                    if (house.sourceType == "shared" || house.isShared) {
                        Surface(
                            color = if (isSubscribed) Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                if (!isSubscribed) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "ล็อกพิกัด",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (isSubscribed) "แชร์จาก ${house.creatorName ?: "คนขับท่านอื่น"}" else "ล็อกพิกัด (แชร์โดย ${house.creatorName ?: "เพื่อนร่วมงาน"})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSubscribed) Color(0xFF1D4ED8) else Color(0xFFDC2626)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // House No Badge
                    if (!houseNo.isNullOrBlank()) {
                        Surface(
                            color = TealContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "บ้านเลขที่ $houseNo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealDark,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // District Badge
                    if (!district.isNullOrBlank()) {
                        Surface(
                            color = NeutralBackground,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = district,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Edit button (positioned immediately to the left of Delete button)
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "แก้ไขข้อมูลบ้าน",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "ลบบ้านออกจากคลัง",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Recipient Full Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = house.fullName?.takeIf { it.isNotBlank() } ?: "ไม่ระบุชื่อผู้รับ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }

            // Phone
            if (!house.phone.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier
                        .clickable { onCall() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = house.phone,
                        fontSize = 13.sp,
                        color = StatusDelivered,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Address
            if (!house.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier
                        .clickable { onNavigate() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = house.address,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            // Sticky Note (matching Dropway web design)
            if (!house.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFEF3C7), // warm amber sticky note
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF78350F),
                            modifier = Modifier.size(13.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = house.note,
                            fontSize = 11.sp,
                            color = Color(0xFF78350F), // amber 900
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add to navigation queue button
                Button(
                    onClick = onAddToQueue,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ เพิ่มเข้าคิวจัดส่ง", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Call Button
                OutlinedButton(
                    onClick = onCall,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "โทร", tint = StatusDelivered, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("โทร", fontSize = 12.sp, color = StatusDelivered)
                }

                // Map Button
                OutlinedButton(
                    onClick = onNavigate,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "แผนที่", tint = TealPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("นำทาง", fontSize = 12.sp, color = TealDark)
                }
            }
        }
    }
}
