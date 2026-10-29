package com.example.myapplication.service

data class ProtectionSettings(
    val enabled: Boolean, val lockedApps: Set<String>,
    val mode: RelockPolicy.Mode, val delayMillis: Long
)
