package com.example.myapplication.service

import org.junit.Assert.*
import org.junit.Test

class AppLockAccessTest {
    @Test fun missingUsageOrOverlayPreventsProtectionBeingReportedReady() {
        assertFalse(AppLockAccess(usage = false, overlay = true).ready)
        assertFalse(AppLockAccess(usage = true, overlay = false).ready)
        assertTrue(AppLockAccess(usage = true, overlay = true).ready)
    }
    @Test fun detectedVendorPopupDenialIsRequiredOnlyOnAnApplicableDevice() {
        assertFalse(AppLockAccess(true, true, popupRequired = true, popupGranted = false).ready)
        assertTrue(AppLockAccess(true, true, popupRequired = true, popupGranted = true).ready)
        assertTrue(AppLockAccess(true, true, popupRequired = false, popupGranted = false).ready)
    }
}
