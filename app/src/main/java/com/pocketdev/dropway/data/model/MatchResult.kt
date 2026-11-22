package com.pocketdev.dropway.data.model

data class MatchResult(
    val house: House,
    val score: Int,
    val level: String, // "exact", "high", "medium"
    val reasons: List<String>,
    val conflicts: List<String> = emptyList(),
    val trackingCandidate: String? = null
)
