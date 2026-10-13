package com.nsec.vpn.vpn

/** First sample is a baseline, never all bytes since tunnel start presented as one second of traffic. */
class TrafficCounterSampler {
    data class Rate(val downloadMbps: Float, val uploadMbps: Float)
    private var previousTime: Long? = null
    private var previousRx = 0L
    private var previousTx = 0L
    fun sample(elapsedMs: Long, received: Long, sent: Long): Rate {
        val before = previousTime
        val seconds = before?.let { (elapsedMs - it) / 1000.0 } ?: 0.0
        val valid = seconds > 0 && received >= previousRx && sent >= previousTx
        val rate = if (valid && before != null) Rate(
            ((received - previousRx).toDouble() * 8 / seconds / 1_000_000).toFloat(),
            ((sent - previousTx).toDouble() * 8 / seconds / 1_000_000).toFloat()
        ) else Rate(0f, 0f)
        previousTime = elapsedMs; previousRx = received; previousTx = sent
        return rate
    }
}
