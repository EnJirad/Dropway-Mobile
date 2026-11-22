package com.pocketdev.dropway.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.pocketdev.dropway.data.model.LabelFields
import com.pocketdev.dropway.util.LabelValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiVisionClient {

    private const val LABEL_PROMPT = """คุณคือผู้อ่านฉลากพัสดุของไทย อ่านรูปแล้วดึงข้อมูล "ผู้รับ" เท่านั้น
ตอบเป็น JSON อย่างเดียว ห้ามมี markdown ห้ามมีข้อความอื่น เริ่มด้วย { จบด้วย }
รูปแบบ:
{"name":"","phone":"","houseNo":"","address":"","postalCode":"","tracking":""}
[กติกา]
- ใช้ฝั่ง "ผู้รับ/To" เท่านั้น ห้ามหยิบชื่อผู้ส่ง
- address = ที่อยู่ผู้รับเต็ม ตั้งแต่บ้านเลขที่จนถึงตำบล อำเภอ จังหวัด
- phone = เฉพาะตัวเลข 9-10 หลัก ขึ้นต้น 0
- houseNo = เลขที่บ้าน เช่น 12/3
- postalCode = รหัสไปรษณีย์ 5 หลัก
- tracking = เลขพัสดุ
- ช่องที่อ่านไม่ได้ให้ใส่ "" ห้ามเดา"""

    suspend fun readLabelWithGemini(apiKey: String, bitmap: Bitmap): Result<LabelFields> = runCatching {
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) {
                throw IllegalStateException("ยังไม่ได้ระบุ Gemini API Key ในหน้าตั้งค่า")
            }

            // Scale down if larger than 1440px to ensure fast upload and low latency
            val maxSide = 1440
            val naturalMax = maxOf(bitmap.width, bitmap.height)
            val scaledBitmap = if (naturalMax > maxSide) {
                val scale = maxSide.toFloat() / naturalMax
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val imageBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection

            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", LABEL_PROMPT))
                                put(JSONObject().put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                }))
                            }
                            put("parts", parts)
                        }
                        put(contentObj)
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0)
                        put("responseMimeType", "application/json")
                        put("maxOutputTokens", 700)
                    })
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

                if (responseCode !in 200..299) {
                    val errObj = try { JSONObject(responseText) } catch (_: Exception) { null }
                    val message = errObj?.optJSONObject("error")?.optString("message") ?: "HTTP $responseCode: $responseText"
                    throw RuntimeException(message)
                }

                val resJson = JSONObject(responseText)
                val candidates = resJson.optJSONArray("candidates")
                val firstPart = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                val start = firstPart.indexOf("{")
                val end = firstPart.lastIndexOf("}")
                if (start in 0 until end) {
                    val cleanJson = JSONObject(firstPart.substring(start, end + 1))
                    LabelValidator.cleanFields(
                        LabelFields(
                            name = cleanJson.optString("name", "").trim(),
                            phone = cleanJson.optString("phone", "").trim(),
                            houseNo = cleanJson.optString("houseNo", "").trim(),
                            address = cleanJson.optString("address", "").trim(),
                            postalCode = cleanJson.optString("postalCode", "").trim(),
                            tracking = cleanJson.optString("tracking", "").trim()
                        )
                    )
                } else {
                    throw RuntimeException("ไม่สามารถดึงข้อมูล JSON จากผลตอบกลับของ Gemini ได้")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
