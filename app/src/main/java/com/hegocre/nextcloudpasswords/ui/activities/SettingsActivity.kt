package com.hegocre.nextcloudpasswords.ui.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NCPSettingsScreen

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            NCPAppLockWrapper {
                NCPSettingsScreen(
                    onNavigationUp = { finish() }
                )
            }
        }
    }
}