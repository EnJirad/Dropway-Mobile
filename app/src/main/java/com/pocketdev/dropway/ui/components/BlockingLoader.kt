package com.pocketdev.dropway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketdev.dropway.ui.theme.SurfaceCard
import com.pocketdev.dropway.ui.theme.TealDark
import com.pocketdev.dropway.ui.theme.TealPrimary
import com.pocketdev.dropway.ui.theme.TextMuted
import com.pocketdev.dropway.ui.theme.TextPrimary

/**
 * Ported from Dropway Web src/components/BlockingLoader.tsx
 * Fullscreen blocking modal showing multi-stage progress during parcel label scanning.
 */
@Composable
fun BlockingLoader(
    open: Boolean,
    title: String = "กำลังอ่านฉลาก",
    stage: String? = null,
    message: String = "ระบบกำลังอ่านและเทียบข้อมูล กรุณารอสักครู่"
) {
    if (!open) return

    Dialog(
        onDismissRequest = {}, // Cannot dismiss while processing
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(SurfaceCard, shape = RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = TealPrimary,
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.5.dp
                )

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )

                if (!stage.isNullOrBlank()) {
                    Text(
                        text = stage,
                        fontSize = 13.sp,
                        color = TealDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
