package com.fitnesslemon.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.fitnesslemon.app.data.models.DeviceInfo

object DeviceUtils {

    fun getImei(context: Context): String? {
        return try {
            val telephonyManager = ContextCompat.getSystemService(
                context,
                TelephonyManager::class.java
            ) as? TelephonyManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager?.imei
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.deviceId
            }
        } catch (e: Exception) {
            // Если нет разрешения или ошибка - возвращаем null
            null
        }
    }

    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }

    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE
    }

    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            imei = getImei(context),
            androidId = getAndroidId(context),
            model = getDeviceModel(),
            androidVersion = getAndroidVersion(),
            appVersion = getAppVersion(context)
        )
    }
}