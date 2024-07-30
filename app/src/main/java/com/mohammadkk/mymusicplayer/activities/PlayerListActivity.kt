package com.mohammadkk.mymusicplayer.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AndroidRuntimeException
import android.util.Log
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.ActivityPlayerListBinding
import com.mohammadkk.mymusicplayer.dialogs.ChangeSortingDialog
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.extensions.overridePendingTransitionCompat
import com.mohammadkk.mymusicplayer.extensions.sendIntent
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.extensions.toLocaleYear
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.MusicService
import com.mohammadkk.mymusicplayer.services.PlaybackStateManager
import com.mohammadkk.mymusicplayer.viewmodels.SubViewModel
import kotlin.math.abs
import kotlin.random.Random

class PlayerListActivity : BaseActivity() {
    private lateinit var binding: ActivityPlayerListBinding
    private val subViewModel: SubViewModel by viewModels()
    private val mHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var mPairActivity: Pair<String, Long>? = null
    private var isIgnoredItems = false
    private var songsAdapter: SongsAdapter? = null
    private val receiverUsb = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d("PlayerListActivity", "Usb otg connected")
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (MusicService.isMusicPlayer()) sendIntent(Constant.FINISH)
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(mBackPressedCallback)
        binding = ActivityPlayerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getInputMethod()
        registerUSBReceiver()
        subViewModel.getListData().observe(this) { items ->
            listLoader(items)
            initRefreshing(false)
            isIgnoredItems = false
        }
        binding.swiperList.setOnRefreshListener {
            initRefreshing(true)
            mHandler.postDelayed({
                onReloadLibrary(Constant.SONG_ID)
                binding.swiperList.isRefreshing = false
            }, 200)
        }
        binding.mainActionbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPlayerListDetails.setOnClickListener {
            val itemsCount = songsAdapter?.itemCount ?: 0
            if (itemsCount > 0) {
                settings.isShuffleEnabled = false
                songsAdapter!!.startPlayer(0)
            }
        }
        binding.btnShuffleListDetails.setOnClickListener {
            val itemsCount = songsAdapter?.itemCount ?: 0
            if (itemsCount > 0) {
                settings.isShuffleEnabled = true
                songsAdapter!!.startPlayer(Random.nextInt(itemsCount))
            } else {
                settings.isShuffleEnabled = false
            }
        }
    }
    private fun getInputMethod() {
        val metrics = resources.displayMetrics
        val size = (metrics.density * 196).toInt()
        val width = metrics.widthPixels
        if ((size * 100 / width) >= 55) {
            binding.detailsAlbumArt.doOnPreDraw {
                val lp = binding.detailsAlbumArt.layoutParams as? ConstraintLayout.LayoutParams
                if (lp != null) {
                    val dim = it.width / 2
                    lp.width = dim
                    lp.height = dim
                    binding.detailsAlbumArt.layoutParams = lp
                }
            }
        }
        binding.mainAppbar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            binding.mainDivider.alpha = abs((verticalOffset / appBarLayout.totalScrollRange) / 2f)
            binding.swiperList.isEnabled = verticalOffset == 0
        }
        binding.mainAppbar.setOnFocusChangeListener { _, hasFocus ->
            binding.mainAppbar.setExpanded(hasFocus, true)
        }
        val findId = getPairActivity()
        when (findId?.first) {
            "ALBUM" -> {
                binding.mainActionbar.setTitle(R.string.album)
                initializeList(findId.first)
                initializeMenu()
                subViewModel.updateList(findId)
            }
            "ARTIST" -> {
                binding.mainActionbar.setTitle(R.string.artist)
                initializeList(findId.first)
                initializeMenu()
                subViewModel.updateList(findId)
            }
            "OTG" -> {
                binding.mainActionbar.setTitle(R.string.usb_device)
                initializeList(findId.first)
                subViewModel.updateList(findId)
            }
        }
    }
    private fun registerUSBReceiver() {
        if (intent.getBooleanExtra("otg", false)) {
            val intentFilter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            try {
                applicationContext.registerReceiver(receiverUsb, intentFilter)
            } catch (e: AndroidRuntimeException) {
                LocalBroadcastManager.getInstance(applicationContext).registerReceiver(receiverUsb, intentFilter)
            }
        }
    }
    private fun unregisterUSBReceiver() {
        if (intent.getBooleanExtra("otg", false)) {
            try {
                applicationContext.unregisterReceiver(receiverUsb)
            } catch (e: AndroidRuntimeException) {
                LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(receiverUsb)
            }
        }
    }
    private val mBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (adapterActionMode != null) {
                adapterActionMode?.finish()
                adapterActionMode = null
            } else {
                finish()
            }
        }
    }
    private fun getPairActivity(): Pair<String, Long>? {
        if (mPairActivity == null) {
            val holder = ArrayList<Pair<String, Long>?>()
            if (intent.hasExtra(Constant.ALBUM_ID)) {
                val albumId = intent.getLongExtra(Constant.ALBUM_ID, -1L)
                if (albumId != -1L) holder.add("ALBUM" to albumId)
            } else if (intent.hasExtra(Constant.ARTIST_ID)) {
                val artistId = intent.getLongExtra(Constant.ARTIST_ID, -1L)
                if (artistId != -1L) holder.add("ARTIST" to artistId)
            } else if (intent.getBooleanExtra("otg", false)) {
                holder.add("OTG" to -1L)
            }
            mPairActivity = holder.firstOrNull()
            if (mPairActivity == null) {
                toast(R.string.unknown_error_occurred)
                finish()
                return null
            }
        }
        return mPairActivity
    }
    private fun initializeList(mode: String) {
        val dataSet = songsAdapter?.dataSet ?: mutableListOf()
        songsAdapter = SongsAdapter(this, dataSet, mode)
        val spanCount = if (isLandscape) {
            resources.getInteger(R.integer.def_list_columns_land)
        } else {
            resources.getInteger(R.integer.def_list_columns)
        }
        binding.tracksRv.setHasFixedSize(true)
        binding.tracksRv.layoutManager = GridLayoutManager(this, spanCount)
        binding.tracksRv.adapter = songsAdapter
    }
    private fun initializeMenu() {
        binding.mainActionbar.menu?.add(0, R.id.action_order_by, 0, getString(R.string.order_by))?.run {
            setIcon(R.drawable.ic_sort)
            setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                ChangeSortingDialog.showDialog(supportFragmentManager, Constant.SONG_ID)
                settings.actionModeIndex = 0
                true
            }
        }
    }
    private fun initRefreshing(isDisabled: Boolean) {
        binding.progressListSwiper.isVisible = isDisabled
        binding.progressListSwiper.isIndeterminate = isDisabled
        binding.progressListSwiper.isEnabled = !isDisabled
    }
    private fun listLoader(songs: List<Song>) {
        val errorRes = when (getPairActivity()?.first) {
            "ALBUM" -> R.drawable.ic_album
            "ARTIST" -> R.drawable.ic_artist
            else -> R.drawable.ic_audiotrack
        }
        if (!isIgnoredItems) songsAdapter?.swapDataSet(songs)
        val currentSong = subViewModel.getCurrentSong()
        binding.detailsAlbumArt.bind(currentSong, errorRes)
        binding.detailsListTracks.text = resources.getQuantityString(
            R.plurals.songs_plural, songs.size, songs.size
        )
        binding.detailsAlbumArtist.text = getString(
            R.string.album_artist_symbol, currentSong.album, currentSong.artist
        )
        binding.detailsAlbumYear.text = currentSong.year.toLocaleYear()
        binding.detailsListDuration.text = subViewModel.getDuration().toFormattedDuration(false)
    }
    private fun getActionBarHeight(): Int {
        val mHeight = if (theme != null) {
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        } else {
            resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_action_bar_default_height_material)
        }
        return mHeight + resources.getDimensionPixelSize(R.dimen.spacing_small)
    }
    override fun onReloadLibrary(mode: String?) {
        getPairActivity()?.let { pair ->
            if (mode == Constant.SONG_ID) {
                subViewModel.updateList(pair)
            } else {
                isIgnoredItems = true
                songsAdapter?.swapDeleted()
                subViewModel.updateList(pair)
            }
            PlaybackStateManager.getInstance().onReloadLibraries()
        }
        if (MusicService.isMusicPlayer()) sendIntent(Constant.REFRESH_LIST)
    }
    override fun onResume() {
        super.onResume()
        if (MusicService.isMusicPlayer()) {
            val visibility = binding.navFragPlayer.visibility
            if (visibility != View.VISIBLE) {
                binding.mainRelative.updatePadding(
                    left = 0, top = 0, right = 0,
                    bottom = getActionBarHeight()
                )
                binding.navFragPlayer.visibility = View.VISIBLE
            }
        } else {
            val visibility = binding.navFragPlayer.visibility
            if (visibility != View.GONE) {
                binding.mainRelative.updatePadding(
                    left = 0, top = 0, right = 0, bottom = 0
                )
                binding.navFragPlayer.visibility = View.GONE
            }
        }
    }
    override fun onPause() {
        super.onPause()
        if (isFadeAnimation)
            overridePendingTransitionCompat(
                true, android.R.anim.fade_in, android.R.anim.fade_out
            )
    }
    override fun onDestroy() {
        unregisterUSBReceiver()
        super.onDestroy()
    }
}