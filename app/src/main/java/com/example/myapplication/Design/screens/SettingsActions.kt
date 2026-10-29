package com.example.myapplication.Design.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.myapplication.R

internal object SettingsActions {
    fun open(context: Context, action: String) {
        val listing = context.getString(R.string.store_listing_url)
        val intent = when (action) {
            "share" -> Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_message, listing))
            }, context.getString(R.string.share))
            "privacy_policy" -> Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))
            "terms_of_use" -> Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.terms_of_use_url)))
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(listing))
        }
        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, R.string.external_action_failed, Toast.LENGTH_LONG).show()
        }
    }
}
