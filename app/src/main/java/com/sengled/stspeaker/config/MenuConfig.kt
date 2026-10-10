package com.sengled.stspeaker.config

import android.content.Context

/**
 * Configuration settings for menu options in [com.sengled.stspeaker.widget.SettingsMenuPopupWindow].
 */
object MenuConfig {
    private const val PREFS_NAME = "pulse_prefs"
    private const val KEY_SHOW_DEVICES_MANAGEMENT = "show_devices_management"
    private const val KEY_SHOW_LAUNCH_MEDIA_APPS = "show_launch_media_apps"

    /**
     * Whether "Devices Management" option is visible in the settings menu.
     * Defaults to false (hidden).
     */
    fun isDevicesManagementEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_DEVICES_MANAGEMENT, false)
    }

    /**
     * Whether "Launch Media Apps" option is visible in the settings menu.
     * Defaults to false (hidden).
     */
    fun isLaunchMediaAppsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_LAUNCH_MEDIA_APPS, false)
    }

    fun setDevicesManagementEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_DEVICES_MANAGEMENT, enabled)
            .apply()
    }

    fun setLaunchMediaAppsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_LAUNCH_MEDIA_APPS, enabled)
            .apply()
    }
}
