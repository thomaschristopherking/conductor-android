package build.conductor.android.client.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Copies a Conductor desktop deep link. Android cannot open `conductor://` links itself. */
fun copyDeepLink(context: Context, deepLink: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Conductor link", deepLink))
    Toast.makeText(context, "Link copied. Open it on a computer with Conductor.", Toast.LENGTH_SHORT).show()
}

fun shareDeepLink(context: Context, title: String, deepLink: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, deepLink)
    }
    context.startActivity(Intent.createChooser(send, "Open in Conductor"))
}
