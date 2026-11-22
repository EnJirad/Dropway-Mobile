package com.pocketdev.dropway.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.remote.NeonClient
import com.pocketdev.dropway.ui.components.ConfirmActionDialog
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    neonClient: NeonClient,
    prefs: PreferencesManager,
    onConnectionUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dbUrl by remember { mutableStateOf(prefs.databaseUrl) }
    var geminiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var showPassword by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var selectedHandMode by remember { mutableStateOf(prefs.handMode) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf<Boolean?>(null) }

    var isInitializingSchema by remember { mutableStateOf(false) }
    var isSeedingData by remember { mutableStateOf(false) }
    var showSeedConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ตั้งค่าและการเชื่อมต่อ", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
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
            // Neon Database Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("การเชื่อมต่อ Neon PostgreSQL", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        "นำ Connection string มาจาก Neon Console (https://console.neon.tech) เมนู Dashboard -> Connection Details",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = dbUrl,
                        onValueChange = {
                            dbUrl = it
                            testResult = null
                            isTestSuccess = null
                        },
                        label = { Text("DATABASE_URL") },
                        placeholder = { Text("postgresql://neondb_owner:password@ep-xyz.aws.neon.tech/neondb?sslmode=require") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.Close else Icons.Default.Lock,
                                    contentDescription = if (showPassword) "ซ่อนรหัสผ่าน" else "แสดงรหัสผ่าน"
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Test result banner
                    testResult?.let { msg ->
                        val isOk = isTestSuccess == true
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isOk) StatusDeliveredBg else StatusReportedBg,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isOk) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isOk) StatusDelivered else StatusReported,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = if (isOk) StatusDelivered else StatusReported,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                prefs.databaseUrl = dbUrl
                                Toast.makeText(context, "บันทึก Connection String แล้ว", Toast.LENGTH_SHORT).show()
                                onConnectionUpdated()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("บันทึก")
                        }

                        OutlinedButton(
                            onClick = {
                                if (dbUrl.isBlank()) {
                                    Toast.makeText(context, "กรุณากรอก DATABASE_URL ก่อนทดสอบ", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    val res = neonClient.testConnection(dbUrl.trim())
                                    isTesting = false
                                    res.onSuccess { ms ->
                                        isTestSuccess = true
                                        testResult = "เชื่อมต่อสำเร็จ! ความเร็วตอบสนอง $ms ms"
                                        prefs.databaseUrl = dbUrl
                                        prefs.lastConnectionStatus = "connected"
                                        onConnectionUpdated()
                                    }.onFailure { err ->
                                        isTestSuccess = false
                                        testResult = "เชื่อมต่อไม่สำเร็จ: ${err.message}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isTesting
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("ทดสอบเชื่อมต่อ")
                            }
                        }
                    }
                }
            }

            // AI & OCR Scanner Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ตั้งค่า AI สแกนใบปะหน้า (Gemini Vision)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        "ใช้ Google Gemini 1.5 Flash ในการอ่านใบปะหน้าพัสดุจากรูปถ่ายกล้องอัตโนมัติ แม่นยำทั้งชื่อ เบอร์โทร และบ้านเลขที่ (รับคีย์ฟรีที่ aistudio.google.com)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("GEMINI_API_KEY") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    if (showGeminiKey) Icons.Default.Close else Icons.Default.Lock,
                                    contentDescription = if (showGeminiKey) "ซ่อนคีย์" else "แสดงคีย์"
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            prefs.geminiApiKey = geminiKey
                            Toast.makeText(context, "บันทึก Gemini API Key เรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("บันทึก Gemini API Key")
                    }
                }
            }

            // Database Operations Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("การจัดการตารางในฐานข้อมูล", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Text(
                        "สร้างตาราง houses, user_navigation_houses, parcels และ navigation_view ให้อัตโนมัติ",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            if (!prefs.isConfigured()) {
                                Toast.makeText(context, "กรุณาบันทึก DATABASE_URL ก่อน", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isInitializingSchema = true
                            scope.launch {
                                val res = neonClient.ensureSchema()
                                isInitializingSchema = false
                                res.onSuccess {
                                    Toast.makeText(context, "สร้างตารางและวิวสำเร็จแล้ว!", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(context, "เกิดข้อผิดพลาด: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isInitializingSchema
                    ) {
                        if (isInitializingSchema) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กำลังสร้างโครงสร้างตาราง...")
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("สร้าง / ซิงค์ตารางฐานข้อมูล (Init Schema)")
                        }
                    }

                    OutlinedButton(
                        onClick = { showSeedConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSeedingData
                    ) {
                        if (isSeedingData) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("ใส่ข้อมูลตัวอย่างแม่สอด 27 จุด (Seed Data)")
                        }
                    }
                }
            }

            // Driver Hand Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("โหมดการใช้งานมือถือของคนขับ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "เลือกตำแหน่งแถบปุ่มกดนำทาง/โทร/ส่งแล้ว (ซ้ายหรือขวา) เพื่อให้ใช้งานมือเดียวขณะขับรถได้สะดวกที่สุด",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedHandMode = "right"
                                prefs.handMode = "right"
                                Toast.makeText(context, "เปลี่ยนเป็นโหมดมือขวาแล้ว", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = if (selectedHandMode == "right") ButtonDefaults.outlinedButtonColors(containerColor = TealContainer) else ButtonDefaults.outlinedButtonColors(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "โหมดมือขวา",
                                fontWeight = if (selectedHandMode == "right") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedHandMode == "right") TealDark else TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                selectedHandMode = "left"
                                prefs.handMode = "left"
                                Toast.makeText(context, "เปลี่ยนเป็นโหมดมือซ้ายแล้ว", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = if (selectedHandMode == "left") ButtonDefaults.outlinedButtonColors(containerColor = TealContainer) else ButtonDefaults.outlinedButtonColors(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "โหมดมือซ้าย",
                                fontWeight = if (selectedHandMode == "left") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedHandMode == "left") TealDark else TextPrimary
                            )
                        }
                    }
                }
            }

            // App info
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Dropway Mobile App v1.0", fontSize = 12.sp, color = TextMuted)
                Text("ระบบจัดการคลังบ้านและนำทางจัดส่งพัสดุ", fontSize = 11.sp, color = TextMuted)
            }
        }
    }

    // Seed confirm dialog
    if (showSeedConfirm) {
        ConfirmActionDialog(
            title = "ใส่ข้อมูลตัวอย่างแม่สอด 27 จุด",
            message = "ระบบจะเพิ่มบ้านตัวอย่าง 27 จุด (ต.แม่ตาว อ.แม่สอด จ.ตาก) เข้าสู่ตาราง houses ใน Neon เพื่อให้คุณเริ่มทดสอบระบบนำทางได้ทันที",
            confirmText = "เพิ่มข้อมูลตัวอย่าง",
            onDismiss = { showSeedConfirm = false },
            onConfirm = {
                showSeedConfirm = false
                isSeedingData = true
                scope.launch {
                    val res = neonClient.seedMaeSotData()
                    isSeedingData = false
                    res.onSuccess { count ->
                        Toast.makeText(context, "เพิ่มข้อมูลตัวอย่าง $count หลังเรียบร้อย!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "เพิ่มไม่สำเร็จ: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
