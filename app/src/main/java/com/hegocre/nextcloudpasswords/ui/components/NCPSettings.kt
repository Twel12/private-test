package com.hegocre.nextcloudpasswords.ui.components

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.NCPScreen
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import foundation.e.elib.compose.components.ETopAppBar
import foundation.e.elib.compose.theme.ETheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NCPSettingsScreen(
    onNavigationUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember {
        PreferencesManager.getInstance(context)
    }

    ETheme {
        Scaffold(
            topBar = {
                ETopAppBar(
                    title = {
                        Text(stringResource(R.string.screen_settings))
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigationUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigation_back)
                            )
                        }
                    },
                    windowInsets = WindowInsets.statusBars
                )
            },
            bottomBar = {
                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                )
            }
        )
        { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                PreferencesCategory(title = { Text(stringResource(R.string.preferences_category_general)) }) {
                    val selectedScreen by preferencesManager.getStartScreen()
                        .collectAsState(
                            initial = NCPScreen.Passwords.name,
                            context = Dispatchers.IO
                        )

                    val startViews = mapOf(
                        NCPScreen.Passwords.name to stringResource(NCPScreen.Passwords.title),
                        NCPScreen.Favorites.name to stringResource(NCPScreen.Favorites.title)
                    )

                    ListPreference(
                        items = startViews,
                        onItemSelected = {
                            scope.launch(Dispatchers.IO) {
                                preferencesManager.setStartScreen(it)
                            }
                        },
                        title = { Text(text = stringResource(id = R.string.start_view_preference_title)) },
                        selectedItem = selectedScreen
                    )

                    val orderBy by preferencesManager.getOrderBy()
                        .collectAsState(initial = PreferencesManager.ORDER_BY_TITLE_ASCENDING, context = Dispatchers.IO)
                    val orderByOptions = mapOf(
                        PreferencesManager.ORDER_BY_TITLE_ASCENDING to stringResource(R.string.preference_order_by_title_asc),
                        PreferencesManager.ORDER_BY_TITLE_DESCENDING to stringResource(R.string.preference_order_by_title_desc),
                        PreferencesManager.ORDER_BY_DATE_DESCENDING to stringResource(R.string.preference_order_by_date_desc),
                        PreferencesManager.ORDER_BY_DATE_ASCENDING to stringResource(R.string.preference_order_by_date_asc)
                    )
                    ListPreference(
                        items = orderByOptions,
                        onItemSelected = {
                            scope.launch(Dispatchers.IO) {
                                preferencesManager.setOrderBy(it)
                            }
                        },
                        title = { Text(text = stringResource(id = R.string.order_by_preference_title)) },
                        selectedItem = orderBy
                    )

                    val showIcons by preferencesManager.getShowIcons()
                        .collectAsState(initial = false, context = Dispatchers.IO)
                    SwitchPreference(
                        checked = showIcons,
                        onCheckedChange = { show ->
                            scope.launch(Dispatchers.IO) {
                                preferencesManager.setShowIcons(show)
                            }
                        },
                        title = { Text(stringResource(R.string.show_icons_preference_title)) },
                        subtitle = { Text(stringResource(R.string.show_icons_preference_subtitle)) }
                    )
                }

                PreferencesCategory(title = { Text(stringResource(R.string.preferences_category_search)) }) {
                    val useStringUrlMatching by preferencesManager.getUseStrictUrlMatching()
                        .collectAsState(initial = true, context = Dispatchers.IO)
                    SwitchPreference(
                        checked = useStringUrlMatching,
                        onCheckedChange = { useStrict ->
                            scope.launch(Dispatchers.IO) {
                                preferencesManager.setUseStrictUrlMatching(useStrict)
                            }
                        },
                        title = { Text(stringResource(R.string.use_strict_domain_matching_preference_title)) },
                        subtitle = { Text(stringResource(R.string.use_strict_domain_matching_preference_subtitle)) }
                    )

                    val searchByUsername by preferencesManager.getSearchByUsername()
                        .collectAsState(initial = true, context = Dispatchers.IO)
                    SwitchPreference(
                        checked = searchByUsername,
                        onCheckedChange = { search ->
                            scope.launch(Dispatchers.IO) {
                                preferencesManager.setSearchByUsername(search)
                            }
                        },
                        title = { Text(stringResource(R.string.search_by_username_preference_title)) },
                        subtitle = { Text(stringResource(R.string.search_by_username_preference_subtitle)) }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val autofillManager = context.getSystemService(AutofillManager::class.java)
                    var autofillEnabled by remember { mutableStateOf(autofillManager.hasEnabledAutofillServices()) }
                    val launchAutofillRequest = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            autofillEnabled = true
                        }
                    }
                    PreferencesCategory(title = { Text(text = stringResource(R.string.preferences_category_autofill_service)) }) {
                        SwitchPreference(
                            checked = autofillEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    val intent =
                                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                            data = "package:${context.packageName}".toUri()
                                        }
                                    launchAutofillRequest.launch(intent)
                                }
                            },
                            title = { Text(stringResource(R.string.autofill_preference_title)) },
                            subtitle = { Text(stringResource(R.string.autofill_preference_subtitle)) },
                            enabled = !autofillEnabled
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            var useInlineAutofill by remember { mutableStateOf(preferencesManager.getUseInlineAutofill()) }

                            SwitchPreference(
                                checked = useInlineAutofill,
                                onCheckedChange = { enabled ->
                                    scope.launch(Dispatchers.IO) {
                                        if (preferencesManager.setUseInlineAutofill(enabled)) {
                                            useInlineAutofill = enabled
                                        }
                                    }
                                },
                                title = { Text(text = stringResource(id = R.string.inline_autofill_preference_title)) },
                                subtitle = { Text(text = stringResource(id = R.string.inline_autofill_preference_subtitle)) },
                                enabled = autofillEnabled
                            )
                        }
                    }
                }
            }
        }
    }
}
