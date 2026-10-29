package com.example.myapplication.Design.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.R
import com.example.myapplication.XiaomiPermissionHelper
import com.example.myapplication.service.AppLockAccess
import com.example.myapplication.service.AppLockPermissions

/** Shared by initial setup and the app list; only missing mandatory access appears here. */
@Composable
internal fun PermissionRequiredDialog(access: AppLockAccess, onDismiss: (() -> Unit)? = null) {
    val context = LocalContext.current
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, R.string.external_action_failed, Toast.LENGTH_SHORT).show()
        }
    }
    Dialog(onDismissRequest = { onDismiss?.invoke() }, properties = DialogProperties(
        dismissOnBackPress = onDismiss != null, dismissOnClickOutside = onDismiss != null,
        usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.width((maxWidth * .92f).coerceAtMost(420.dp))
                .heightIn(max = maxHeight * .85f)
                .background(Color.White, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState()).padding(22.dp)) {
                Text(stringResource(R.string.permissions_required), color = Color(0xFF333333), fontSize = 20.sp)
                if (!access.overlay) {
                    PermissionAccessRow(R.drawable.group1, R.string.show_over_other_apps, R.string.allow_lock_screen) {
                        open(AppLockPermissions.overlayIntent(context))
                    }
                }
                if (!access.usage) {
                    PermissionAccessRow(R.drawable.group2, R.string.usage_access_title, R.string.usage_access_description) {
                        open(AppLockPermissions.usageIntent())
                    }
                }
                if (access.popupRequired && !access.popupGranted) {
                    PermissionAccessRow(R.drawable.group1, R.string.background_popup, R.string.background_popup_description) {
                        XiaomiPermissionHelper.openBackgroundPopupSettings(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionAccessRow(icon: Int, title: Int, description: Int, allow: () -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Color(0xFFE5E5E5))
    Row(verticalAlignment = Alignment.Top) {
        Image(painterResource(icon), null, Modifier.padding(top = 5.dp).size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = Color(0xFF333333), fontSize = 15.sp)
            Spacer(Modifier.height(7.dp))
            Text(stringResource(description), color = Color(0xFF777777), fontSize = 13.sp)
            Button(onClick = allow, modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
                Text(stringResource(R.string.allow))
            }
        }
    }
}
