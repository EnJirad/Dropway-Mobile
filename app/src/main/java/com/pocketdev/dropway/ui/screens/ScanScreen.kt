package com.pocketdev.dropway.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.pocketdev.dropway.data.model.LabelFields
import com.pocketdev.dropway.data.model.MatchResult
import com.pocketdev.dropway.data.remote.GeminiVisionClient
import com.pocketdev.dropway.data.remote.NeonClient
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.DriverActions
import com.pocketdev.dropway.util.LabelParser
import com.pocketdev.dropway.util.SearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    neonClient: NeonClient,
    prefs: PreferencesManager,
    onNavigateToQueue: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var houses by remember { mutableStateOf<List<House>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Parsed draft fields
    var draftFields by remember { mutableStateOf(LabelFields()) }
    var isEditingDraft by remember { mutableStateOf(false) }
    var driverNote by remember { mutableStateOf("") }

    // Match Results
    var primaryMatch by remember { mutableStateOf<MatchResult?>(null) }
    var candidates by remember { mutableStateOf<List<MatchResult>>(emptyList()) }
    var hasScanned by remember { mutableStateOf(false) }

    // In-sheet search
    var manualSearchQuery by remember { mutableStateOf("") }
    var manualSearchResults by remember { mutableStateOf<List<House>>(emptyList()) }

    // Load houses from database on mount
    LaunchedEffect(Unit) {
        if (prefs.isConfigured()) {
            val res = neonClient.getHouses(null)
            res.onSuccess { houses = it }
        }
    }

    // Logic to analyze text and match against houses
    fun processScan(text: String, fields: LabelFields? = null) {
        val parsed = fields ?: LabelParser.parseLabel(text)
        draftFields = parsed
        hasScanned = true

        val searchSource = if (text.isNotBlank()) {
            text
        } else {
            listOf(parsed.name, parsed.houseNo, parsed.address, parsed.phone, parsed.tracking)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

        val allMatches = SearchMatcher.matchHouses(houses, searchSource)
        if (allMatches.isNotEmpty()) {
            primaryMatch = allMatches.first()
            candidates = allMatches.drop(1)
        } else {
            primaryMatch = null
            candidates = emptyList()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            if (prefs.geminiApiKey.isNotBlank()) {
                isProcessing = true
                scope.launch {
                    val geminiRes = GeminiVisionClient.readLabelWithGemini(prefs.geminiApiKey, bitmap)
                    isProcessing = false
                    geminiRes.onSuccess { fields ->
                        Toast.makeText(context, "AI อ่านข้อมูลใบปะหน้าสำเร็จ!", Toast.LENGTH_SHORT).show()
                        processScan("", fields)
                    }.onFailure { err ->
                        Toast.makeText(context, "AI อ่านไม่สำเร็จ: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    "ถ่ายรูปแล้ว! ตั้งค่า Gemini API Key ในหน้าตั้งค่าเพื่อให้ระบบอ่านใบปะหน้าอัตโนมัติ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                capturedBitmap = bitmap
                if (prefs.geminiApiKey.isNotBlank()) {
                    isProcessing = true
                    scope.launch {
                        val geminiRes = GeminiVisionClient.readLabelWithGemini(prefs.geminiApiKey, bitmap)
                        isProcessing = false
                        geminiRes.onSuccess { fields ->
                            Toast.makeText(context, "AI อ่านข้อมูลใบปะหน้าสำเร็จ!", Toast.LENGTH_SHORT).show()
                            processScan("", fields)
                        }.onFailure { err ->
                            Toast.makeText(context, "AI อ่านไม่สำเร็จ: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        context,
                        "เลือกรูปแล้ว! ตั้งค่า Gemini API Key ในหน้าตั้งค่าเพื่อให้ AI อ่านใบปะหน้า",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "เปิดรูปภาพไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Manual search debounce
    LaunchedEffect(manualSearchQuery) {
        val q = manualSearchQuery.trim()
        if (q.length >= 2) {
            withContext(Dispatchers.Default) {
                val results = houses.filter { house ->
                    SearchMatcher.matchHouseWithSearch(house, q) > 0
                }.take(6)
                withContext(Dispatchers.Main) {
                    manualSearchResults = results
                }
            }
        } else {
            manualSearchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("สแกนใบปะหน้าพัสดุ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("ตรวจจับบ้านเลขที่, ผู้รับ, เบอร์โทร และเทียบกับคลังบ้าน", fontSize = 11.sp, color = TextMuted)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NeutralBackground),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scanner input methods card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ถ่ายรูปหรือป้อนข้อความใบปะหน้า", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Photo buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ถ่ายรูปใบปะหน้า", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("เลือกจากคลังภาพ", fontSize = 12.sp, color = TextSecondary)
                            }
                        }

                        // Text input for OCR text / Barcode text
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                if (it.isNotBlank()) {
                                    draftFields = LabelParser.parseLabel(it)
                                }
                            },
                            placeholder = {
                                Text(
                                    "หรือวางข้อความใบปะหน้า / เลขพัสดุ เช่น:\nTH012345678 นายมานะ 0891234567 บ้านเลขที่ 1442/3 ม.2 ต.แม่ตาว...",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            singleLine = false,
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Action button
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    processScan(inputText)
                                } else if (capturedBitmap != null && prefs.geminiApiKey.isNotBlank()) {
                                    isProcessing = true
                                    scope.launch {
                                        val res = GeminiVisionClient.readLabelWithGemini(prefs.geminiApiKey, capturedBitmap!!)
                                        isProcessing = false
                                        res.onSuccess { fields ->
                                            processScan("", fields)
                                        }.onFailure {
                                            Toast.makeText(context, "AI อ่านไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "กรุณาถ่ายรูปหรือวางข้อความใบปะหน้า", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("กำลังวิเคราะห์ด้วย OCR/AI...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("วิเคราะห์ใบปะหน้าและเทียบกับคลังบ้าน", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Results Section (ScanResultSheet equivalent)
            if (hasScanned || draftFields.hasAnyField() || primaryMatch != null) {
                item {
                    val activeHouse = primaryMatch?.house
                    val displayAddress = remember(draftFields, activeHouse) {
                        if (activeHouse != null) {
                            activeHouse.address ?: ""
                        } else {
                            LabelParser.composeAddress(draftFields.houseNo, draftFields.address, draftFields.postalCode)
                        }
                    }
                    val displayName = remember(draftFields, activeHouse) {
                        activeHouse?.fullName ?: draftFields.name
                    }
                    val displayPhone = remember(draftFields, activeHouse) {
                        activeHouse?.phone ?: draftFields.phone
                    }
                    val displayTracking = remember(draftFields, primaryMatch) {
                        draftFields.tracking.takeIf { it.isNotBlank() } ?: primaryMatch?.trackingCandidate ?: ""
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Primary Header: Hit or Miss Badge + Match Level
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (activeHouse != null) {
                                        Surface(
                                            color = TealContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealDark, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ตรงกับฐานข้อมูล", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = Color(0xFFFEF3C7),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ข้อมูลจากฉลาก (บ้านใหม่)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                            }
                                        }
                                    }

                                    // Match Level Badge
                                    primaryMatch?.let { m ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val levelText = when (m.level) {
                                            "exact" -> "ตรงมาก"
                                            "high" -> "ใกล้เคียงสูง"
                                            else -> "อาจใช่"
                                        }
                                        val levelColor = when (m.level) {
                                            "exact" -> StatusDelivered
                                            "high" -> TealPrimary
                                            else -> StatusPending
                                        }
                                        Surface(
                                            color = levelColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = levelText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = levelColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                // Edit button toggle
                                TextButton(
                                    onClick = { isEditingDraft = !isEditingDraft },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEditingDraft) "ดูสรุป" else "แก้ไขข้อมูล", fontSize = 12.sp)
                                }
                            }

                            // Match reasons pill
                            primaryMatch?.reasons?.let { reasons ->
                                if (reasons.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = reasons.joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Conflicts alert
                            primaryMatch?.conflicts?.let { conflicts ->
                                if (conflicts.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "⚠️ " + conflicts.joinToString(" • "),
                                            fontSize = 11.sp,
                                            color = Color(0xFF991B1B),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Editable form or Display View
                            if (isEditingDraft) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = draftFields.name,
                                        onValueChange = { draftFields = draftFields.copy(name = it) },
                                        label = { Text("ชื่อผู้รับ", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = draftFields.phone,
                                        onValueChange = { draftFields = draftFields.copy(phone = it) },
                                        label = { Text("เบอร์โทร", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draftFields.houseNo,
                                            onValueChange = { draftFields = draftFields.copy(houseNo = it) },
                                            label = { Text("บ้านเลขที่", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = draftFields.postalCode,
                                            onValueChange = { draftFields = draftFields.copy(postalCode = it) },
                                            label = { Text("รหัสไปรษณีย์", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                    OutlinedTextField(
                                        value = draftFields.address,
                                        onValueChange = { draftFields = draftFields.copy(address = it) },
                                        label = { Text("ที่อยู่", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3
                                    )
                                    OutlinedTextField(
                                        value = draftFields.tracking,
                                        onValueChange = { draftFields = draftFields.copy(tracking = it) },
                                        label = { Text("เลขพัสดุ", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = driverNote,
                                        onValueChange = { driverNote = it },
                                        label = { Text("หมายเหตุผู้ส่ง / จุดสังเกต", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2
                                    )
                                }
                            } else {
                                // Clean display card
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Recipient Name
                                    Text(
                                        text = displayName.takeIf { it.isNotBlank() } ?: "ไม่ทราบชื่อผู้รับ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = TextPrimary
                                    )

                                    // Phone
                                    if (displayPhone.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.clickable { DriverActions.openPhoneDialer(context, displayPhone) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(displayPhone, fontSize = 14.sp, color = StatusDelivered, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    // Address
                                    if (displayAddress.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(15.dp).padding(top = 2.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(displayAddress, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                                        }
                                    }

                                    // Tracking number
                                    if (displayTracking.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("เลขพัสดุ: $displayTracking", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    // House note if present
                                    val noteText = activeHouse?.note?.takeIf { it.isNotBlank() } ?: driverNote.takeIf { it.isNotBlank() }
                                    if (noteText != null) {
                                        Surface(
                                            color = Color(0xFFFEF3C7),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text("📝", fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(noteText, fontSize = 11.sp, color = Color(0xFF78350F))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dropway 3-action buttons (บันทึก / แก้ไข / เพิ่มเข้านำทาง)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. บันทึกเป็นบ้านใหม่ / อัปเดตบ้าน
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val fullAddr = LabelParser.composeAddress(draftFields.houseNo, draftFields.address, draftFields.postalCode)
                                            val houseToSave = House(
                                                id = activeHouse?.id ?: UUID.randomUUID().toString(),
                                                fullName = draftFields.name.takeIf { it.isNotBlank() } ?: activeHouse?.fullName,
                                                phone = draftFields.phone.takeIf { it.isNotBlank() } ?: activeHouse?.phone,
                                                address = fullAddr.takeIf { it.isNotBlank() } ?: activeHouse?.address,
                                                lat = activeHouse?.lat ?: 0.0,
                                                lng = activeHouse?.lng ?: 0.0,
                                                note = driverNote.takeIf { it.isNotBlank() } ?: activeHouse?.note,
                                                quantity = activeHouse?.quantity ?: 1
                                            )
                                            val saveRes = if (activeHouse != null) {
                                                neonClient.updateHouse(houseToSave)
                                            } else {
                                                neonClient.createHouse(houseToSave)
                                            }
                                            saveRes.onSuccess {
                                                Toast.makeText(context, "บันทึกข้อมูลบ้านลงคลังสำเร็จ!", Toast.LENGTH_SHORT).show()
                                                val refreshRes = neonClient.getHouses(null)
                                                refreshRes.onSuccess { houses = it }
                                            }.onFailure {
                                                Toast.makeText(context, "บันทึกไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (activeHouse != null) "อัปเดตบ้าน" else "บันทึกบ้านใหม่", fontSize = 12.sp)
                                }

                                // 2. เพิ่มเข้าคิวนำทาง
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val tracking = displayTracking.takeIf { it.isNotBlank() }
                                            val res = neonClient.addToNavigation(
                                                houseId = activeHouse?.id,
                                                trackingNumber = tracking,
                                                destName = displayName.takeIf { it.isNotBlank() },
                                                destPhone = displayPhone.takeIf { it.isNotBlank() },
                                                destAddress = displayAddress.takeIf { it.isNotBlank() },
                                                userId = prefs.userId
                                            )
                                            res.onSuccess {
                                                Toast.makeText(context, "เพิ่มเข้าคิวจัดส่งเรียบร้อยแล้ว!", Toast.LENGTH_SHORT).show()
                                                onNavigateToQueue()
                                            }.onFailure {
                                                Toast.makeText(context, "เพิ่มไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ เพิ่มเข้านำทาง", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Candidate Recommendations (บ้านใกล้เคียงที่ระบบแนะนำ)
            if (candidates.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = StatusPending, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "บ้านใกล้เคียงที่ระบบแนะนำ (${candidates.size} รายการ)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }

                        candidates.forEach { candidate ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val oldPrimary = primaryMatch
                                        primaryMatch = candidate
                                        candidates = (candidates - candidate) + listOfNotNull(oldPrimary)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.house.fullName ?: "ไม่ระบุชื่อ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = candidate.house.address ?: "ไม่มีที่อยู่",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                        if (candidate.reasons.isNotEmpty()) {
                                            Text(
                                                text = candidate.reasons.joinToString(" • "),
                                                fontSize = 10.sp,
                                                color = TealDark
                                            )
                                        }
                                    }

                                    Surface(
                                        color = NeutralBackground,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "เลือกหลังนี้",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TealPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // In-sheet Manual Search (ค้นหาบ้านเองเมื่อผลไม่ตรง)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ค้นหาบ้านในคลังเอง", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedTextField(
                            value = manualSearchQuery,
                            onValueChange = { manualSearchQuery = it },
                            placeholder = { Text("พิมพ์เลขที่บ้าน หรือชื่อผู้รับเพื่อเลือกจากคลัง...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                if (manualSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { manualSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "ล้าง")
                                    }
                                }
                            }
                        )

                        if (manualSearchResults.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                manualSearchResults.forEach { foundHouse ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                primaryMatch = MatchResult(
                                                    house = foundHouse,
                                                    score = 100,
                                                    level = "exact",
                                                    reasons = listOf("เลือกด้วยตนเอง"),
                                                    trackingCandidate = draftFields.tracking
                                                )
                                                manualSearchQuery = ""
                                                manualSearchResults = emptyList()
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = NeutralBackground
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(foundHouse.fullName ?: "ไม่ระบุชื่อ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(foundHouse.address ?: "", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                            }
                                            Text("เลือก", fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
