package com.zuxing.markmap.domain

/**
 * 路段交通方式。落库时使用 name，展示时使用 label。
 */
enum class TransportMode(val label: String, val colorHex: String) {
    FLIGHT("飞行", "#4A6FA5"),
    TRAIN("火车", "#2E5D5C"),
    DRIVE("自驾", "#B96A4B"),
    WALK("徒步", "#6B8E6B"),
    FERRY("轮渡", "#5C7A99"),
    OTHER("其他", "#8A8A8A");

    companion object {
        fun from(value: String?): TransportMode =
            values().firstOrNull { it.name == value } ?: OTHER
    }
}
