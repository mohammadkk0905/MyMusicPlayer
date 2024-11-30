package com.mohammadkk.mymusicplayer.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.util.Util.isOnMainThread
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.base.MusicServiceActivity
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.ActivityMainBinding
import com.mohammadkk.mymusicplayer.dialogs.ChangeSortingDialog
import com.mohammadkk.mymusicplayer.dialogs.ScanMediaFoldersDialog
import com.mohammadkk.mymusicplayer.extensions.getDefaultNightMode
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.extensions.isMassUsbDeviceConnected
import com.mohammadkk.mymusicplayer.extensions.reduceDragSensitivity
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.fragments.AlbumsFragment
import com.mohammadkk.mymusicplayer.fragments.ArtistsFragment
import com.mohammadkk.mymusicplayer.fragments.GenresFragment
import com.mohammadkk.mymusicplayer.fragments.SongsFragment
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class MainActivity : MusicServiceActivity() {
    private lateinit var binding: ActivityMainBinding
    private val musicViewModel: MusicViewModel by viewModels()
    private var permissionMode = '0'

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, true) {
            onBackPressedCompat()
        }
        if (savedInstanceState != null) {
            permissionMode = savedInstanceState.getChar("permission_mode")
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.mainActionBar)
        setupElementUi()
        val permission = Constant.STORAGE_PERMISSION
        if (hasPermission(permission)) {
            setupRequires()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), Constant.PERMISSION_REQUEST_STORAGE)
        }
        binding.mainActionBar.setNavigationOnClickListener {
            if (AudioPlayerRemote.isPlaying) {
                MaterialAlertDialogBuilder(this)
                    .setCancelable(false)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.on_close_activity)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        AudioPlayerRemote.quit()
                        finishAndRemoveTask()
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        finishAndRemoveTask()
                    }
                    .setNeutralButton(android.R.string.cancel, null)
                    .show()
            } else {
                AudioPlayerRemote.quit()
                finishAndRemoveTask()
            }
        }
    }
    private fun onBackPressedCompat() {
        if (actionModeBackPressed != null) {
            actionModeBackPressed?.run()
            actionModeBackPressed = null
        } else {
            if (binding.mainPager.currentItem >= 1) {
                binding.mainPager.currentItem = 0
            } else {
                finish()
            }
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (Constant.isMarshmallowPlus()) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == Constant.PERMISSION_REQUEST_STORAGE) {
                if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                    storagePermissionManager()
                } else {
                    setupRequires()
                }
            }
        }
    }
    private fun storagePermissionManager() {
        val permission = Constant.STORAGE_PERMISSION
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            Snackbar.make(binding.root, R.string.permission_storage_denied, Snackbar.LENGTH_SHORT).setAction(R.string.grant) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), Constant.PERMISSION_REQUEST_STORAGE)
            }.show()
        } else {
            Snackbar.make(binding.root, R.string.permission_storage_denied, Snackbar.LENGTH_SHORT).setAction(R.string.settings) {
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.fromParts("package", applicationContext.packageName, null)
                startActivity(intent)
                permissionMode = '1'
            }.show()
        }
    }
    private fun setupElementUi() {
        val adapter = SlidePagerAdapter(supportFragmentManager, lifecycle)
        binding.mainPager.offscreenPageLimit = adapter.itemCount.minus(1)
        binding.mainPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.mainPager.adapter = adapter
        binding.mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var oldPosition = -1

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (musicViewModel.searchHandle.value.first != -1) {
                    musicViewModel.setSearch(true, null)
                    invalidateOptionsMenu()
                }
                if (position == 0) {
                    binding.fabShuffle.show()
                    binding.fabShuffle.tag = "1"
                } else {
                    binding.fabShuffle.hide()
                    binding.fabShuffle.tag = "0"
                }
                if (actionModeBackPressed != null) oldPosition = position
            }
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (actionModeBackPressed != null && oldPosition != -1) {
                        actionModeBackPressed?.run()
                        actionModeBackPressed = null
                        oldPosition = -1
                    }
                }
            }
        })
        binding.mainPager.reduceDragSensitivity()
        TabLayoutMediator(binding.bottomTabs, binding.mainPager) { tab, pos ->
            val mInfoTab = when (pos) {
                0 -> intArrayOf(R.drawable.ic_audiotrack, R.string.songs)
                1 -> intArrayOf(R.drawable.ic_library_music, R.string.albums)
                2 -> intArrayOf(R.drawable.ic_person, R.string.artists)
                else -> intArrayOf(R.drawable.ic_genre, R.string.genres)
            }
            tab.setCustomView(R.layout.bottom_tab_item).apply {
                customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageResource(mInfoTab[0])
                customView?.findViewById<TextView>(R.id.tab_item_label)?.setText(mInfoTab[1])
            }
        }.attach()
        binding.fabShuffle.setOnClickListener {
            if (!hasPermission(Constant.STORAGE_PERMISSION)) {
                storagePermissionManager()
                return@setOnClickListener
            }
            if (!hasNotificationApi()) return@setOnClickListener
            val dataSet = musicViewModel.songsList.value
            if (dataSet.isEmpty()) return@setOnClickListener
            lifecycleScope.launch(Dispatchers.IO) {
                AudioPlayerRemote.openAndShuffleQueue(dataSet, true)
                SongsAdapter.launchPlayer(this@MainActivity)
            }
        }
    }
    override fun onShowOpenMiniPlayer(isShow: Boolean) {
        if (isShow) {
            if (binding.nowPlayerFrag.visibility != View.VISIBLE) {
                binding.nowPlayerFrag.visibility = View.VISIBLE
            }
        } else {
            if (binding.nowPlayerFrag.visibility != View.GONE) {
                binding.nowPlayerFrag.visibility = View.GONE
            }
        }
    }
    private fun setupRequires() {
        musicViewModel.updateLibraries()
        BaseSettings.initialize(application)
        hasNotificationApi()
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main_libraries, menu)
        menu?.run {
            val searchView = findItem(R.id.action_search).actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    musicViewModel.setSearch(binding.mainPager.currentItem, false, query)
                    return false
                }
                override fun onQueryTextChange(newText: String?): Boolean {
                    musicViewModel.setSearch(binding.mainPager.currentItem, false, newText)
                    return false
                }
            })
            searchView.setOnSearchClickListener {
                binding.mainPager.isUserInputEnabled = false
            }
            searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
                findItem(R.id.action_order_by).isVisible = !hasFocus
                setGroupVisible(R.id.order_group, !hasFocus)
                binding.mainPager.isUserInputEnabled = !hasFocus
            }
        }
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_order_by -> {
                ChangeSortingDialog.showDialog(
                    supportFragmentManager,
                    binding.mainPager.currentItem
                )
            }
            R.id.action_recheck_library -> ScanMediaFoldersDialog.create(
                false, supportFragmentManager
            )
            R.id.action_usb_otg -> if (isMassUsbDeviceConnected()) {
                ScanMediaFoldersDialog.create(true, supportFragmentManager)
            } else {
                toast(R.string.usb_device_not_found)
            }
            R.id.action_ui_mode -> {
                val items = arrayOf(
                    getString(R.string.theme_light_title),
                    getString(R.string.theme_dark_title),
                    getString(R.string.theme_auto_title)
                )
                val index = min(max(settings.themeUI, 0), 2)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.theme_title)
                    .setSingleChoiceItems(items, index) { dialog, which ->
                        if (settings.themeUI != which) {
                            settings.themeUI = which
                            ThemeManager.clear()
                            AppCompatDelegate.setDefaultNightMode(
                                which.getDefaultNightMode()
                            )
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.action_album_covers -> {
                val items = arrayOf(
                    getString(R.string.set_cover_mode_off),
                    getString(R.string.set_cover_mode_media_store),
                    getString(R.string.set_cover_mode_quality)
                )
                val index = min(max(settings.coverMode, 0), 2)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.set_cover_mode)
                    .setSingleChoiceItems(items, index) { dialog, which ->
                        if (settings.coverMode != which) {
                            settings.coverMode = which
                            if (isOnMainThread()) {
                                recreate()
                            } else {
                                runOnUiThread { recreate() }
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return super.onOptionsItemSelected(item)
    }
    fun setVisibilityFloating(isVisible: Boolean) {
        if (binding.mainPager.currentItem == 0) {
            if (isVisible) {
                binding.fabShuffle.show()
            } else {
                binding.fabShuffle.hide()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if (settings.actionModeIndex == 0) {
            musicViewModel.forceReload(0)
            settings.actionModeIndex = -1
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putChar("permission_mode", permissionMode)
    }
    override fun onResume() {
        super.onResume()
        if (permissionMode == '1') {
            if (hasPermission(Constant.STORAGE_PERMISSION)) {
                setupRequires()
            }
            permissionMode = '0'
        }
    }
    override fun onReloadLibrary(tabIndex: Int?) {
        if (tabIndex == null) {
            musicViewModel.updateLibraries()
        } else {
            musicViewModel.forceReload(tabIndex)
        }
    }
    private class SlidePagerAdapter(fm: FragmentManager, le: Lifecycle) : FragmentStateAdapter(fm, le) {
        override fun getItemCount(): Int {
            return 4
        }
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SongsFragment()
                1 -> AlbumsFragment()
                2 -> ArtistsFragment()
                else -> GenresFragment()
            }
        }
        override fun getItemId(position: Int): Long {
            return position.toLong()
        }
    }
}