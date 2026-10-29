package com.example.myapplication.vault

import android.net.Uri

data class VaultMediaItem(
    val uri: Uri,
    val name: String,
    val bucketName: String,
    val isVideo: Boolean,
    val isVaultFile: Boolean = false,
    val vaultFilePath: String? = null,
    val pendingOriginalDelete: Boolean = false,
    val restoreUri: String? = null
)
