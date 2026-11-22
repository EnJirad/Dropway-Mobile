@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pocketdev.dropway.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.model.House

@Composable
fun AddEditHouseDialog(
    initialHouse: House? = null,
    onDismiss: () -> Unit,
    onSave: (House) -> Unit
) {
    var fullName by remember { mutableStateOf(initialHouse?.fullName ?: "") }
    var phone by remember { mutableStateOf(initialHouse?.phone ?: "") }
    var address by remember { mutableStateOf(initialHouse?.address ?: "") }
    var latText by remember { mutableStateOf(initialHouse?.lat?.toString() ?: "") }
    var lngText by remember { mutableStateOf(initialHouse?.lng?.toString() ?: "") }
    var note by remember { mutableStateOf(initialHouse?.note ?: "") }
    var quantityText by remember { mutableStateOf(initialHouse?.quantity?.toString() ?: "1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialHouse == null) "เพิ่มบ้านใหม่ในคลัง" else "แก้ไขข้อมูลบ้าน") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("ชื่อ-นามสกุล ผู้รับ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("เบอร์โทรศัพท์") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ที่อยู่ (เลขบ้าน, หมู่, ตำบล, อำเภอ)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    singleLine = false
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("ละติจูด (Lat)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("ลองจิจูด (Lng)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("จุดสังเกต / หมายเหตุ") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    singleLine = false
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("จำนวนพัสดุปกติ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val house = House(
                        id = initialHouse?.id ?: java.util.UUID.randomUUID().toString(),
                        fullName = fullName.trim().ifEmpty { null },
                        phone = phone.trim().ifEmpty { null },
                        address = address.trim().ifEmpty { null },
                        lat = latText.toDoubleOrNull(),
                        lng = lngText.toDoubleOrNull(),
                        note = note.trim().ifEmpty { null },
                        quantity = quantityText.toIntOrNull() ?: 1
                    )
                    onSave(house)
                }
            ) {
                Text("บันทึก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@Composable
fun ReportProblemDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String, note: String) -> Unit
) {
    val commonReasons = listOf(
        "ติดต่อไม่ได้ / ไม่รับสาย",
        "ขอเลื่อนวันส่ง",
        "ปฏิเสธการรับพัสดุ",
        "ที่อยู่ไม่ชัดเจน / หาบ้านไม่พบ",
        "พัสดุชำรุดเสียหาย",
        "อื่นๆ"
    )
    var selectedReason by remember { mutableStateOf(commonReasons[0]) }
    var detailNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("รายงานปัญหาการจัดส่ง") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("เลือกสาเหตุ:", fontSize = 14.sp)
                commonReasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(reason, fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    value = detailNote,
                    onValueChange = { detailNote = it },
                    label = { Text("รายละเอียดเพิ่มเติม (ถ้ามี)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    singleLine = false
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = { onConfirm(selectedReason, detailNote) }
            ) {
                Text("บันทึกปัญหา")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@Composable
fun EditDriverNoteDialog(
    currentNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var note by remember { mutableStateOf(currentNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("บันทึกช่วยจำคนขับ") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("ข้อความบันทึก เช่น วางไว้หน้าประตู, โทรก่อน 10 โมง") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                singleLine = false
            )
        },
        confirmButton = {
            Button(onClick = { onSave(note.trim()) }) {
                Text("บันทึก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@Composable
fun AddToQueueDialog(
    houseName: String?,
    onDismiss: () -> Unit,
    onConfirm: (trackingNumber: String?) -> Unit
) {
    var tracking by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("เพิ่มเข้าคิวนำทาง") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ต้องการเพิ่ม ${houseName ?: "จุดส่งนี้"} เข้าสู่คิวจัดส่งวันนี้?")
                OutlinedTextField(
                    value = tracking,
                    onValueChange = { tracking = it },
                    label = { Text("เลขพัสดุ (ไม่บังคับ)") },
                    placeholder = { Text("เช่น FLASH..., SPX...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tracking.trim().ifEmpty { null }) }) {
                Text("เพิ่มเข้าคิว")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String = "ยืนยัน",
    isDestructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                         else ButtonDefaults.buttonColors(),
                onClick = onConfirm
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}
