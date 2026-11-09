package com.goorder.domain.model

import java.util.UUID

data class PrintRequest(
    val id: String = UUID.randomUUID().toString(),
    val data: String,
    val isPrinted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
