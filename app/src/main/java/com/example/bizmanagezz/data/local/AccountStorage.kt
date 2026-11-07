package com.example.bizmanagezz.data.local

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest

object AccountStorage {
    private const val PREFERENCES = "bizmanage_account_storage"
    private const val LEGACY_OWNER = "legacy_owner"

    fun currentUid(): String {
        return requireNotNull(
            FirebaseAuth.getInstance().currentUser?.uid
        ) {
            "Sign in before accessing business records."
        }
    }

    @Synchronized
    fun suffix(context: Context, uid: String): String {
        require(uid.isNotBlank()) {
            "An account ID is required."
        }

        check(currentUid() == uid) {
            "The signed-in account changed. Reopen this screen."
        }

        val preferences = context.applicationContext
            .getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            )

        var owner = preferences.getString(LEGACY_OWNER, null)

        // Assign existing single-account data once.
        // Later accounts receive separate storage.
        if (owner == null) {
            check(
                preferences.edit()
                    .putString(LEGACY_OWNER, uid)
                    .commit()
            ) {
                "Could not configure account storage."
            }

            owner = uid
        }

        if (owner == uid) {
            return ""
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))

        return "_" + digest.joinToString("") {
            (it.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
    }
}