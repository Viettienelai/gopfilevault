package com.gopfilevault

import android.content.Context
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object GeminiService {

    suspend fun askVault(context: Context, vaultUri: Uri, apiKey: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Đọc nội dung file TXT tổng từ thiết bị
                val inputStream = context.contentResolver.openInputStream(vaultUri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val vaultContent = reader.use { it.readText() }

                // 2. Khởi tạo GenerativeModel với bản cập nhật Flash Lite 3.1 mới nhất
                val model = GenerativeModel(
                    modelName = "gemini-3.1-flash-lite-preview",
                    apiKey = apiKey,
                    // Đưa toàn bộ nội dung file vào System Instruction
                    // Đây là khu vực "bất khả xâm phạm", giúp AI định hình vai trò và kiến thức gốc
                    systemInstruction = content {
                        text(
                            """
                            Bạn là Himmel, một trợ lý AI thông minh cá nhân.
                            Dưới đây là toàn bộ cơ sở dữ liệu cốt lõi (Vault) của hệ thống:
                            
                            --- BẮT ĐẦU DỮ LIỆU VAULT ---
                            $vaultContent
                            --- KẾT THÚC DỮ LIỆU VAULT ---
                            
                            Nhiệm vụ của bạn:
                            1. Dựa ĐỘC QUYỀN vào dữ liệu Vault được cung cấp bên trên để trả lời câu hỏi của người dùng.
                            2. Nếu thông tin người dùng hỏi không tồn tại trong Vault, hãy thành thật trả lời là không có dữ liệu, TUYỆT ĐỐI KHÔNG bịa đặt hay suy diễn ngoài lề.
                            3. Trả lời ngắn gọn, súc tích, định dạng bằng Markdown rõ ràng.
                            """.trimIndent()
                        )
                    }
                )

                // 3. Gửi Prompt (Lúc này Prompt chỉ chứa câu hỏi hiện tại và 5 câu lịch sử gần nhất)
                val response = model.generateContent(prompt)

                response.text ?: "Himmel không thể tạo câu trả lời lúc này. Có thể nội dung vi phạm chính sách hoặc lỗi mạng."

            } catch (e: Exception) {
                e.printStackTrace()
                "Lỗi khi truy xuất Vault: ${e.message}"
            }
        }
    }
}