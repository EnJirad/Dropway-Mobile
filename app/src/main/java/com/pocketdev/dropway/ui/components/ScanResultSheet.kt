package com.pocketdev.dropway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.model.LabelFields
import com.pocketdev.dropway.data.model.MatchResult
import com.pocketdev.dropway.data.model.SameParcelMeta
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.DriverActions
import com.pocketdev.dropway.util.LabelParser
import com.pocketdev.dropway.util.SearchMatcher

/**
 * Ported from Dropway Web src/components/ScanResultSheet.tsx
 * Bottom modal sheet displaying primary matched house, editable fields, candidates, and duplicate warnings.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanResultSheet(
    open: Boolean,
    house: House?,
    match: MatchResult?,
    draft: LabelFields,
    candidates: List<MatchResult>,
    tracking: String?,
    sameParcel: SameParcelMeta?,
    notice: String?,
    busy: Boolean,
    added: Boolean,
    onClose: () -> Unit,
    onAdd: (LabelFields) -> Unit,
    onSave: (LabelFields, String) -> Unit,
    onSelectCandidate: (MatchResult) -> Unit,
    allHouses: List<House>,
    onScanAgain: () -> Unit
) {
    if (!open) return

    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(draft) }
    var note by remember { mutableStateOf(house?.note ?: "") }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<House>>(emptyList()) }

    // Synchronize form when primary selection changes
    LaunchedEffect(house, draft, tracking) {
        isEditing = false
        if (house != null) {
            val head = (house.address ?: "").split(Regex("""ม\.|หมู่|moo|\bm\.""", RegexOption.IGNORE_CASE)).firstOrNull() ?: (house.address ?: "")
            val houseNo = Regex("""\d{1,5}(?:/\d{1,4})?""").find(head)?.value ?: ""
            val postal = Regex("""(?:^|\D)([1-9]\d{4})(?!\d)""").findAll(house.address ?: "").lastOrNull()?.groupValues?.get(1) ?: ""
            form = LabelFields(
                name = house.fullName ?: "",
                phone = house.phone ?: "",
                houseNo = houseNo,
                address = house.address ?: "",
                postalCode = postal,
                tracking = tracking ?: draft.tracking
            )
            note = house.note ?: ""
        } else {
            form = draft.copy(tracking = tracking ?: draft.tracking)
            note = ""
        }
    }

    // Inline search filtering
    LaunchedEffect(searchQuery, allHouses) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            searchResults = allHouses.filter { h ->
                SearchMatcher.matchHouseWithSearch(h, q) > 0
            }.take(6)
        } else {
            searchResults = emptyList()
        }
    }

    val addressPreview = remember(form) {
        LabelParser.composeAddress(form.houseNo, form.address, form.postalCode)
    }

    val canSave = remember(form, addressPreview) {
        form.name.isNotBlank() || form.phone.isNotBlank() || addressPreview.isNotBlank()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onClose() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clickable(enabled = false) {}, // Prevent dismiss when tapping sheet
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = SurfaceCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Grabber bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(44.dp)
                            .height(4.dp)
                            .background(NeutralBorder, RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Header: Title + Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (house != null) "พบบ้านในคลัง" else "ไม่พบในคลัง (ข้อมูลจากฉลาก)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (house != null) "ตรวจข้อมูลให้ถูกต้อง แล้วเลือกเพิ่มเข้านำทางหรือบันทึก"
                                else "ตรวจข้อมูลที่อ่านได้จากฉลาก แล้วบันทึกเป็นบ้านใหม่ได้เลย",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "ปิด", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Same Parcel Alert (พัสดุชิ้นนี้อยู่ในคิวแล้ว)
                        sameParcel?.let { sp ->
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("พัสดุชิ้นนี้อยู่ในคิวแล้ว", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                                        Text(
                                            text = "เลขพัสดุ ${sp.trackingNumber ?: tracking ?: ""} ถูกสแกนแล้ว ${if (!sp.fullName.isNullOrBlank()) "(${sp.fullName})" else ""} — เลขเดียวกันคือชิ้นเดียวกัน จึงเพิ่มเข้าคิวนำทางซ้ำไม่ได้",
                                            fontSize = 11.sp,
                                            color = Color(0xFF78350F),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Notice Banner
                        notice?.let { msg ->
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = msg, fontSize = 11.sp, color = Color(0xFF1E40AF))
                                }
                            }
                        }

                        // 3. Primary Match Card (ผลหลัก)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = NeutralBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Badges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (house != null) {
                                            Surface(color = TealContainer, shape = RoundedCornerShape(6.dp)) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Home, contentDescription = null, tint = TealDark, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("ตรงกับฐานข้อมูล", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                                }
                                            }
                                        } else {
                                            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp)) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("ข้อมูลจากฉลาก", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                                }
                                            }
                                        }

                                        match?.let { m ->
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val (lbl, col) = when (m.level) {
                                                "exact" -> "ตรงมาก" to StatusDelivered
                                                "high" -> "ใกล้เคียงสูง" to TealPrimary
                                                else -> "อาจใช่" to StatusPending
                                            }
                                            Surface(color = col.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                                Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                            }
                                        }
                                    }
                                }

                                match?.reasons?.let { reasons ->
                                    if (reasons.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(reasons.joinToString(" • "), fontSize = 11.sp, color = TextMuted)
                                    }
                                }

                                match?.conflicts?.let { conflicts ->
                                    if (conflicts.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("⚠️ " + conflicts.joinToString(" • "), fontSize = 11.sp, color = StatusReported)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Content: Display vs Editable
                                if (isEditing) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = form.name,
                                            onValueChange = { form = form.copy(name = it) },
                                            label = { Text("ชื่อผู้รับ", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = form.phone,
                                            onValueChange = { form = form.copy(phone = it) },
                                            label = { Text("เบอร์โทร", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = form.houseNo,
                                                onValueChange = { form = form.copy(houseNo = it) },
                                                label = { Text("บ้านเลขที่", fontSize = 11.sp) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = form.postalCode,
                                                onValueChange = { form = form.copy(postalCode = it) },
                                                label = { Text("รหัสไปรษณีย์", fontSize = 11.sp) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }
                                        OutlinedTextField(
                                            value = form.address,
                                            onValueChange = { form = form.copy(address = it) },
                                            label = { Text("ที่อยู่", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 3
                                        )
                                        OutlinedTextField(
                                            value = form.tracking,
                                            onValueChange = { form = form.copy(tracking = it) },
                                            label = { Text("เลขพัสดุ", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = note,
                                            onValueChange = { note = it },
                                            label = { Text("หมายเหตุ", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = form.name.takeIf { it.isNotBlank() } ?: "ไม่ทราบชื่อผู้รับ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = TextPrimary
                                        )

                                        if (form.phone.isNotBlank()) {
                                            Row(
                                                modifier = Modifier.clickable { DriverActions.openPhoneDialer(context, form.phone) },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Phone, contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(form.phone, fontSize = 13.sp, color = StatusDelivered, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        if (addressPreview.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(addressPreview, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                                            }
                                        }

                                        if (form.tracking.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("เลขพัสดุ: ${form.tracking}", fontSize = 11.sp, color = TextMuted)
                                            }
                                        }

                                        if (note.isNotBlank()) {
                                            Surface(
                                                color = Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                            ) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                                    Text("📝", fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(note, fontSize = 11.sp, color = Color(0xFF78350F))
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Dropway 3-action buttons: บันทึก | แก้ไข | เพิ่ม
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. บันทึก
                                    OutlinedButton(
                                        onClick = { onSave(form, note) },
                                        enabled = !busy && canSave,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isEditing) "บันทึกแก้ไข" else "บันทึก", fontSize = 12.sp)
                                    }

                                    // 2. แก้ไข / ยกเลิก
                                    OutlinedButton(
                                        onClick = { isEditing = !isEditing },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isEditing) "ยกเลิก" else "แก้ไข", fontSize = 12.sp)
                                    }

                                    // 3. เพิ่มเข้าคิวนำทาง
                                    Button(
                                        onClick = { onAdd(form) },
                                        enabled = !busy && !added && sameParcel == null,
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (sameParcel != null) "ชิ้นเดียวกัน" else if (added) "เพิ่มแล้ว" else "เพิ่ม",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (added) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("เพิ่มเข้าคิวนำทางวันนี้แล้ว", fontSize = 11.sp, color = StatusDelivered, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // 4. In-Sheet Manual Search (ค้นหาบ้านเอง)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = NeutralBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ค้นหาบ้านเอง", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted)
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("ชื่อ / เบอร์ / ที่อยู่ / เลขพัสดุ", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "ล้าง")
                                            }
                                        }
                                    }
                                )

                                if (searchResults.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        searchResults.forEach { found ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSelectCandidate(
                                                            MatchResult(
                                                                house = found,
                                                                score = 100,
                                                                level = "exact",
                                                                reasons = listOf("เลือกด้วยตนเอง"),
                                                                trackingCandidate = form.tracking
                                                            )
                                                        )
                                                        searchQuery = ""
                                                        searchResults = emptyList()
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = SurfaceCard
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(found.fullName ?: "ไม่ระบุชื่อ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(found.address ?: "", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                                    }
                                                    Text("เลือก", fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Candidate Recommendations (บ้านใกล้เคียงที่ระบบแนะนำ)
                        if (candidates.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "บ้านใกล้เคียงที่ระบบแนะนำ (${candidates.size} รายการ)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )

                                candidates.forEach { cand ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectCandidate(cand) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = NeutralBackground),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(cand.house.fullName ?: "ไม่ระบุชื่อ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(cand.house.address ?: "ไม่มีที่อยู่", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                                if (cand.reasons.isNotEmpty()) {
                                                    Text(cand.reasons.joinToString(" • "), fontSize = 10.sp, color = TealDark)
                                                }
                                            }
                                            Surface(color = SurfaceCard, shape = RoundedCornerShape(6.dp)) {
                                                Text("เลือก", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TealPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Scan Again Button
                        Button(
                            onClick = onScanAgain,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeutralBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("สแกนชิ้นถัดไป", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
