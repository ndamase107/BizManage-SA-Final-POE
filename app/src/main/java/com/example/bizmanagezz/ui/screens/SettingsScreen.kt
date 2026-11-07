package com.example.bizmanagezz.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.example.bizmanagezz.R
import com.example.bizmanagezz.data.model.Business
import com.example.bizmanagezz.data.model.UserSettings
import com.example.bizmanagezz.data.repository.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val context = LocalContext.current

    val store = remember(context) {
        SettingsStore(context.applicationContext)
    }

    val scope = rememberCoroutineScope()

    val initialSettings = remember(store) {
        store.readSettings()
    }

    val initialBusiness = remember(store) {
        store.readBusiness()
    }

    val languageOptions = listOf(
        "English" to "en",
        "isiZulu" to "zu",
        "Sesotho" to "st"
    )

    val currentTag = AppCompatDelegate
        .getApplicationLocales()
        .get(0)
        ?.language
        ?: context.resources.configuration.locales[0].language

    var selectedTag by rememberSaveable {
        mutableStateOf(
            currentTag.takeIf { tag ->
                languageOptions.any {
                    it.second == tag
                }
            } ?: "en"
        )
    }

    var notifications by rememberSaveable {
        mutableStateOf(
            initialSettings.notificationsEnabled
        )
    }

    var businessName by rememberSaveable {
        mutableStateOf(initialBusiness.name)
    }

    var businessType by rememberSaveable {
        mutableStateOf(initialBusiness.type)
    }

    var contact by rememberSaveable {
        mutableStateOf(initialBusiness.contact)
    }

    var address by rememberSaveable {
        mutableStateOf(initialBusiness.address)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val nameRequired = stringResource(
        R.string.bm_name_required
    )

    val saveFailed = stringResource(
        R.string.bm_save_failed
    )

    val savedMessage = stringResource(
        R.string.bm_saved
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                R.string.bm_settings
            ),
            style = MaterialTheme.typography.headlineMedium
        )

        SyncStatusPanel()

        // Available in debug builds for connection diagnostics.
        CloudConnectionCheck()

        HorizontalDivider()

        Text(
            text = stringResource(
                R.string.bm_language
            ),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languageOptions.forEach { (name, tag) ->
                FilterChip(
                    selected = selectedTag == tag,
                    enabled = !saving,
                    onClick = {
                        selectedTag = tag
                        message = null
                        error = null
                    },
                    label = {
                        Text(name)
                    }
                )
            }
        }

        Text(
            text = stringResource(
                R.string.bm_language_help
            ),
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.bm_notifications
                ),
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = notifications,
                enabled = !saving,
                onCheckedChange = {
                    notifications = it
                    message = null
                    error = null
                }
            )
        }

        Text(
            text = stringResource(
                R.string.bm_notification_help
            ),
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()

        Text(
            text = stringResource(
                R.string.bm_business_details
            ),
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = businessName,
            onValueChange = {
                businessName = it
                message = null
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        R.string.bm_business_name
                    )
                )
            },
            enabled = !saving,
            singleLine = true
        )

        OutlinedTextField(
            value = businessType,
            onValueChange = {
                businessType = it
                message = null
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        R.string.bm_business_type
                    )
                )
            },
            enabled = !saving,
            singleLine = true
        )

        OutlinedTextField(
            value = contact,
            onValueChange = {
                contact = it
                message = null
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        R.string.bm_contact
                    )
                )
            },
            enabled = !saving,
            singleLine = true
        )

        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it
                message = null
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        R.string.bm_address
                    )
                )
            },
            enabled = !saving,
            minLines = 2
        )

        error?.let { errorText ->
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error
            )
        }

        message?.let { messageText ->
            Text(
                text = messageText
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            onClick = {
                error = null
                message = null

                if (businessName.isBlank()) {
                    error = nameRequired
                } else {
                    saving = true

                    val languageName = languageOptions
                        .first {
                            it.second == selectedTag
                        }
                        .first

                    val settings = UserSettings(
                        language = languageName,
                        notificationsEnabled = notifications
                    )

                    val business = Business(
                        name = businessName.trim(),
                        type = businessType.trim(),
                        contact = contact.trim(),
                        address = address.trim()
                    )

                    scope.launch {
                        try {
                            store.save(
                                settings = settings,
                                business = business
                            )

                            com.google.firebase.auth.FirebaseAuth
                                .getInstance()
                                .currentUser
                                ?.uid
                                ?.let {
                                    com.example.bizmanagezz.data.notifications.NotificationRegistration
                                        .schedule(context.applicationContext, it)
                                }

                            message = savedMessage
                        } catch (
                            cancelled: CancellationException
                        ) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = saveFailed
                            saving = false
                            return@launch
                        }

                        saving = false

                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(
                                selectedTag
                            )
                        )
                    }
                }
            }
        ) {
            Text(
                text = stringResource(
                    if (saving) {
                        R.string.bm_saving
                    } else {
                        R.string.bm_save_settings
                    }
                )
            )
        }

        OutlinedButton(
            onClick = onLogout,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    R.string.bm_logout
                )
            )
        }
    }
}