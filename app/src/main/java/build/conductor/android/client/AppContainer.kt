package build.conductor.android.client

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import build.conductor.android.client.data.ApiConductorRepository
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.FileHomeSnapshots
import build.conductor.android.client.data.HomeSnapshots
import build.conductor.android.client.data.OpenQuestionTracker
import build.conductor.android.client.data.QuestionScanner
import build.conductor.android.client.data.RecentCache
import build.conductor.android.client.data.WorkspaceArchiver
import build.conductor.android.client.data.api.createConductorApi
import build.conductor.android.client.data.github.ApiGitHubRepository
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.github.createGitHubApi
import build.conductor.android.client.data.settings.AesGcmCipher
import build.conductor.android.client.data.settings.AppearanceStore
import build.conductor.android.client.data.settings.ColorSchemeChoice
import build.conductor.android.client.data.settings.SavedSecret
import build.conductor.android.client.data.settings.SecretStore
import build.conductor.android.client.data.settings.asSavedSecret
import build.conductor.android.client.data.settings.WorkspaceSubscriptionStore
import build.conductor.android.client.data.settings.androidKeystoreKey
import build.conductor.android.client.data.transcript.TranscriptSnapshot
import build.conductor.android.client.notify.StatusNotifier
import build.conductor.android.client.notify.WorkspaceWatcher
import build.conductor.android.client.ui.workspace.WorkspaceDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Creates the app's long-lived objects once. Screens get them through [LocalAppContainer]. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsDataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE) }
    private val keystoreKey by lazy { androidKeystoreKey(KEYSTORE_ALIAS) }
    private val secretCipher = AesGcmCipher { keystoreKey }

    val apiKeyStore = SecretStore(settingsDataStore, secretCipher, SecretStore.API_KEY)

    val apiKeyState: StateFlow<ApiKeyState> = apiKeyStore.secret
        .map { key -> if (key.isNullOrBlank()) ApiKeyState.Missing else ApiKeyState.Present(key) }
        .stateIn(applicationScope, SharingStarted.Eagerly, ApiKeyState.Loading)

    val gitHubTokenStore = SecretStore(settingsDataStore, secretCipher, SecretStore.GITHUB_TOKEN)

    val gitHubToken: StateFlow<SavedSecret> = gitHubTokenStore.secret.asSavedSecret()
        .stateIn(applicationScope, SharingStarted.Eagerly, SavedSecret.Loading)

    /** Waits for DataStore, so a screen does not ask for a token that is saved. */
    val isGitHubConnected: Flow<Boolean> = gitHubToken.filterNot { it == SavedSecret.Loading }.map { it is SavedSecret.Present }

    val gitHubRepository: GitHubRepository = ApiGitHubRepository(createGitHubApi(token = { (gitHubToken.value as? SavedSecret.Present)?.value }))

    val appearanceStore = AppearanceStore(settingsDataStore)

    /** Null until DataStore reads the saved choice, so the first frame does not show the wrong colours. */
    val colorScheme: StateFlow<ColorSchemeChoice?> = appearanceStore.colorScheme
        .stateIn(applicationScope, SharingStarted.Eagerly, null)

    val subscriptionStore = WorkspaceSubscriptionStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SUBSCRIPTIONS_FILE) },
    )

    /** The number of subscribed workspaces, or 0 without an API key. */
    val watchedWorkspaceCount: Flow<Int> = combine(apiKeyState.filterNot { it == ApiKeyState.Loading }, subscriptionStore.subscribedIds) { key, ids ->
        if (key is ApiKeyState.Present) ids.size else 0
    }

    val isWatching: Flow<Boolean> = watchedWorkspaceCount.map { it > 0 }

    val repository: ConductorRepository = ApiConductorRepository(
        createConductorApi(apiKey = { (apiKeyState.value as? ApiKeyState.Present)?.apiKey }),
    )

    val workspaceArchiver = WorkspaceArchiver(repository, CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate))

    val questionScanner = QuestionScanner(repository)

    val openQuestionTracker = OpenQuestionTracker(questionScanner)

    val workspaceWatcher = WorkspaceWatcher(repository, questionScanner, subscriptionStore, StatusNotifier(context.applicationContext))

    val homeSnapshots: HomeSnapshots = FileHomeSnapshots(File(context.cacheDir, HOME_SNAPSHOT_FILE))

    val transcripts = RecentCache<String, TranscriptSnapshot>(MAX_SAVED_TRANSCRIPTS)

    val workspaceDetails = RecentCache<String, WorkspaceDetail>(MAX_SAVED_WORKSPACES)

    init {
        // The first key is the one saved at start; a later key can belong to a different account.
        applicationScope.launch { apiKeyState.filterNot { it == ApiKeyState.Loading }.drop(1).collect { clearAccountData() } }
    }

    private suspend fun clearAccountData() {
        homeSnapshots.clear()
        transcripts.clear()
        workspaceDetails.clear()
    }

    private companion object {
        const val SETTINGS_FILE = "settings"
        const val SUBSCRIPTIONS_FILE = "workspace_subscriptions"
        const val KEYSTORE_ALIAS = "conductor_api_key"
        const val HOME_SNAPSHOT_FILE = "home_snapshot.json"
        const val MAX_SAVED_TRANSCRIPTS = 12
        const val MAX_SAVED_WORKSPACES = 20
    }
}

sealed interface ApiKeyState {
    data object Loading : ApiKeyState
    data object Missing : ApiKeyState
    data class Present(val apiKey: String) : ApiKeyState
}
