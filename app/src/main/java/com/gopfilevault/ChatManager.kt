package com.gopfilevault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Attachment(
    val uri: String,
    val name: String,
    val isImage: Boolean,
    val time: Long = 0L
)

// THÊM: Biến tokenCount để lưu số token đã dùng cho request này
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val attachments: List<Attachment> = emptyList(),
    val tokenCount: Int = 0
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Đoạn chat mới",
    var messages: List<ChatMessage> = emptyList()
)

object ChatManager {
    private const val PREFS_NAME = "HimmelOS_Chats"
    private const val SESSIONS_KEY = "ALL_SESSIONS"
    private const val HISTORY_FILES_KEY = "HISTORY_MERGED_FILES"

    fun saveSessions(context: Context, sessions: List<ChatSession>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()

        sessions.forEach { session ->
            val sessionObj = JSONObject()
            sessionObj.put("id", session.id)
            sessionObj.put("title", session.title)

            val messagesArray = JSONArray()
            session.messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("text", msg.text)
                msgObj.put("isUser", msg.isUser)
                msgObj.put("tokenCount", msg.tokenCount) // LƯU TOKEN

                val attachArray = JSONArray()
                msg.attachments.forEach { att ->
                    val attObj = JSONObject()
                    attObj.put("uri", att.uri)
                    attObj.put("name", att.name)
                    attObj.put("isImage", att.isImage)
                    attObj.put("time", att.time)
                    attachArray.put(attObj)
                }
                msgObj.put("attachments", attachArray)
                messagesArray.put(msgObj)
            }
            sessionObj.put("messages", messagesArray)
            jsonArray.put(sessionObj)
        }
        prefs.edit().putString(SESSIONS_KEY, jsonArray.toString()).apply()
    }

    fun loadSessions(context: Context): List<ChatSession> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(SESSIONS_KEY, "[]") ?: "[]"
        val sessions = mutableListOf<ChatSession>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val sessionObj = jsonArray.getJSONObject(i)
                val messagesArray = sessionObj.getJSONArray("messages")
                val messages = mutableListOf<ChatMessage>()
                for (j in 0 until messagesArray.length()) {
                    val msgObj = messagesArray.getJSONObject(j)
                    val attachArray = msgObj.optJSONArray("attachments") ?: JSONArray()
                    val attachments = mutableListOf<Attachment>()
                    for (k in 0 until attachArray.length()) {
                        val attObj = attachArray.getJSONObject(k)
                        attachments.add(
                            Attachment(
                                attObj.getString("uri"),
                                attObj.getString("name"),
                                attObj.getBoolean("isImage"),
                                attObj.optLong("time", 0L)
                            )
                        )
                    }
                    messages.add(
                        ChatMessage(
                            msgObj.getString("text"),
                            msgObj.getBoolean("isUser"),
                            attachments,
                            msgObj.optInt("tokenCount", 0) // ĐỌC TOKEN
                        )
                    )
                }
                sessions.add(ChatSession(sessionObj.getString("id"), sessionObj.getString("title"), messages))
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (sessions.isEmpty()) sessions.add(ChatSession())
        return sessions
    }

    fun saveMergedHistory(context: Context, newUri: String, newName: String, timestamp: Long): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_FILES_KEY, "[]") ?: "[]"
        val historyList = mutableListOf<JSONObject>()

        try {
            val array = JSONArray(historyString)
            for (i in 0 until array.length()) historyList.add(array.getJSONObject(i))
        } catch (e: Exception) {}

        val newObj = JSONObject().apply {
            put("uri", newUri)
            put("name", newName)
            put("time", timestamp)
        }

        historyList.removeAll { it.getString("uri") == newUri }
        historyList.add(0, newObj)

        val removedUris = mutableListOf<String>()
        while (historyList.size > 5) {
            removedUris.add(historyList.removeLast().getString("uri"))
        }

        val newArray = JSONArray()
        historyList.forEach { newArray.put(it) }
        prefs.edit().putString(HISTORY_FILES_KEY, newArray.toString()).apply()

        return removedUris
    }

    fun getMergedHistory(context: Context): List<Attachment> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_FILES_KEY, "[]") ?: "[]"
        val result = mutableListOf<Attachment>()
        try {
            val array = JSONArray(historyString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(Attachment(obj.getString("uri"), obj.getString("name"), false, obj.optLong("time", 0L)))
            }
        } catch (e: Exception) {}
        return result
    }
}