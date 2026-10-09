package com.teraper.printmaster

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.teraper.printmaster.core.data.maps.PlaceInbox
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives a place shared from Yandex Maps (or any map app), gives it to the client form
 * waiting for it and brings the app back. Shows nothing itself.
 */
@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {

    @Inject lateinit var placeInbox: PlaceInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .distinct()
            .joinToString("\n")
        if (!placeInbox.deliver(text)) {
            Toast.makeText(this, R.string.share_place_not_waiting, Toast.LENGTH_LONG).show()
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
