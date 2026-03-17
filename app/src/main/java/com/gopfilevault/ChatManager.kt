package com.gopfilevault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(val text: String, val isUser: Boolean)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Đoạn chat mới",
    var messages: List<ChatMessage> = emptyList()
)

object ChatManager {
    private const val PREFS_NAME = "HimmelOS_Chats"
    private const val SESSIONS_KEY = "ALL_SESSIONS"

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
                    messages.add(ChatMessage(msgObj.getString("text"), msgObj.getBoolean("isUser")))
                }

                sessions.add(
                    ChatSession(
                        id = sessionObj.getString("id"),
                        title = sessionObj.getString("title"),
                        messages = messages
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Nếu chưa có session nào, tạo 1 cái mặc định
        if (sessions.isEmpty()) {
            sessions.add(ChatSession())
        }
        return sessions
    }
}