package build.conductor.android.client.data.github

/** A published build of the app. [buildNumber] is also the `versionCode` of its APK. */
data class AppRelease(val buildNumber: Int, val tag: String, val apkUrl: String, val apkSize: Long)

/** The repository whose releases carry the APK. */
val APP_REPOSITORY = RepositoryName("thomaschristopherking", "conductor-android")

/** The release workflow attaches the APK under this name, some minutes after the release is published. */
const val APK_ASSET_NAME = "conductor-android.apk"

/** The release with the highest build number, from the releases that are not drafts and that have the APK. */
fun newestAppRelease(releases: List<GitHubRelease>): AppRelease? = releases
    .filterNot { it.draft }
    .mapNotNull { it.toAppRelease() }
    .maxByOrNull { it.buildNumber }

private fun GitHubRelease.toAppRelease(): AppRelease? {
    val buildNumber = buildNumberOf(tagName) ?: return null
    val apk = assets.firstOrNull { it.name == APK_ASSET_NAME } ?: return null
    return AppRelease(buildNumber, tagName, apk.browserDownloadUrl, apk.size)
}

/** `v0.1.0-release.14` is build 14. */
fun buildNumberOf(tag: String): Int? = tag.substringAfterLast('.').toIntOrNull()
