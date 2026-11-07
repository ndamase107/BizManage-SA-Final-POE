package com.example.bizmanagezz.data.repository

import android.content.Context
import com.example.bizmanagezz.data.local.AccountStorage
import com.example.bizmanagezz.data.model.Business
import com.example.bizmanagezz.data.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsStore(context: Context) {
    private val accountId = AccountStorage.currentUid()

    private val suffix = AccountStorage.suffix(
        context,
        accountId
    )

    private val preferences = context.applicationContext
        .getSharedPreferences(
            "bizmanage_settings$suffix",
            Context.MODE_PRIVATE
        )

    private fun checkAccount() {
        check(AccountStorage.currentUid() == accountId) {
            "The signed-in account changed. Reopen Settings."
        }
    }

    fun readSettings(): UserSettings {
        checkAccount()

        return UserSettings(
            language = preferences.getString(
                "language",
                "English"
            ) ?: "English",
            biometricEnabled = false,
            notificationsEnabled = preferences.getBoolean(
                "notifications",
                true
            )
        )
    }

    fun readBusiness(): Business {
        checkAccount()

        return Business(
            name = preferences.getString("business_name", "") ?: "",
            type = preferences.getString("business_type", "") ?: "",
            contact = preferences.getString("business_contact", "") ?: "",
            address = preferences.getString("business_address", "") ?: ""
        )
    }

    suspend fun save(
        settings: UserSettings,
        business: Business
    ) {
        checkAccount()

        require(
            settings.language in listOf(
                "English",
                "isiZulu",
                "Sesotho"
            )
        ) {
            "Choose a supported language."
        }

        require(business.name.isNotBlank()) {
            "Enter your business name."
        }

        withContext(Dispatchers.IO) {
            checkAccount()

            val successful = preferences.edit()
                .putString("language", settings.language)
                .putBoolean(
                    "notifications",
                    settings.notificationsEnabled
                )
                .putString("business_name", business.name.trim())
                .putString("business_type", business.type.trim())
                .putString("business_contact", business.contact.trim())
                .putString("business_address", business.address.trim())
                .commit()

            check(successful) {
                "Could not save settings. Check available storage."
            }
        }
    }
}