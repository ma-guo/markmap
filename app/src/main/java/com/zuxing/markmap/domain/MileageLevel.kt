package com.zuxing.markmap.domain

enum class MileageKind(val label: String) {
    FLIGHT("飞行里程"),
    RAIL("铁路里程"),
    TOTAL("总里程")
}

data class MileageLevel(val name: String, val minKm: Double)

object Mileage {

    private val flight = listOf(
        MileageLevel("青铜", 0.0),
        MileageLevel("白银", 5_000.0),
        MileageLevel("黄金", 20_000.0),
        MileageLevel("铂金", 50_000.0),
        MileageLevel("钻石", 100_000.0),
        MileageLevel("白金", 200_000.0)
    )
    private val rail = listOf(
        MileageLevel("青铜", 0.0),
        MileageLevel("白银", 2_000.0),
        MileageLevel("黄金", 8_000.0),
        MileageLevel("铂金", 20_000.0),
        MileageLevel("钻石", 50_000.0),
        MileageLevel("白金", 100_000.0)
    )
    private val total = listOf(
        MileageLevel("青铜", 0.0),
        MileageLevel("白银", 10_000.0),
        MileageLevel("黄金", 30_000.0),
        MileageLevel("铂金", 80_000.0),
        MileageLevel("钻石", 150_000.0),
        MileageLevel("白金", 300_000.0)
    )

    fun levels(kind: MileageKind): List<MileageLevel> = when (kind) {
        MileageKind.FLIGHT -> flight
        MileageKind.RAIL -> rail
        MileageKind.TOTAL -> total
    }

    data class Progress(
        val kind: MileageKind,
        val current: MileageLevel,
        val next: MileageLevel?,
        val ratio: Float,
        val remainKm: Double
    )

    fun progress(km: Double, kind: MileageKind): Progress {
        val levels = levels(kind)
        var current = levels.first()
        var next: MileageLevel? = null
        for (i in levels.indices) {
            if (km >= levels[i].minKm) {
                current = levels[i]
                next = levels.getOrNull(i + 1)
            }
        }
        val ratio = if (next == null) 1f else {
            val span = (next.minKm - current.minKm).coerceAtLeast(1.0)
            ((km - current.minKm) / span).coerceIn(0.0, 1.0).toFloat()
        }
        val remain = next?.let { (it.minKm - km).coerceAtLeast(0.0) } ?: 0.0
        return Progress(kind, current, next, ratio, remain)
    }
}
