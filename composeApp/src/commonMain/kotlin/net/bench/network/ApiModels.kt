package net.bench.network

import kotlinx.serialization.Serializable

@Serializable
data class SampleDto(
    val id: Int? = null,
    val title: String? = null,
    val body: String? = null,
)
