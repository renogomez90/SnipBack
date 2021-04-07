package com.hipoint.snipback.Utils

data class TagFilter(
    val hasAudio: Boolean,
    val hasShareLater: Boolean,
    val hasLinkLater: Boolean,
    val hasColour: List<String>,
    val hasText: List<String>){
    override fun toString(): String {
        return """
            | hasAudio = $hasAudio
            | hasShareLater = $hasShareLater
            | hasLinkLater = $hasLinkLater
            | hasColour = $hasColour
            | hasText = $hasText
        """.trimIndent()
    }
}