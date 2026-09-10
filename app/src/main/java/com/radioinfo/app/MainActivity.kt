package com.radioinfo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.radioinfo.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var pendingNfcIntent: Intent? = null
    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Keep the toolbar as a plain view. The app uses a NoActionBar theme.
        binding.toolbar.title = getString(R.string.app_name)
        setupViewPager()
        checkAndRequestPermissions()
        routeNfcIntent(intent)
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = arrayOf(
                "SIM", "RAT", "WiFi", "Band", "Signal", "Traffic", "RF", "Net", "Bluetooth", "NFC"
            )[position]
        }.attach()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeNfcIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        dispatchPendingNfcIntent()
    }

    private fun routeNfcIntent(intent: Intent?) {
        if (intent?.action !in NFC_ACTIONS) return
        pendingNfcIntent = intent
        binding.viewPager.setCurrentItem(NFC_PAGE, false)
        binding.viewPager.post { dispatchPendingNfcIntent() }
    }

    private fun dispatchPendingNfcIntent() {
        val intent = pendingNfcIntent ?: return
        val fragment = supportFragmentManager.fragments
            .filterIsInstance<NfcFragment>()
            .firstOrNull { it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            ?: return
        pendingNfcIntent = null
        fragment.handleNfcIntent(intent)
    }

    private fun checkAndRequestPermissions() {
        val ungranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, getString(R.string.permissions_limited), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val NFC_PAGE = 9
        private val NFC_ACTIONS = setOf(
            "android.nfc.action.NDEF_DISCOVERED",
            "android.nfc.action.TECH_DISCOVERED",
            "android.nfc.action.TAG_DISCOVERED"
        )
    }
}
