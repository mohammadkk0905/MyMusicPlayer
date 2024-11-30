package com.mohammadkk.mymusicplayer.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore.Audio.AudioColumns
import android.util.AndroidRuntimeException
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.base.MusicServiceActivity
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.ActivityPlayerListBinding
import com.mohammadkk.mymusicplayer.dialogs.ChangeSortingDialog
import com.mohammadkk.mymusicplayer.extensions.errorToast
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.extensions.overridePendingTransitionCompat
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.extensions.toLocaleYear
import com.mohammadkk.mymusicplayer.image.GlideExtensions
import com.mohammadkk.mymusicplayer.image.GlideExtensions.getCoverOptions
import com.mohammadkk.mymusicplayer.image.palette.MusicColoredTarget
import com.mohammadkk.mymusicplayer.image.palette.PaletteColors
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.services.PlaybackStateManager
import com.mohammadkk.mymusicplayer.services.ScannerService
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import com.mohammadkk.mymusicplayer.viewmodels.SubViewModel
import kotlin.math.abs

class PlayerListActivity : MusicServiceActivity() {
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
                    AudioPlayerRemote.quit()
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
                onReloadLibrary(0)
                binding.swiperList.isRefreshing = false
            }, 200)
        }
        binding.mainActionbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPlayerListDetails.setOnClickListener { songsAdapter?.startFirstPlayer(false) }
        binding.btnShuffleListDetails.setOnClickListener { songsAdapter?.startFirstPlayer(true) }
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
            Constant.ALBUM_TAB -> {
                binding.mainActionbar.setTitle(R.string.album)
                initializeList(findId.first)
                initializeMenu(false)
                subViewModel.updateList(findId)
            }
            Constant.ARTIST_TAB -> {
                binding.mainActionbar.setTitle(R.string.artist)
                initializeList(findId.first)
                initializeMenu(false)
                subViewModel.updateList(findId)
            }
            Constant.GENRE_TAB -> {
                binding.mainActionbar.setTitle(R.string.genre)
                initializeList(findId.first)
                initializeMenu(false)
                subViewModel.updateList(findId)
            }
            "OTG" -> {
                binding.mainActionbar.setTitle(R.string.usb_device)
                initializeList(findId.first)
                initializeMenu(true)
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
            if (actionModeBackPressed != null) {
                actionModeBackPressed?.run()
                actionModeBackPressed = null
            } else {
                finish()
            }
        }
    }
    private fun getPairActivity(): Pair<String, Long>? {
        if (mPairActivity == null) {
            if (intent.hasExtra(Constant.LIST_CHILD)) {
                mPairActivity = Constant.jsonToPairState(intent.getStringExtra(Constant.LIST_CHILD))
            } else if (intent.getBooleanExtra("otg", false)) {
                mPairActivity = Pair("OTG", -1L)
            }
        }
        return mPairActivity
    }
    private fun initializeList(mode: String) {
        songsAdapter = SongsAdapter(this, mode)
        val spanCount = if (isLandscape) {
            resources.getInteger(R.integer.def_list_columns_land)
        } else {
            resources.getInteger(R.integer.def_list_columns)
        }
        binding.tracksRv.setHasFixedSize(true)
        binding.tracksRv.layoutManager = GridLayoutManager(this, spanCount)
        binding.tracksRv.adapter = songsAdapter
    }
    private fun initializeMenu(isOtg: Boolean) {
        if (!isOtg) {
            binding.mainActionbar.menu?.add(0, R.id.action_order_by, 0, getString(R.string.action_sort_order))?.run {
                setIcon(R.drawable.ic_sort)
                setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                setOnMenuItemClickListener {
                    ChangeSortingDialog.showDialog(supportFragmentManager, 0)
                    settings.actionModeIndex = 0
                    true
                }
            }
        } else {
            binding.mainActionbar.menu?.add(0, R.id.action_recheck_library, 0, getString(R.string.action_sort_order))?.run {
                setIcon(R.drawable.ic_scan)
                setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                setOnMenuItemClickListener {
                    if (hasNotificationApi()) {
                        val serviceIntent = Intent(this@PlayerListActivity, ScannerService::class.java)
                        serviceIntent.putExtra(AudioColumns.DATA, settings.otgPartition)
                        try {
                            startService(serviceIntent)
                        } catch (e: Exception) {
                            errorToast(e)
                        }
                    }
                    true
                }
            }
        }
    }
    private fun initRefreshing(isDisabled: Boolean) {
        binding.progressListSwiper.isVisible = isDisabled
        binding.progressListSwiper.isIndeterminate = isDisabled
        binding.progressListSwiper.isEnabled = !isDisabled
    }
    private fun listLoader(songs: List<Song>) {
        if (!isIgnoredItems) songsAdapter?.swapDataSet(songs)
        val currentSong = subViewModel.getCurrentSong()
        loadCover(currentSong)
        binding.detailsListTracks.text = resources.getQuantityString(
            R.plurals.songs_plural, songs.size, songs.size
        )
        binding.detailsAlbumArtist.text = getString(
            R.string.album_artist_symbol, currentSong.album, currentSong.artist
        )
        binding.detailsAlbumYear.text = currentSong.year.toLocaleYear()
        binding.detailsListDuration.text = subViewModel.getDuration().toFormattedDuration(false)
    }
    private fun loadCover(song: Song) {
        val drawable = GlideExtensions.getCoverArt(this, song.id, when (getPairActivity()?.first) {
            Constant.ALBUM_TAB -> R.drawable.ic_album
            Constant.ARTIST_TAB  -> R.drawable.ic_artist
            Constant.GENRE_TAB  -> R.drawable.ic_genre
            else -> R.drawable.ic_audiotrack
        })
        if (settings.coverMode != Constant.COVER_OFF) {
            Glide.with(this)
                .asBitmap()
                .getCoverOptions(song, drawable)
                .load(GlideExtensions.getSongModel(song))
                .into(object : MusicColoredTarget(binding.detailsAlbumArt) {
                    override fun onResolveColor(colors: PaletteColors) {
                        if (!colors.isFallback) {
                            applyColor(colors)
                            applyOutlineColor(colors.backgroundColor)
                        }
                    }
                })
        } else {
            binding.detailsAlbumArt.setImageDrawable(drawable)
        }
    }
    private fun applyColor(colors: PaletteColors) = with(binding.btnPlayerListDetails) {
        val backgroundColorStateList = ColorStateList.valueOf(colors.backgroundColor)
        val textColorColorStateList = ColorStateList.valueOf(colors.primaryTextColor)
        setTextColor(textColorColorStateList)
        backgroundTintList = backgroundColorStateList
        iconTint = textColorColorStateList
    }
    private fun applyOutlineColor(@ColorInt color: Int) = with(binding.btnShuffleListDetails) {
        val colorStateList = ColorStateList.valueOf(getColorAlpha(color))
        iconTint = colorStateList
        strokeColor = colorStateList
        setTextColor(colorStateList)
        rippleColor = ColorStateList.valueOf(ThemeManager.withAlpha(color, 0.35f))
    }
    private fun getColorAlpha(@ColorInt color: Int): Int {
        if (ThemeManager.isNightTheme(resources)) {
            return ColorUtils.blendARGB(
                color, Color.parseColor("#FCFCFC"), 0.52f
            )
        }
        return color
    }
    override fun onReloadLibrary(tabIndex: Int?) {
        getPairActivity()?.let { pair ->
            if (tabIndex == 0) {
                subViewModel.updateList(pair)
            } else {
                isIgnoredItems = true
                songsAdapter?.swapDeleted()
                subViewModel.updateList(pair)
            }
            PlaybackStateManager.getInstance().onReloadLibraries()
        }
    }
    override fun onShowOpenMiniPlayer(isShow: Boolean) {
        if (isShow) {
            if (binding.nowPlayerFrag.visibility != View.VISIBLE) {
                val actionBar = binding.mainActionbar.layoutParams.height
                binding.mainRelative.updatePadding(
                    left = 0, top = 0, right = 0, bottom = actionBar
                )
                binding.nowPlayerFrag.visibility = View.VISIBLE
            }
        } else {
            if (binding.nowPlayerFrag.visibility != View.GONE) {
                binding.mainRelative.updatePadding(
                    left = 0, top = 0, right = 0, bottom = 0
                )
                binding.nowPlayerFrag.visibility = View.GONE
            }
        }
    }
    override fun onPause() {
        super.onPause()
        overridePendingTransitionCompat(
            true, android.R.anim.fade_in, android.R.anim.fade_out
        )
    }
    override fun onDestroy() {
        unregisterUSBReceiver()
        super.onDestroy()
    }
}