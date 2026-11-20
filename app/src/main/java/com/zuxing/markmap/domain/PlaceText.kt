package com.zuxing.markmap.domain

/**
 * 长地名结构化：拆成「主名（场所/景区）」与「副名（行政区）」，
 * 便于以多行完整展示，而不是行末省略。
 */
object PlaceText {

    data class Split(val main: String, val admin: String)

    fun split(
        description: String?,
        address: String?,
        province: String?,
        city: String?,
        district: String?
    ): Split {
        val adminParts = listOfNotNull(
            province?.takeIf { it.isNotBlank() },
            city?.takeIf { it.isNotBlank() },
            district?.takeIf { it.isNotBlank() }
        ).distinct()
        val admin = adminParts.joinToString(" · ")

        var main = description?.takeIf { it.isNotBlank() }

        if (main == null) {
            val addr: String? = address?.takeIf { it.isNotBlank() }
            if (addr != null) {
                var stripped: String = addr
                for (part in adminParts) {
                    stripped = stripped.replace(part, "")
                }
                val trimmedLine = stripped.trim()
                main = if (trimmedLine.isBlank()) addr else trimmedLine
            }
        }

        if (main.isNullOrBlank()) main = "地点"

        return Split(main, admin.ifBlank { address ?: "" })
    }
}
