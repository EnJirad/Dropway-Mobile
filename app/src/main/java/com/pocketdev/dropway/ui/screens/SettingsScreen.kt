package com.pocketdev.dropway.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.repository.LocalHouseRepository
import com.pocketdev.dropway.data.sharing.SubscriptionConfig
import com.pocketdev.dropway.data.sharing.VaultCryptoManager
import com.pocketdev.dropway.ui.components.ConfirmActionDialog
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.MapProvider
import com.pocketdev.dropway.util.MapProviderRegistry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    houseRepo: LocalHouseRepository,
    prefs: PreferencesManager,
    onConnectionUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var driverNameInput by remember { mutableStateOf(prefs.driverName) }
    var geminiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var selectedHandMode by remember { mutableStateOf(prefs.handMode) }

    var selectedMapProvider by remember {
        mutableStateOf(MapProviderRegistry.getById(prefs.defaultMapLayer))
    }
    var showMapPickerSheet by remember { mutableStateOf(false) }

    var isSubscribed by remember { mutableStateOf(prefs.isSubscribed) }
    var subscriptionExpiryMs by remember { mutableStateOf(prefs.subscriptionExpiryMs) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var licenseInput by remember { mutableStateOf("") }
    var licenseError by remember { mutableStateOf<String?>(null) }

    var houseCount by remember { mutableStateOf(0) }
    var isSeedingData by remember { mutableStateOf(false) }
    var showSeedConfirm by remember { mutableStateOf(false) }

    fun refreshStats() {
        scope.launch {
            houseCount = houseRepo.getHouseCount()
            isSubscribed = prefs.isSubscribed
            subscriptionExpiryMs = prefs.subscriptionExpiryMs
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    // .dwv File Picker Launcher
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
                            Toast.makeText(context, "ต้องมีแพ็กเกจ Dropway Active Subscription เพื่อปลดล็อกคลังบ้านนี้", Toast.LENGTH_LONG).show()
                        } else {
                            val imp = houseRepo.importDwvVault(bytes)
                            imp.onSuccess {
                                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                                refreshStats()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ตั้งค่าระบบคลังและแผนที่", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
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
            // 1. Driver Profile Card (Creator Attribution)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ข้อมูลพนักงานส่งของ (ผู้สร้างคลัง)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        "ชื่อของคุณจะถูกผูกเข้ากับพิกัดบ้านที่คุณสร้าง และแสดงเป็นเครดิตผู้สร้างเมื่อแชร์ไฟล์ .dwv ให้เพื่อนร่วมงาน",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = driverNameInput,
                        onValueChange = {
                            driverNameInput = it
                            prefs.driverName = it
                        },
                        label = { Text("ชื่อหรือรหัสสายส่งของคุณ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("รหัสประจำเครื่อง:", fontSize = 12.sp, color = TextMuted)
                        Text(prefs.userId, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    }
                }
            }

            // 2. Default Map Provider Card (16 Layers)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("แผนที่เริ่มต้น (รองรับ 16 แบบ)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        "เลือกชนิดแผนที่หลักที่จะเปิดใช้งานทันทีในหน้าหมุดพิกัดและระบบนำทาง",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Surface(
                        color = NeutralBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMapPickerSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    selectedMapProvider.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TealDark
                                )
                                Text(
                                    selectedMapProvider.description,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Button(
                                onClick = { showMapPickerSheet = true },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("เปลี่ยน", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. Dropway Subscription & Licensing Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = if (isSubscribed) StatusDelivered else StatusReported)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("แพ็กเกจสมาชิก Dropway", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Surface(
                            color = if (isSubscribed) StatusDeliveredBg else StatusReportedBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "Active Pro" else "Free Plan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSubscribed) StatusDelivered else StatusReported,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        "ค่าบริการ: ${SubscriptionConfig.monthlyPriceThb} บาท/เดือน สำหรับปลดล็อกการนำเข้าคลังบ้านที่เพื่อนร่วมงานแชร์มา และใช้งานแผนที่ดาวเทียมทุกประเภท",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    if (isSubscribed && subscriptionExpiryMs > 0) {
                        val expiryDate = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("th", "TH")).format(Date(subscriptionExpiryMs))
                        Text(
                            "หมดอายุ: $expiryDate",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealDark
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                licenseError = null
                                showLicenseDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ใส่รหัส License", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val thirtyDaysMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                                prefs.isSubscribed = true
                                prefs.subscriptionExpiryMs = thirtyDaysMs
                                refreshStats()
                                Toast.makeText(context, "เปิดใช้งาน Dropway Pro 30 วันสำเร็จ!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                        ) {
                            Text("เปิดใช้งาน Pro (30 วัน)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. Local Vault Management & .dwv Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("จัดการคลังข้อมูลในเครื่อง (Offline Vault)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        "ข้อมูลทั้งหมดเก็บในฐานข้อมูล SQLite ภายในเครื่อง ไม่เชื่อมต่อเซิร์ฟเวอร์ภายนอก ทำงานได้ 100% แม้ไม่มีสัญญาณอินเทอร์เน็ต",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Surface(
                        color = NeutralBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("จำนวนบ้านในเครื่องขณะนี้:", fontSize = 13.sp, color = TextPrimary)
                            Text("$houseCount หลัง", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export .dwv
                        Button(
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
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ส่งออก .dwv", fontSize = 12.sp)
                        }

                        // Import .dwv
                        OutlinedButton(
                            onClick = { dwvPickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("นำเข้า .dwv", fontSize = 12.sp)
                        }
                    }

                    // Seed Mae Sot
                    OutlinedButton(
                        onClick = { showSeedConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSeedingData) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กำลังโหลดข้อมูลแม่สอด 4,530 จุด...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("โหลดข้อมูลตัวอย่างแม่สอด (4,530 จุด)", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 5. OCR & Hand Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("การสแกนและโหมดการใช้งาน", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = {
                            geminiKey = it
                            prefs.geminiApiKey = it
                        },
                        label = { Text("GEMINI_API_KEY (สำหรับอ่านรูปฉลากพัสดุ)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    if (showGeminiKey) Icons.Default.Close else Icons.Default.Lock,
                                    contentDescription = null
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text("มือข้างที่ถนัดสำหรับจัดส่ง:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedHandMode = "right"
                                    prefs.handMode = "right"
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedHandMode == "right") TealContainer else NeutralBackground
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedHandMode == "right",
                                    onClick = {
                                        selectedHandMode = "right"
                                        prefs.handMode = "right"
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("มือขวา (ค่าเริ่มต้น)", fontSize = 12.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedHandMode = "left"
                                    prefs.handMode = "left"
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedHandMode == "left") TealContainer else NeutralBackground
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedHandMode == "left",
                                    onClick = {
                                        selectedHandMode = "left"
                                        prefs.handMode = "left"
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("มือซ้าย", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 16-Map Provider Selection Dialog
    if (showMapPickerSheet) {
        AlertDialog(
            onDismissRequest = { showMapPickerSheet = false },
            title = {
                Text(
                    "เลือกแผนที่เริ่มต้น (16 ชนิด)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val grouped = MapProviderRegistry.PROVIDERS.groupBy { it.category }

                    grouped.forEach { (cat, providers) ->
                        val catLabel = when (cat) {
                            "satellite" -> "ภาพถ่ายดาวเทียม (Satellite)"
                            "street" -> "แผนที่ถนนและการนำทาง (Street)"
                            "topo" -> "ภูมิประเทศและความสูง (Topo & Terrain)"
                            else -> "แผนที่เฉพาะทาง (Specialized)"
                        }

                        item {
                            Text(
                                catLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealDark,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }

                        items(providers) { provider ->
                            val isSelected = provider.id == selectedMapProvider.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMapProvider = provider
                                        prefs.defaultMapLayer = provider.id
                                        showMapPickerSheet = false
                                        Toast.makeText(context, "ตั้ง '${provider.name}' เป็นแผนที่เริ่มต้นแล้ว", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) TealContainer else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedMapProvider = provider
                                            prefs.defaultMapLayer = provider.id
                                            showMapPickerSheet = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            provider.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            color = if (isSelected) TealDark else TextPrimary
                                        )
                                        if (provider.description.isNotBlank()) {
                                            Text(
                                                provider.description,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMapPickerSheet = false }) {
                    Text("ปิด")
                }
            }
        )
    }

    // License Activation Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = {
                Text("ใส่รหัสเปิดใช้งาน (License Code)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "กรอกรหัสเปิดใช้งานแพ็กเกจที่คุณได้รับจากเจ้าของแอป (ตัวอย่าง: DW-PRO-...)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = licenseInput,
                        onValueChange = {
                            licenseInput = it.uppercase(Locale.US)
                            licenseError = null
                        },
                        label = { Text("รหัสเปิดใช้งาน") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = licenseError != null
                    )
                    licenseError?.let {
                        Text(it, fontSize = 12.sp, color = StatusReported)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = VaultCryptoManager.verifyLicenseCode(licenseInput, prefs.userId)
                        if (result.isValid) {
                            prefs.isSubscribed = true
                            prefs.subscriptionPlan = result.planName
                            prefs.subscriptionExpiryMs = result.expiryTimestampMs
                            refreshStats()
                            showLicenseDialog = false
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        } else {
                            licenseError = result.message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("เปิดใช้งาน")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Seed Data Confirmation Dialog
    if (showSeedConfirm) {
        ConfirmActionDialog(
            title = "โหลดข้อมูลตัวอย่างแม่สอด",
            message = "ระบบจะโหลดข้อมูลจริง 4,530 หลังคาเรือน (อ.แม่สอด จ.ตาก) เข้าสู่ฐานข้อมูล SQLite ภายในเครื่อง",
            confirmText = "โหลดข้อมูล",
            onDismiss = { showSeedConfirm = false },
            onConfirm = {
                showSeedConfirm = false
                isSeedingData = true
                scope.launch {
                    val count = houseRepo.initialize()
                    isSeedingData = false
                    refreshStats()
                    Toast.makeText(context, "โหลดข้อมูลแม่สอดเรียบร้อยแล้ว ($count หลัง)", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
