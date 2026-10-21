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
import build.conductor.android.client.data.api.createConductorApi
import build.conductor.android.client.data.settings.AesGcmCipher
import build.conductor.android.client.data.settings.ApiKeyStore
import build.conductor.android.client.data.settings.AppearanceStore
import build.conductor.android.client.data.settings.ColorSchemeChoice
import build.conductor.android.client.data.settings.StarredSessionStore
import build.conductor.android.client.data.settings.androidKeystoreKey
import build.conductor.android.client.data.transcript.TranscriptSnapshot
import build.conductor.android.client.ui.workspace.WorkspaceDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val apiKeyStore = ApiKeyStore(settingsDataStore, AesGcmCipher { keystoreKey })

    val apiKeyState: StateFlow<ApiKeyState> = apiKeyStore.apiKey
        .map { key -> if (key.isNullOrBlank()) ApiKeyState.Missing else ApiKeyState.Present(key) }
        .stateIn(applicationScope, SharingStarted.Eagerly, ApiKeyState.Loading)

    val appearanceStore = AppearanceStore(settingsDataStore)

    /** Null until DataStore reads the saved choice, so the first frame does not show the wrong colours. */
    val colorScheme: StateFlow<ColorSchemeChoice?> = appearanceStore.colorScheme
        .stateIn(applicationScope, SharingStarted.Eagerly, null)

    val starredSessionStore = StarredSessionStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(STARRED_SESSIONS_FILE) },
    )

    val repository: ConductorRepository = ApiConductorRepository(
        createConductorApi(apiKey = { (apiKeyState.value as? ApiKeyState.Present)?.apiKey }),
    )

    val questionScanner = QuestionScanner(repository)

    val openQuestionTracker = OpenQuestionTracker(questionScanner)

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
        const val STARRED_SESSIONS_FILE = "starred_sessions"
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
