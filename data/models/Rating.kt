package com.fitnesslemon.app.data.models

data class Rating(
    val id: Int,
    val userName: String,
    val rating: Int,
    val comment: String,
    val date: String,
    val isApproved: Boolean
)