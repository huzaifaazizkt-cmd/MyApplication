package com.example.myapplication.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread bridge for the service and its non-exported activity. */
object AppLockServiceHolder {
    internal var service: AppLockService? = null
    private val connected = MutableStateFlow(false)
    val isConnected = connected.asStateFlow()
    private val status = MutableStateFlow(ProtectionStatus.DISCONNECTED)
    val state = status.asStateFlow()
    private val notification = MutableStateFlow(ProtectionNotificationStatus.AVAILABLE)
    val notificationState = notification.asStateFlow()
    internal fun setStatus(value: ProtectionStatus) {
        status.value = value
        connected.value = value == ProtectionStatus.READY
    }
    internal fun setNotificationStatus(value: ProtectionNotificationStatus) { notification.value = value }
    fun refreshNotification() { service?.refreshNotification() }
    fun onAuthenticationUiChanged() { service?.verifyAfterAuthenticationUi() }
    fun onLockVisible(pkg: String) = service?.onLockVisible(pkg)
    fun onLockHidden(pkg: String) = service?.onLockHidden(pkg)
    fun authenticate(pkg: String): Boolean = service?.authenticate(pkg) == true
    fun isChallengeRequired(pkg: String): Boolean = service?.isChallengeRequired(pkg) == true
    fun dismiss(pkg: String) = service?.dismiss(pkg)
}
