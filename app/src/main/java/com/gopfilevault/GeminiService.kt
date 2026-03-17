package com.gopfilevault

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object GeminiService {

    // Khởi tạo Model với nội dung Vault được định tuyến lại
    private fun buildGenerativeModel(apiKey: String, vaultContent: String): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-3.1-flash-lite-preview",
            apiKey = apiKey,
            systemInstruction = content {
                text(
                    """
                    Bạn là Himmel, một trợ lý AI thông minh cá nhân.
                    Dưới đây là toàn bộ cơ sở dữ liệu cốt lõi (Vault) của hệ thống:
                    
                    --- BẮT ĐẦU DỮ LIỆU VAULT ---
                    $vaultContent
                    --- KẾT THÚC DỮ LIỆU VAULT ---
                    
                    Nhiệm vụ và Quy tắc của bạn:
                    1. NGUỒN DỮ LIỆU CHÍNH: Dựa ĐỘC QUYỀN vào dữ liệu Vault và các File đính kèm được cung cấp bên trên để trả lời.
                    2. NGỮ CẢNH: Phần "[NGỮ CẢNH - LỊCH SỬ TRÒ CHUYỆN]" do người dùng gửi kèm CHỈ dùng để bạn theo dõi mạch câu chuyện (ví dụ: người dùng hỏi "giải thích thêm về phần đó", bạn sẽ nhìn lịch sử để biết "phần đó" là gì). TUYỆT ĐỐI KHÔNG coi nội dung trong Lịch sử chat là dữ liệu gốc để trích xuất. Hãy luôn tham chiếu vào Vault.
                    3. SỰ THẬT: Nếu thông tin không tồn tại trong Vault hoặc File, hãy thành thật trả lời là không có dữ liệu, KHÔNG bịa đặt.
                    4. Trả lời ngắn gọn, súc tích, định dạng bằng Markdown rõ ràng.
                    """.trimIndent()
                )
            }
        )
    }

    private fun getActualVaultContent(context: Context, defaultVaultUri: Uri, textAttachments: List<Attachment>): String {
        return if (textAttachments.isNotEmpty()) {
            textAttachments.joinToString("\n\n") { att ->
                try {
                    val uri = Uri.parse(att.uri)
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    "--- TÀI LIỆU ĐÍNH KÈM CHÍNH: ${att.name} ---\n$text"
                } catch (e: Exception) { "" }
            }
        } else {
            try {
                val inputStream = context.contentResolver.openInputStream(defaultVaultUri)
                BufferedReader(InputStreamReader(inputStream!!)).use { it.readText() }
            } catch (e: Exception) { "" }
        }
    }

    private fun buildInputContent(context: Context, prompt: String, imageAttachments: List<Attachment>): Content {
        return content {
            imageAttachments.forEach { att ->
                try {
                    val uri = Uri.parse(att.uri)
                    val imageStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(imageStream)
                    imageStream?.close()
                    if (bitmap != null) image(bitmap)
                } catch (e: Exception) {
                    text("[Lỗi khi đọc ảnh: ${att.name}]")
                }
            }
            text(prompt)
        }
    }

    suspend fun askVault(context: Context, defaultVaultUri: Uri, apiKey: String, prompt: String, attachments: List<Attachment> = emptyList()): String {
        return withContext(Dispatchers.IO) {
            try {
                val textAtts = attachments.filter { !it.isImage }
                val imageAtts = attachments.filter { it.isImage }

                val vaultContent = getActualVaultContent(context, defaultVaultUri, textAtts)
                val model = buildGenerativeModel(apiKey, vaultContent)
                val inputContent = buildInputContent(context, prompt, imageAtts)

                val response = model.generateContent(inputContent)
                response.text ?: "Himmel không thể tạo câu trả lời lúc này."
            } catch (e: Exception) {
                e.printStackTrace()
                "Lỗi hệ thống: ${e.message}"
            }
        }
    }

    suspend fun countRequestTokens(context: Context, defaultVaultUri: Uri, apiKey: String, prompt: String, attachments: List<Attachment>): Int {
        return withContext(Dispatchers.IO) {
            try {
                val textAtts = attachments.filter { !it.isImage }
                val imageAtts = attachments.filter { it.isImage }

                val vaultContent = getActualVaultContent(context, defaultVaultUri, textAtts)
                val model = buildGenerativeModel(apiKey, vaultContent)
                val inputContent = buildInputContent(context, prompt, imageAtts)

                val response = model.countTokens(inputContent)
                response.totalTokens
            } catch (e: Exception) {
                -1
            }
        }
    }
}