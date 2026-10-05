plugins {
    alias(libs.plugins.android.application) apply false
    // Declared only to raise the Kotlin version that AGP's built-in Kotlin support uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
