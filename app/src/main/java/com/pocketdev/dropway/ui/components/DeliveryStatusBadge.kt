package com.pocketdev.dropway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.ui.theme.StatusDelivered
import com.pocketdev.dropway.ui.theme.StatusDeliveredBg
import com.pocketdev.dropway.ui.theme.StatusPending
import com.pocketdev.dropway.ui.theme.StatusPendingBg
import com.pocketdev.dropway.ui.theme.StatusReported
import com.pocketdev.dropway.ui.theme.StatusReportedBg

@Composable
fun DeliveryStatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (status.lowercase()) {
        "delivered" -> Triple(StatusDeliveredBg, StatusDelivered, "✓ ส่งแล้ว")
        "reported" -> Triple(StatusReportedBg, StatusReported, "⚠ แจ้งปัญหา")
        else -> Triple(StatusPendingBg, StatusPending, "⏳ รอดำเนินการ")
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
