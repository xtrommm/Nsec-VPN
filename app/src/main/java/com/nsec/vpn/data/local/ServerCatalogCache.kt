package com.nsec.vpn.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.nsec.vpn.data.api.ServerItemDto

class ServerCatalogCache(context: Context) {
    private val prefs = context.getSharedPreferences("nsec_verified_server_catalog", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun read(now: Long = System.currentTimeMillis()): List<ServerItemDto> {
        val stamp = prefs.getLong("verified_at", 0)
        if (stamp <= 0 || now - stamp !in 0..MAX_AGE_MS) return emptyList()
        return try {
            val list = gson.fromJson(prefs.getString("catalog", null), Array<ServerItemDto>::class.java)?.toList() ?: emptyList()
            validate(list)
            list
        } catch (_: Exception) { prefs.edit { remove("catalog"); remove("verified_at") }; emptyList() }
    }
    fun write(list: List<ServerItemDto>, now: Long = System.currentTimeMillis()) {
        validate(list)
        prefs.edit { putString("catalog", gson.toJson(list)); putLong("verified_at", now) }
    }
    companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        fun validate(list: List<ServerItemDto>) {
            require(list.size <= 2000 && list.map { it.id }.toSet().size == list.size)
            list.forEach { require(it.id.isNotBlank() && it.id.length <= 64 && it.name.isNotBlank() && it.countryCode.length == 2 && it.currentLoad in 0..100) }
        }
    }
}
