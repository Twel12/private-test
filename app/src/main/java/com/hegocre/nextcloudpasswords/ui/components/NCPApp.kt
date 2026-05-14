package com.hegocre.nextcloudpasswords.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillPendingSaveStore
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillSaveInteractionActivity
import com.hegocre.nextcloudpasswords.services.autofill.NCPPasswordBackend
import com.hegocre.nextcloudpasswords.ui.NCPScreen
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import foundation.e.autofill.PasswordSaveResult
import foundation.e.elib.compose.components.EFloatingActionButtonExtended
import foundation.e.elib.compose.components.EModalBottomSheet
import foundation.e.elib.compose.theme.ETheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudPasswordsApp(
    passwordsViewModel: PasswordsViewModel,
    onLogOut: () -> Unit,
    isAutofillRequest: Boolean = false,
    isAutofillUnlockRequest: Boolean = false,
    pendingAutofillSave: NCPAutofillPendingSaveStore.PendingSave? = null,
    defaultSearchQuery: String = "",
    onAutofillUnlockComplete: (() -> Unit)? = null,
    onPendingAutofillSaveComplete: ((PasswordSaveResult) -> Unit)? = null,
    replyAutofill: ((String, String, String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val navController = rememberNavController()
    val backstackEntry = navController.currentBackStackEntryAsState()
    val currentScreen = NCPScreen.fromRoute(
        backstackEntry.value?.destination?.route
    )

    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val needsMasterPassword by passwordsViewModel.needsMasterPassword.collectAsState()
    val masterPasswordInvalid by passwordsViewModel.masterPasswordInvalid.collectAsState()

    val sessionOpen by passwordsViewModel.sessionOpen.collectAsState()
    val showSessionOpenError by passwordsViewModel.showSessionOpenError.collectAsState()
    val isRefreshing by passwordsViewModel.isRefreshing.collectAsState()

    var showLogOutDialog by rememberSaveable { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingSaveToComplete by remember { mutableStateOf(pendingAutofillSave) }
    var unlockCompleteDelivered by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isAutofillRequest) searchExpanded = true
    }
    LaunchedEffect(isAutofillUnlockRequest) {
        if (isAutofillUnlockRequest) {
            passwordsViewModel.requestMasterPassword()
        }
    }
    LaunchedEffect(sessionOpen, pendingSaveToComplete) {
        if (!sessionOpen || !isAutofillUnlockRequest) return@LaunchedEffect
        val pendingSave = pendingSaveToComplete
        if (pendingSave == null) {
            if (!unlockCompleteDelivered) {
                unlockCompleteDelivered = true
                onAutofillUnlockComplete?.invoke()
            }
            return@LaunchedEffect
        }
        pendingSaveToComplete = null
        val backend = NCPApplication.passwordBackend(context) as? NCPPasswordBackend
            ?: return@LaunchedEffect
        val saveResult = if (pendingSave.createNew || pendingSave.selectedCredentialId != null) {
            backend.saveFromUserInteraction(
                request = pendingSave.request,
                selectedCredentialId = pendingSave.selectedCredentialId
            )
        } else {
            backend.save(pendingSave.request)
        }
        when (saveResult) {
            PasswordSaveResult.Saved,
            PasswordSaveResult.DuplicateIgnored,
            is PasswordSaveResult.QueuedForRetry -> {
                Toast.makeText(context, R.string.autofill_password_saved, Toast.LENGTH_SHORT).show()
                onPendingAutofillSaveComplete?.invoke(saveResult)
            }

            PasswordSaveResult.NeedsUnlock -> {
                pendingSaveToComplete = pendingSave
                passwordsViewModel.requestMasterPassword()
            }

            is PasswordSaveResult.NeedsUserInteraction -> {
                context.startActivity(
                    NCPAutofillSaveInteractionActivity.intent(context, pendingSave.request)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onPendingAutofillSaveComplete?.invoke(saveResult)
            }

            is PasswordSaveResult.Failed -> {
                Toast.makeText(
                    context,
                    saveResult.message ?: context.getString(R.string.error_password_saving_failed),
                    Toast.LENGTH_LONG
                ).show()
                onPendingAutofillSaveComplete?.invoke(saveResult)
            }
        }
    }
    LaunchedEffect(currentScreen) {
        fabMenuExpanded = false
    }
    BackHandler(enabled = fabMenuExpanded) {
        fabMenuExpanded = false
    }
    val fabIconRotation by animateFloatAsState(
        targetValue = if (fabMenuExpanded) 45f else 0f,
        label = "fabIconRotation"
    )
    val (searchQuery, setSearchQuery) = rememberSaveable { mutableStateOf(defaultSearchQuery) }

    val showLogoutAction = passwordsViewModel.supportsLocalLogout

    ETheme {
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
            rememberTopAppBarState()
        )

        Scaffold(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            topBar = {
                if (currentScreen != NCPScreen.PasswordEdit && currentScreen != NCPScreen.FolderEdit) {
                    val username = remember { passwordsViewModel.server?.username ?: "" }
                    val url = remember {
                        passwordsViewModel.server?.url ?: ""
                    }
                    NCPSearchTopBar(
                        username = username,
                        serverAddress = url,
                        title = when (currentScreen) {
                            NCPScreen.Passwords -> {
                                passwordsViewModel.visibleFolder.value?.let {
                                    if (it.id == FoldersApi.DEFAULT_FOLDER_UUID) {
                                        stringResource(currentScreen.title)
                                    } else {
                                        it.label
                                    }
                                } ?: stringResource(currentScreen.title)
                            }
                            NCPScreen.Favorites -> stringResource(currentScreen.title)
                            else -> ""
                        },
                        userAvatar = { size ->
                            Image(
                                painter = passwordsViewModel.getPainterForAvatar(),
                                contentDescription = "",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .size(size)
                            )
                        },
                        searchQuery = searchQuery,
                        setSearchQuery = setSearchQuery,
                        isAutofill = isAutofillRequest,
                        searchExpanded = searchExpanded,
                        onSearchClick = { searchExpanded = true },
                        onSearchCloseClick = {
                            searchExpanded = false
                            setSearchQuery("")
                        },
                        showLogoutAction = showLogoutAction,
                        onLogoutClick = { showLogOutDialog = true },
                        showNavigationIcon = currentScreen == NCPScreen.Passwords &&
                                passwordsViewModel.visibleFolder.value?.id != null &&
                                passwordsViewModel.visibleFolder.value?.id != FoldersApi.DEFAULT_FOLDER_UUID,
                        onNavigationClick = { navController.navigateUp() },
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    TopAppBar(
                        title = { Text(text = stringResource(id = currentScreen.title)) },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(id = R.string.navigation_back)
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = !sessionOpen && showSessionOpenError && !isRefreshing) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.clickable { (passwordsViewModel.sync()) }
                        ) {
                            Text(
                                text = stringResource(id = R.string.error_cannot_connect_to_server),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }
                    val navigationHeight =
                        WindowInsets.navigationBars.getBottom(LocalDensity.current)
                    AnimatedVisibility(
                        visible = currentScreen != NCPScreen.PasswordEdit
                                && currentScreen != NCPScreen.FolderEdit,
                        enter = slideInVertically(initialOffsetY = { (it + navigationHeight) }),
                        exit = slideOutVertically(targetOffsetY = { (it + navigationHeight) })
                    ) {
                        NCPBottomNavigation(
                            allScreens = NCPScreen.entries.filter { !it.hidden },
                            currentScreen = currentScreen,
                            onScreenSelected = { screen ->
                                navController.navigate(screen.name) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = currentScreen != NCPScreen.PasswordEdit &&
                            currentScreen != NCPScreen.FolderEdit &&
                            currentScreen != NCPScreen.Favorites &&
                            sessionOpen,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedVisibility(visible = fabMenuExpanded) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FloatingActionMenuItem(
                                    label = stringResource(R.string.password),
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.VpnKey,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        fabMenuExpanded = false
                                        navController.navigate("${NCPScreen.PasswordEdit.name}/none")
                                    }
                                )
                                FloatingActionMenuItem(
                                    label = stringResource(R.string.folder),
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        fabMenuExpanded = false
                                        navController.navigate("${NCPScreen.FolderEdit.name}/none")
                                    }
                                )
                            }
                        }

                        EFloatingActionButtonExtended(
                            onClick = {
                                fabMenuExpanded = !fabMenuExpanded
                            },
                            text =  {Text(text = stringResource(R.string.passwords_floating_action_button))},
                            icon = {Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(id = R.string.action_create_element),
                                modifier = Modifier.rotate(fabIconRotation)
                            )}
                        )
                    }
                }
            }
        ) { innerPadding ->
            NCPNavHost(
                modifier = Modifier.padding(innerPadding),
                navController = navController,
                passwordsViewModel = passwordsViewModel,
                searchQuery = searchQuery,
                isAutofillRequest = isAutofillRequest,
                modalSheetState = modalSheetState,
                openPasswordDetails = { password, folderPath ->
                    passwordsViewModel.setVisiblePassword(password, folderPath)
                    keyboardController?.hide()
                    openBottomSheet = true
                },
                replyAutofill = replyAutofill,
                searchVisibility = searchExpanded,
                closeSearch = {
                    searchExpanded = false
                    setSearchQuery("")
                },
            )

            if (showLogOutDialog) {
                LogOutDialog(
                    onDismissRequest = { showLogOutDialog = false },
                    onConfirmButton = onLogOut
                )
            }

            if (needsMasterPassword) {
                val (masterPassword, setMasterPassword) = rememberSaveable {
                    mutableStateOf("")
                }
                val (savePassword, setSavePassword) = rememberSaveable {
                    mutableStateOf(false)
                }
                MasterPasswordDialog(
                    masterPassword = masterPassword,
                    setMasterPassword = { newValue ->
                        if (masterPasswordInvalid) {
                            passwordsViewModel.clearMasterPasswordInvalid()
                        }
                        setMasterPassword(newValue)
                    },
                    savePassword = savePassword,
                    setSavePassword = setSavePassword,
                    onOkClick = {
                        passwordsViewModel.setMasterPassword(masterPassword, savePassword)
                    },
                    errorText = if (masterPasswordInvalid) stringResource(R.string.error_invalid_password) else "",
                    onDismissRequest = { }
                )
            }

            if (openBottomSheet) {
                EModalBottomSheet(
                    onDismissRequest = { openBottomSheet = false },
                    contentWindowInsets = { WindowInsets.navigationBars },
                    sheetState = modalSheetState
                ) {
                    PasswordItem(
                        passwordInfo = passwordsViewModel.visiblePassword.value,
                        onEditPassword = if (sessionOpen &&
                            (passwordsViewModel.visiblePassword.value?.first?.canEdit() == true)
                        ) {
                            {
                                coroutineScope.launch {
                                    modalSheetState.hide()
                                }.invokeOnCompletion {
                                    if (!modalSheetState.isVisible) {
                                        openBottomSheet = false
                                    }
                                }
                                navController.navigate("${NCPScreen.PasswordEdit.name}/${passwordsViewModel.visiblePassword.value?.first?.id ?: "none"}")
                            }
                        } else null,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingActionMenuItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            icon()
            Text(text = label)
        }
    }
}
