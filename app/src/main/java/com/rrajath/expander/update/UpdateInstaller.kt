package com.rrajath.expander.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

internal enum class InstallLaunchResult {
    INSTALLER,
    PERMISSION_SETTINGS,
}

internal object UpdateInstaller {
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun launch(context: Context, apkFile: File): InstallLaunchResult {
        if (!canInstallPackages(context)) {
            openPermissionSettings(context)
            return InstallLaunchResult.PERMISSION_SETTINGS
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            throw UpdateException("Android could not find an APK installer.")
        }
        context.startActivity(intent)
        return InstallLaunchResult.INSTALLER
    }

    fun openPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            throw UpdateException("Android could not open the install permission settings.")
        }
        context.startActivity(intent)
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
