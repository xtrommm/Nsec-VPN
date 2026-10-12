package com.nsec.vpn.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

class SpeedTestHistory(context: Context) {
    data class Result(val nodeName: String, val timestamp: Long, val ping: Double, val jitter: Double,
                      val download: Float, val upload: Float)
    private val prefs = context.getSharedPreferences("nsec_speed_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun read(): List<Result> = try {
        (gson.fromJson(prefs.getString("results", null), Array<Result>::class.java)?.toList() ?: emptyList())
            .filter { it.timestamp > 0 && it.nodeName.isNotBlank() && it.ping.isFinite() && it.jitter.isFinite() &&
                it.download.isFinite() && it.upload.isFinite() && it.download > 0 && it.upload > 0 }.take(5)
    } catch (_: Exception) { emptyList() }
    fun append(result: Result): List<Result> {
        require(result.download.isFinite() && result.upload.isFinite() && result.download > 0 && result.upload > 0)
        return (listOf(result) + read()).take(5).also { list -> prefs.edit { putString("results", gson.toJson(list)) } }
    }
}
