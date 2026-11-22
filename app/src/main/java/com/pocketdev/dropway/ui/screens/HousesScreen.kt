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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.remote.NeonClient
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
    neonClient: NeonClient,
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

    fun loadHouses() {
        if (!prefs.isConfigured()) {
            errorMessage = "กรุณาตั้งค่า Neon Database URL ในหน้าตั้งค่าก่อนใช้งาน"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val res = neonClient.getHouses(null)
            isLoading = false
            res.onSuccess {
                houses = it
            }.onFailure {
                errorMessage = "ดึงข้อมูลคลังบ้านไม่สำเร็จ: ${it.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHouses()
    }

    // Districts list
    val districts = remember(houses) {
        val list = houses.mapNotNull { SearchMatcher.extractDistrict(it.address) }.distinct()
        listOf("ทั้งหมด") + list
    }

    // Optimized background filtering and ranking (offloaded to Dispatchers.Default for 60fps performance)
    LaunchedEffect(houses, searchQuery, selectedDistrict) {
        isFiltering = true
        withContext(Dispatchers.Default) {
            var list = houses

            // District filter
            if (selectedDistrict != "ทั้งหมด") {
                list = list.filter {
                    val d = SearchMatcher.extractDistrict(it.address)
                    d == selectedDistrict
                }
            }

            // Search query with ranking
            val filtered = if (searchQuery.isNotBlank()) {
                val scored = list.map { house ->
                    val score = SearchMatcher.matchHouseWithSearch(house, searchQuery)
                    house to score
                }.filter { it.second > 0 }
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
                    IconButton(onClick = { loadHouses() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingHouse = true },
                containerColor = TealPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "เพิ่มบ้านใหม่")
            }
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
                        Text("กำลังโหลดข้อมูลบ้านจาก Neon...", fontSize = 12.sp, color = TextMuted)
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
                            Text("ไปหน้าตั้งค่า Neon")
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
                                        val res = neonClient.seedMaeSotData()
                                        isLoading = false
                                        res.onSuccess {
                                            Toast.makeText(context, "ใส่ข้อมูลตัวอย่างแม่สอด $it จุดแล้ว", Toast.LENGTH_SHORT).show()
                                            loadHouses()
                                        }.onFailure {
                                            Toast.makeText(context, "ผิดพลาด: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
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
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = displayedHouses,
                        key = { it.id },
                        contentType = { "house_item" }
                    ) { house ->
                        HouseCard(
                            house = house,
                            onAddToQueue = { addingToQueueHouse = house },
                            onNavigate = {
                                DriverActions.openMapNavigation(
                                    context,
                                    house.lat,
                                    house.lng,
                                    house.address,
                                    house.fullName
                                )
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
                    val res = neonClient.createHouse(newHouse)
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
                    val res = neonClient.updateHouse(updatedHouse)
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

    // Delete House Dialog
    deletingHouse?.let { house ->
        ConfirmActionDialog(
            title = "ลบข้อมูลบ้าน",
            message = "ต้องการลบบ้าน '${house.fullName ?: house.address ?: "หลังนี้"}' ออกจากคลังบ้านอย่างถาวรหรือไม่?",
            confirmText = "ลบบ้าน",
            isDestructive = true,
            onDismiss = { deletingHouse = null },
            onConfirm = {
                deletingHouse = null
                scope.launch {
                    val res = neonClient.deleteHouse(house.id)
                    res.onSuccess {
                        Toast.makeText(context, "ลบบ้านสำเร็จแล้ว", Toast.LENGTH_SHORT).show()
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
                    val res = neonClient.addToNavigation(
                        houseId = house.id,
                        trackingNumber = trackingNumber,
                        userId = prefs.userId
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
}

@Composable
fun HouseCard(
    house: House,
    onAddToQueue: () -> Unit,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val houseNo = remember(house.address) {
        val head = (house.address ?: "").split(Regex("""ม\.|หมู่|moo|\bm\.""", RegexOption.IGNORE_CASE)).firstOrNull() ?: (house.address ?: "")
        Regex("""\d{1,5}(?:/\d{1,4})?""").find(head)?.value
    }
    val district = remember(house.address) {
        SearchMatcher.extractDistrict(house.address)
    }

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
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "แก้ไข", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = TextMuted, modifier = Modifier.size(16.dp))
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
                        Text("📝", fontSize = 12.sp)
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
