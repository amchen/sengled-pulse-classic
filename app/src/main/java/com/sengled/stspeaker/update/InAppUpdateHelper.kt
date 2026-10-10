package com.sengled.stspeaker.update

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Helper class to check and trigger Google Play In-App Updates on app startup.
 */
object InAppUpdateHelper {

    private const val TAG = "InAppUpdateHelper"
    const val REQUEST_CODE_UPDATE = 1001

    /**
     * Checks if an update is available on Google Play and starts the update flow if necessary.
     *
     * @param activity The host [Activity] to launch the update UI flow.
     * @param updateType [AppUpdateType.IMMEDIATE] or [AppUpdateType.FLEXIBLE]. Defaults to [AppUpdateType.IMMEDIATE].
     * @param requestCode Request code passed to [Activity.onActivityResult] when update flow completes.
     * @param onNoUpdateCallback Optional callback invoked when no update is available or the check fails.
     */
    fun checkForAppUpdate(
        activity: Activity,
        @AppUpdateType updateType: Int = AppUpdateType.IMMEDIATE,
        requestCode: Int = REQUEST_CODE_UPDATE,
        onNoUpdateCallback: (() -> Unit)? = null
    ) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(updateType)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(updateType),
                        requestCode
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting in-app update flow: ${e.message}", e)
                    onNoUpdateCallback?.invoke()
                }
            } else {
                onNoUpdateCallback?.invoke()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for in-app updates: ${e.message}", e)
            onNoUpdateCallback?.invoke()
        }
    }

    /**
     * Call in [Activity.onResume] to resume an in-progress update.
     * This ensures that if a user leaves the app during an immediate update,
     * the update flow is resumed when returning to the app.
     */
    fun resumeUpdateIfNeeded(
        activity: Activity,
        @AppUpdateType updateType: Int = AppUpdateType.IMMEDIATE,
        requestCode: Int = REQUEST_CODE_UPDATE
    ) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(updateType),
                        requestCode
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error resuming in-app update flow: ${e.message}", e)
                }
            }
        }
    }
}
