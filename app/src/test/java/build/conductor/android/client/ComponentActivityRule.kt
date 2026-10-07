package build.conductor.android.client

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Shadows.shadowOf

/** Registers the host activity of `createComposeRule`, so the debug APK does not need the ui-test-manifest entry. */
class ComponentActivityRule : TestWatcher() {
    override fun starting(description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
    }
}
