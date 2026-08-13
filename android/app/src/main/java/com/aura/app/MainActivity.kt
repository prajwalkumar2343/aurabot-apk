package com.aura.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, LauncherActivity::class.java)
                .putExtra(LauncherActivity.EXTRA_REQUESTED_SURFACE, LauncherActivity.SURFACE_SETTINGS)
        )
        finish()
    }
}
