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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.model.LabelFields
import com.pocketdev.dropway.data.model.MatchResult
import com.pocketdev.dropway.data.model.SameParcelMeta
import com.pocketdev.dropway.data.remote.GeminiVisionClient
import com.pocketdev.dropway.data.repository.LocalHouseRepository
import com.pocketdev.dropway.ui.components.BlockingLoader
import com.pocketdev.dropway.ui.components.CameraViewfinder
import com.pocketdev.dropway.ui.components.ConfirmActionDialog
import com.pocketdev.dropway.ui.components.ScanResultSheet
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.ImageEnhancer
import com.pocketdev.dropway.util.LabelParser
import com.pocketdev.dropway.util.LabelValidator
import com.pocketdev.dropway.util.SearchMatcher
import com.pocketdev.dropway.util.SoundFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    houseRepo: LocalHouseRepository,
    prefs: PreferencesManager,
    onNavigateToQueue: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var houses by remember { mutableStateOf<List<House>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    // Multi-stage loader states
    var loading by remember { mutableStateOf(false) }
    var loadingTitle by remember { mutableStateOf("กำลังอ่านฉลาก") }
    var loadingStage by remember { mutableStateOf<String?>(null) }
    var lastElapsedMs by remember { mutableStateOf<Long?>(null) }

    // Scan result states
    var sheetOpen by remember { mutableStateOf(false) }
    var matchedHouse by remember { mutableStateOf<House?>(null) }
    var primaryMatch by remember { mutableStateOf<MatchResult?>(null) }
    var candidates by remember { mutableStateOf<List<MatchResult>>(emptyList()) }
    var draftFields by remember { mutableStateOf(LabelFields()) }
    var trackingNumber by remember { mutableStateOf<String?>(null) }
    var sameParcel by remember { mutableStateOf<SameParcelMeta?>(null) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var isAddingToNav by remember { mutableStateOf(false) }
    var addedSuccess by remember { mutableStateOf(false) }
    var lastScannedCode by remember { mutableStateOf("") }

    // Manual input & Duplicate confirm dialog states
    var manualCodeInput by remember { mutableStateOf("") }
    var pendingDuplicateForm by remember { mutableStateOf<LabelFields?>(null) }
    var showDuplicateConfirmDialog by remember { mutableStateOf(false) }

    // Cooldown timestamp to prevent immediate duplicate re-scanning
    var cooldownUntil by remember { mutableStateOf(0L) }

    // Load houses from local vault on mount
    LaunchedEffect(Unit) {
        houses = houseRepo.getHouses()
    }

    // Process scan from image or text
    fun processScan(bitmap: Bitmap?, rawText: String?, isAuto: Boolean = false) {
        if (loading || System.currentTimeMillis() < cooldownUntil) return

        val startedAt = System.currentTimeMillis()
        loading = true
        loadingTitle = "กำลังอ่านฉลาก"
        loadingStage = "กำลังเตรียมรูป…"
        sheetOpen = false
        matchedHouse = null
        primaryMatch = null
        candidates = emptyList()
        sameParcel = null
        noticeMessage = null
        addedSuccess = false
        lastScannedCode = ""

        scope.launch {
            try {
                var fields = LabelFields()

                // 1. Read label with AI or OCR
                if (bitmap != null && prefs.geminiApiKey.isNotBlank()) {
                    loadingStage = "กำลังอ่านฉลากด้วย AI และ OCR พร้อมกัน…"
                    val geminiRes = GeminiVisionClient.readLabelWithGemini(prefs.geminiApiKey, bitmap)
                    geminiRes.onSuccess { parsed ->
                        fields = LabelValidator.cleanFields(parsed)
                    }.onFailure { err ->
                        noticeMessage = "AI อ่านไม่สมบูรณ์ (${err.message}) กรุณาตรวจข้อมูล"
                    }
                } else if (!rawText.isNullOrBlank()) {
                    loadingStage = "กำลังประมวลผลข้อความฉลาก…"
                    val parsed = LabelParser.parseLabel(rawText)
                    fields = LabelValidator.cleanFields(parsed)
                } else if (bitmap != null) {
                    noticeMessage = "ตั้งค่า Gemini API Key ในหน้าตั้งค่าเพื่อให้ระบบอ่านรูปอัตโนมัติ"
                }

                draftFields = fields
                val effectiveTracking = fields.tracking.takeIf { it.isNotBlank() }
                    ?: LabelParser.extractTracking(rawText ?: "")
                trackingNumber = effectiveTracking

                // 2. Check duplicate parcel in queue
                if (effectiveTracking != null) {
                    val dupRes = houseRepo.checkExistingParcel(effectiveTracking)
                    dupRes.onSuccess { sp ->
                        sameParcel = sp
                    }
                }

                // 3. Search and Match with houses database
                loadingStage = "กำลังเทียบกับคลังบ้าน…"
                val searchText = when {
                    !rawText.isNullOrBlank() -> rawText
                    fields.hasAnyField() -> listOf(fields.name, fields.houseNo, fields.address, fields.phone, fields.tracking)
                        .filter { it.isNotBlank() }.joinToString(" ")
                    else -> ""
                }

                lastScannedCode = searchText.take(200)

                if (searchText.isNotBlank()) {
                    val allMatches = withContext(Dispatchers.Default) {
                        SearchMatcher.matchHouses(houses, searchText)
                    }
                    if (allMatches.isNotEmpty()) {
                        val top = allMatches.first()
                        if (top.level == "exact" || top.level == "high") {
                            matchedHouse = top.house
                            primaryMatch = top
                            candidates = allMatches.drop(1)
                            SoundFeedback.playSuccessSound(context)
                        } else {
                            matchedHouse = null
                            primaryMatch = top
                            candidates = allMatches
                        }
                    } else {
                        matchedHouse = null
                        primaryMatch = null
                        candidates = emptyList()
                    }
                }

                lastElapsedMs = System.currentTimeMillis() - startedAt
                loading = false
                sheetOpen = true

                if (matchedHouse != null) {
                    Toast.makeText(context, "พบบ้านแล้ว", Toast.LENGTH_SHORT).show()
                } else if (fields.hasAnyField()) {
                    Toast.makeText(context, "อ่านข้อมูลฉลากสำเร็จ", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ไม่พบบ้านที่ตรงกับฉลากนี้", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loading = false
                sheetOpen = true
                noticeMessage = "อ่านฉลากไม่สำเร็จ: ${e.message}"
                Toast.makeText(context, "อ่านฉลากไม่สำเร็จ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Add to Navigation queue with Duplicate check
    fun handleAddToNavigation(form: LabelFields, force: Boolean = false) {
        if (isAddingToNav) return
        isAddingToNav = true

        scope.launch {
            val tracking = form.tracking.trim().takeIf { it.isNotBlank() }
            val fullAddr = LabelParser.composeAddress(form.houseNo, form.address, form.postalCode)
            val targetHouse = matchedHouse ?: House(
                id = UUID.randomUUID().toString(),
                fullName = form.name.trim().takeIf { it.isNotBlank() },
                phone = form.phone.trim().takeIf { it.isNotBlank() },
                address = fullAddr.takeIf { it.isNotBlank() },
                lat = null,
                lng = null
            )

            val addRes = houseRepo.addToNavigation(
                house = targetHouse,
                trackingNumber = tracking,
                navNote = null
            )

            isAddingToNav = false

            addRes.onSuccess {
                addedSuccess = true
                SoundFeedback.playSuccessSound(context)
                Toast.makeText(context, "เพิ่มเข้าคิวนำทางวันนี้เรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()

                // Immediately close sheet and resume camera with cooldown
                sheetOpen = false
                cooldownUntil = System.currentTimeMillis() + 1800 // AUTO_COOLDOWN_MS
            }.onFailure { err ->
                Toast.makeText(context, "เพิ่มไม่สำเร็จ: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Save or update house in local vault
    fun handleSaveHouse(form: LabelFields, noteText: String) {
        if (isAddingToNav) return
        isAddingToNav = true

        scope.launch {
            val fullAddr = LabelParser.composeAddress(form.houseNo, form.address, form.postalCode)
            val houseToSave = House(
                id = matchedHouse?.id ?: UUID.randomUUID().toString(),
                fullName = form.name.trim().takeIf { it.isNotBlank() } ?: matchedHouse?.fullName,
                phone = form.phone.trim().takeIf { it.isNotBlank() } ?: matchedHouse?.phone,
                address = fullAddr.takeIf { it.isNotBlank() } ?: matchedHouse?.address,
                lat = matchedHouse?.lat,
                lng = matchedHouse?.lng,
                note = noteText.trim().takeIf { it.isNotBlank() } ?: matchedHouse?.note,
                quantity = matchedHouse?.quantity ?: 1
            )

            val res = houseRepo.saveHouse(houseToSave)

            isAddingToNav = false

            res.onSuccess { saved ->
                matchedHouse = saved
                primaryMatch = null
                SoundFeedback.playSuccessSound(context)
                Toast.makeText(context, if (matchedHouse != null) "บันทึกการแก้ไขแล้ว" else "บันทึกเป็นบ้านใหม่แล้ว", Toast.LENGTH_SHORT).show()
                // Refresh houses cache
                houses = houseRepo.getHouses()
            }.onFailure { err ->
                Toast.makeText(context, "บันทึกไม่สำเร็จ: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fallback camera launcher
    val photoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            SoundFeedback.playCaptureSound(context)
            processScan(ImageEnhancer.sharpenBitmap(bitmap), null)
        }
    }

    // Gallery picker launcher
    val galleryPickerLauncher = rememberLauncherForActivityResult(
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
                SoundFeedback.playCaptureSound(context)
                processScan(ImageEnhancer.sharpenBitmap(bitmap), null)
            } catch (e: Exception) {
                Toast.makeText(context, "เปิดรูปภาพไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("สแกนใบปะหน้า", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("เปิดกล้องแล้วจ่อใบปะหน้าให้อยู่กลางกรอบ ระบบจะถ่ายและอ่านให้เอง", fontSize = 11.sp, color = TextMuted)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Camera Viewfinder Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    CameraViewfinder(
                        scanning = scanning,
                        onStartScanning = { scanning = true },
                        onStopScanning = { scanning = false },
                        onCapture = { capturedBitmap ->
                            SoundFeedback.playCaptureSound(context)
                            processScan(capturedBitmap, null, isAuto = true)
                        },
                        isProcessing = loading
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Camera Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (scanning) {
                            OutlinedButton(
                                onClick = { scanning = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("หยุดกล้อง")
                            }
                        } else {
                            Button(
                                onClick = { scanning = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("เปิดกล้องสแกน")
                            }
                        }

                        OutlinedButton(
                            onClick = { photoCaptureLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ถ่ายรูปเอง (สำรอง)", fontSize = 12.sp)
                        }
                    }

                    // Gallery fallback button
                    OutlinedButton(
                        onClick = { galleryPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เลือกรูปจากคลังภาพ", fontSize = 12.sp, color = TextSecondary)
                    }

                    // Elapsed Time Status
                    lastElapsedMs?.let { ms ->
                        Text(
                            text = "รอบล่าสุดใช้เวลา ${String.format("%.1f", ms / 1000.0)} วินาที (ตั้งแต่ถ่ายภาพจนได้ผล)",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (lastScannedCode.isNotBlank()) {
                        Text(
                            text = "รหัสที่อ่านได้: $lastScannedCode",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // 2. Manual Code / Text Input Section (กรอกรหัสเอง)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("กรอกรหัสเอง", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "ใช้เมื่อกล้องใช้ไม่ได้ — วาง UUID ของบ้าน หรือข้อความฉลาก / ชื่อ / เบอร์ / ที่อยู่",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualCodeInput,
                            onValueChange = { manualCodeInput = it },
                            placeholder = { Text("UUID หรือข้อความใบปะหน้า...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                if (manualCodeInput.isNotBlank()) {
                                    processScan(null, manualCodeInput)
                                } else {
                                    Toast.makeText(context, "กรุณากรอกข้อความก่อนค้นหา", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ค้นหา")
                        }
                    }
                }
            }
        }
    }

    // Blocking Loader
    BlockingLoader(
        open = loading,
        title = loadingTitle,
        stage = loadingStage,
        message = "ระบบกำลังอ่านและเทียบข้อมูล กรุณารอสักครู่"
    )

    // Result Sheet Dialog
    ScanResultSheet(
        open = sheetOpen && !loading,
        house = matchedHouse,
        match = primaryMatch,
        draft = draftFields,
        candidates = candidates,
        tracking = trackingNumber,
        sameParcel = sameParcel,
        notice = noticeMessage,
        busy = isAddingToNav,
        added = addedSuccess,
        onClose = {
            sheetOpen = false
            cooldownUntil = System.currentTimeMillis() + 1800
        },
        onAdd = { form -> handleAddToNavigation(form, force = false) },
        onSave = { form, note -> handleSaveHouse(form, note) },
        onSelectCandidate = { cand ->
            val prevPrimary = primaryMatch
            primaryMatch = cand
            matchedHouse = cand.house
            candidates = (candidates - cand) + listOfNotNull(prevPrimary)
            addedSuccess = false
        },
        allHouses = houses,
        onScanAgain = {
            sheetOpen = false
            cooldownUntil = 0L
            matchedHouse = null
            primaryMatch = null
            draftFields = LabelFields()
            candidates = emptyList()
            trackingNumber = null
            sameParcel = null
            addedSuccess = false
            scanning = true
        }
    )

    // Duplicate tracking confirmation dialog
    if (showDuplicateConfirmDialog) {
        val form = pendingDuplicateForm
        ConfirmActionDialog(
            title = "เลขพัสดุนี้ถูกบันทึกวันนี้แล้ว",
            message = "เลขพัสดุ ${form?.tracking ?: ""} ถูกบันทึกในระบบวันนี้แล้ว ต้องการเพิ่มซ้ำหรือไม่?",
            confirmText = "เพิ่มซ้ำ",
            onDismiss = {
                showDuplicateConfirmDialog = false
                pendingDuplicateForm = null
            },
            onConfirm = {
                showDuplicateConfirmDialog = false
                form?.let { handleAddToNavigation(it, force = true) }
                pendingDuplicateForm = null
            }
        )
    }
}
