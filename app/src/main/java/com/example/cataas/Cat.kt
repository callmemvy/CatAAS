package com.example.cataas

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

data class Cat(
    val id: String,
    @SerializedName("created_at")
    val createdAt: LocalDateTime,
    val tags: List<String>,
)
