package com.zuxing.markmap.domain

import com.google.gson.Gson

/**
 * 可保存的合并方案。
 */
data class MergeConfig(
    val segments: List<MergeSegmentConfig> = emptyList(),
    val includeConnectors: Boolean = true,
    val showSegmentColors: Boolean = true
) {
    data class MergeSegmentConfig(
        val segmentId: Long,
        val reversed: Boolean = false
    )

    fun toJson(): String = Gson().toJson(this)

    /** 按保存的顺序与方向重排输入；缺失的路段会被忽略。 */
    fun order(inputs: List<TrackMerger.SegmentInput>): List<TrackMerger.SegmentInput> {
        val byId = inputs.associateBy { it.id }
        return segments.mapNotNull { config ->
            byId[config.segmentId]?.copy(reversed = config.reversed)
        }
    }

    companion object {
        fun fromJson(json: String?): MergeConfig? {
            if (json.isNullOrBlank()) return null
            return try {
                Gson().fromJson(json, MergeConfig::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
