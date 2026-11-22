package com.pocketdev.dropway.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object DriverActions {

    fun openMapNavigation(context: Context, lat: Double?, lng: Double?, address: String?, name: String?) {
        try {
            if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                // Priority 1: Direct Google Maps Turn-by-Turn Navigation intent
                val navUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(navIntent)
                    return
                } catch (_: Exception) {
                    // Fall through to next navigation method
                }

                // Priority 2: Google Maps Web / Universal Directions intent
                try {
                    val universalUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                    val universalIntent = Intent(Intent.ACTION_VIEW, universalUri).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(universalIntent)
                    return
                } catch (_: Exception) {
                    // Fall through to next navigation method
                }

                // Priority 3: Standard geo: intent for any installed navigation app (Waze, OsmAnd, etc.)
                val label = Uri.encode(name ?: address ?: "จุดส่งพัสดุ")
                val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(mapIntent)
            } else if (!address.isNullOrBlank()) {
                // Search by address
                val encodedAddress = Uri.encode(address)
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
                val mapIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(mapIntent)
            } else {
                Toast.makeText(context, "ไม่มีข้อมูลพิกัดหรือที่อยู่สำหรับนำทาง", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "ไม่สามารถเปิดแอพแผนที่ได้: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPhoneDialer(context: Context, phone: String?) {
        if (phone.isNullOrBlank()) {
            Toast.makeText(context, "ไม่มีเบอร์โทรศัพท์สำหรับติดต่อ", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "ไม่สามารถโทรออกได้: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
