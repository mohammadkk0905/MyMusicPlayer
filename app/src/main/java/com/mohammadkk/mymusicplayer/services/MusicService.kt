package com.mohammadkk.mymusicplayer.services

import android.app.Application
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import android.view.KeyEvent
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.media.session.MediaButtonReceiver
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.database.SongsDatabase
import com.mohammadkk.mymusicplayer.extensions.getAlbumArt
import com.mohammadkk.mymusicplayer.extensions.getTrackArt
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.extensions.queueDAO
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.extensions.toImmutableFlag
import com.mohammadkk.mymusicplayer.models.QueueItem
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.utils.Libraries
import com.mohammadkk.mymusicplayer.utils.MusicPlayer
import com.mohammadkk.mymusicplayer.utils.NotificationUtils
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MusicService : Service(), MusicPlayer.PlaybackListener {
    private val playbackStateManager = PlaybackStateManager.getInstance()
    private val settings = BaseSettings.getInstance()
    private var isServiceInit = false
    private var mCurrSongCover: Bitmap? = null
    private var mPlaceholder: Bitmap? = null
    private var mExecutor: ScheduledExecutorService? = null
    private var mSeekBarPositionUpdateTask: Runnable? = null
    private var playOnPrepare = true
    private var mMediaSession: MediaSessionCompat? = null
    private var retriedSongCount = 0
    private var setProgressOnPrepare = 0
    private var mClicksCount = 0
    private val mButtonControlHandler = Handler(Looper.getMainLooper())
    private val mRunnable = Runnable {
        if (mClicksCount == 0) return@Runnable
        when (mClicksCount) {
            1 -> onHandlePlayPause()
            2 -> onHandleNext()
            else -> onHandlePrevious()
        }
        mClicksCount = 0
    }
    private val notificationHandler = Handler(Looper.getMainLooper())
    private var notificationUtils: NotificationUtils? = null
    private val mMediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            return onHandleMediaButton(mediaButtonEvent)
        }
        override fun onPlay() {
            isGlobalPlayAnim = true
            onResumeSong()
        }
        override fun onPause() {
            isGlobalPlayAnim = true
            onPauseSong()
        }
        override fun onStop() {
            if (isServiceInit)
                onPauseSong()
        }
        override fun onSkipToNext() {
            onHandleNext()
        }
        override fun onSkipToPrevious() {
            onHandlePrevious()
        }
        override fun onSeekTo(pos: Long) {
            updateProgress(pos.toInt())
        }
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == Constant.DISMISS)
                handleFinish(true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initMediaPlayerIfNeeded()
        onCreateMediaSession()
        notificationUtils = NotificationUtils.createInstance(this, mMediaSession!!)
        if (!Constant.isQPlus() && !hasPermission(Constant.STORAGE_PERMISSION)) {
            playbackStateManager.noStoragePermission()
        }
        startForegroundOrNotify()
    }
    private fun onCreateMediaSession() {
        val mbIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val mbrComponentName = ComponentName(applicationContext, MediaButtonSessionReceiver::class.java)
        val mbrPendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, mbIntent, (0).toImmutableFlag()
        )
        mMediaSession = MediaSessionCompat(this, applicationContext.packageName, mbrComponentName, mbrPendingIntent).apply {
            isActive = true
            setCallback(mMediaSessionCallback)
            setMediaButtonReceiver(mbrPendingIntent)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
        mMediaSession?.run {
            if (isActive) {
                isActive = false
                setCallback(null)
                setMediaButtonReceiver(null)
                release()
            }
        }
        mMediaSession = null
        SongsDatabase.destroyInstance()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Constant.isQPlus() && !hasPermission(Constant.STORAGE_PERMISSION)) {
            return START_NOT_STICKY
        }

        isServiceInit = true

        val action = intent?.action
        when (action) {
            Constant.INIT -> onHandleInit(intent)
            Constant.PREVIOUS -> onHandlePrevious()
            Constant.PAUSE -> onPauseSong()
            Constant.PLAY_PAUSE -> onHandlePlayPause()
            Constant.NEXT -> onHandleNext()
            Constant.FINISH -> handleFinish(false)
            Constant.DISMISS -> handleFinish(true)
            Constant.SET_PROGRESS -> onHandleSetProgress(intent)
            Constant.SKIP_BACKWARD -> onSkip(false)
            Constant.SKIP_FORWARD -> onSkip(true)
            Constant.UPDATE_QUEUE_SIZE -> updateMediaSession()
            Constant.REFRESH_LIST -> onHandleRefreshList()
            Constant.BROADCAST_STATUS -> {
                broadcastSongStateChange(isPlaying())
                broadcastSongChange()
                broadcastSongProgress(mPlayer?.position() ?: 0)
            }
        }
        MediaButtonReceiver.handleIntent(mMediaSession!!, intent)
        if (action != Constant.DISMISS && action != Constant.FINISH) {
            startForegroundOrNotify()
        }
        return START_NOT_STICKY
    }
    private fun initializeService(intent: Intent?) {
        mSongs = getQueuedSongs()
        var wantedId = intent?.getLongExtra(Constant.SONG_ID, -1L) ?: -1L
        if (wantedId == -1L) {
            val currentQueueItem = getSkipQueue(true)
            wantedId = currentQueueItem.songId
            setProgressOnPrepare = currentQueueItem.lastPosition
        }
        for (i in 0 until mSongs.size) {
            val track = mSongs[i]
            if (track.id == wantedId) {
                mCurrSong = track
                break
            }
        }
        initializeMetadata()
        checkSongShuffle()
        initMediaPlayerIfNeeded()
        startForegroundOrNotify()
        isServiceInit = true
    }
    private fun initializeMetadata() {
        if (mCurrSong?.isOTGMode() == true) {
            val mrr = MediaMetadataRetriever()
            try {
                mrr.setDataSource(this, mCurrSong!!.path.toUri())
                val album = mrr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: MediaStore.UNKNOWN_STRING
                val artist = mrr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: MediaStore.UNKNOWN_STRING
                val duration = mrr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
                mrr.release()
                mCurrSong = mCurrSong!!.copy(album = album, artist = artist, duration = duration)
            } catch (ignored: Exception) {
            }
        }
    }
    private fun onHandleInit(intent: Intent? = null) {
        Constant.ensureBackgroundThread {
            initializeService(intent)
            val wantedId = mCurrSong?.id ?: -1L
            playOnPrepare = true
            setSong(wantedId, false)
        }
    }
    private fun onHandlePrevious() {
        playOnPrepare = true
        Constant.ensureBackgroundThread {
            val queueItem = getSkipQueue(false)
            setProgressOnPrepare = queueItem.lastPosition
            setSong(queueItem.songId, true)
        }
    }
    private fun onHandlePlayPause() {
        playOnPrepare = true
        if (isPlaying())
            onPauseSong()
        else
            onResumeSong()
    }
    private fun onHandleNext() {
        playOnPrepare = true
        setupNextSong()
    }
    private fun onResumeSong() {
        if (mSongs.isEmpty()) {
            onHandleEmptyList()
            return
        }
        initMediaPlayerIfNeeded()
        if (mCurrSong == null) {
            setupNextSong()
        } else {
            mPlayer!!.start()
        }
        songStateChanged(true)
    }
    private fun onPauseSong(notify: Boolean = true) {
        initMediaPlayerIfNeeded()
        mPlayer!!.pause()
        songStateChanged(playing = false, notify = notify)
        updateMediaSessionState()
        saveSongProgress()
        if (!Constant.isSPlus()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        }
    }
    private fun handleFinish(isDismiss: Boolean) {
        if (isDismiss) {
            if (isPlaying()) onPauseSong(false)
            stopForegroundOrNotification()
        } else {
            broadcastSongProgress(0)
            stopForegroundOrNotification()
            stopSelf()
        }
    }
    private fun getSkipQueue(isNext: Boolean): QueueItem {
        return when (mSongs.size) {
            0 -> QueueItem(-1L)
            1 -> QueueItem(mSongs.first().id)
            else -> if (isNext) {
                val index = mSongs.indexOf(mCurrSong)
                if (index != -1) {
                    QueueItem(mSongs[(index + 1) % mSongs.size].id)
                } else {
                    queueDAO.getCurrent() ?: QueueItem(mSongs.first().id)
                }
            } else {
                val index = mSongs.indexOf(mCurrSong)
                if (index != -1) {
                    QueueItem(mSongs[(if (index > 0) index else mSongs.size).minus(1)].id)
                } else {
                    queueDAO.getCurrent() ?: QueueItem(mSongs.last().id)
                }
            }
        }
    }
    private fun setupNextSong() {
        Constant.ensureBackgroundThread {
            val queueItem = getSkipQueue(true)
            setProgressOnPrepare = queueItem.lastPosition
            setSong(queueItem.songId, true)
        }
    }
    private fun onHandleSetProgress(intent: Intent) {
        if (mPlayer != null) {
            val progress = intent.getIntExtra(Constant.PROGRESS, mPlayer!!.position())
            updateProgress(progress)
        }
    }
    private fun startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            if (mSeekBarPositionUpdateTask == null) {
                mSeekBarPositionUpdateTask = Runnable { updateProgressCallbackTask() }
            }
            mExecutor = Executors.newSingleThreadScheduledExecutor()
            mExecutor?.scheduleWithFixedDelay(
                mSeekBarPositionUpdateTask, 0, 1000, TimeUnit.MILLISECONDS
            )
        }
    }
    private fun stopUpdatingCallbackWithPosition() {
        mExecutor?.shutdownNow()
        mExecutor = null
        mSeekBarPositionUpdateTask = null
    }
    private fun updateProgressCallbackTask() {
        if (isPlaying()) {
            val mTime = mPlayer!!.position()
            broadcastSongProgress(mTime)
        }
    }
    private fun onHandleProgressHandler(playing: Boolean) {
        if (playing) {
            startUpdatingCallbackWithPosition()
        } else {
            stopUpdatingCallbackWithPosition()
        }
    }
    fun onHandleMediaButton(mediaButtonEvent: Intent?): Boolean {
        if (mediaButtonEvent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val swapBtn = settings.swapPrevNext
            val event: KeyEvent = IntentCompat.getParcelableExtra(
                mediaButtonEvent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java
            ) ?: return false
            if (event.action == KeyEvent.ACTION_UP) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        onResumeSong()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        onPauseSong()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        onPauseSong()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        onHandlePlayPause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (swapBtn) onHandleNext() else onHandlePrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (swapBtn) onHandlePrevious() else onHandleNext()
                        return true
                    }
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        mClicksCount++
                        mButtonControlHandler.removeCallbacks(mRunnable)
                        if (mClicksCount >= 3) {
                            mButtonControlHandler.post(mRunnable)
                        } else {
                            mButtonControlHandler.postDelayed(mRunnable, 700)
                        }
                        return true
                    }
                }
            }
        }
        return false
    }
    private fun onHandleEmptyList() {
        mPlayer?.pause()
        songChanged()
        songStateChanged(false)
        if (!isServiceInit) onHandleInit()
    }
    private fun onSkip(forward: Boolean) {
        val curr = mPlayer?.position() ?: return
        val newProgress = if (forward) min(curr + 1000, mPlayer!!.duration()) else max(curr - 1000, 0)
        mPlayer!!.seekTo(newProgress)
        onResumeSong()
    }
    private fun onHandleRefreshList() {
        Constant.ensureBackgroundThread {
            mSongs = getQueuedSongs()
            checkSongShuffle()
        }
    }
    private fun getQueuedSongs(): MutableList<Song> {
        val songs = ArrayList<Song>()
        val queueItems = queueDAO.getAll()
        val allTracks = Libraries.getSortedSongs(
            if (mCurrSong?.isOTGMode() == false) {
                Libraries.fetchAllSongs(this, null, null)
            } else {
                Libraries.fetchSongsByOtg(this)
            }
        )
        val wantedSongs = ArrayList<Song>()
        for (wanted in queueItems) {
            val wantedSong = allTracks.firstOrNull { it.id == wanted.songId }
            if (wantedSong != null) {
                wantedSongs.add(wantedSong)
                continue
            }
        }
        songs.addAll(wantedSongs)
        return songs.distinctBy { it.id }.toMutableList()
    }
    private fun checkSongShuffle() {
        if (settings.isShuffleEnabled) {
            mSongs.shuffle()
            if (mCurrSong != null) {
                mSongs.remove(mCurrSong)
                mSongs.add(0, mCurrSong!!)
            }
        }
    }
    private fun updateProgress(progress: Int) {
        mPlayer!!.seekTo(progress)
        saveSongProgress()
        onResumeSong()
    }
    private fun songChanged() {
        broadcastSongChange()
        updateMediaSession()
        updateMediaSessionState()
    }
    private fun songStateChanged(playing: Boolean = isPlaying(), notify: Boolean = true) {
        onHandleProgressHandler(playing)
        broadcastSongStateChange(playing)
        if (notify) startForegroundOrNotify()
    }
    private fun setSong(wantedId: Long, isChange: Boolean) {
        if (mSongs.isEmpty()) {
            onHandleEmptyList()
            return
        }
        initMediaPlayerIfNeeded()
        if (mCurrSong == null || isChange) {
            mCurrSong = mSongs.firstOrNull { it.id == wantedId } ?: return
        }
        initializeMetadata()
        try {
            val songUri = mCurrSong!!.toContentUri()
            mPlayer!!.setDataSource(songUri)
            songChanged()
        } catch (e: IOException) {
            if (retriedSongCount < 3) {
                retriedSongCount++
                setupNextSong()
            }
        } catch (ignored: Exception) {
        }
    }
    private fun broadcastSongProgress(progress: Int) {
        playbackStateManager.progressUpdated(progress)
        updateMediaSessionState()
    }
    private fun broadcastSongChange() {
        Handler(Looper.getMainLooper()).post {
            playbackStateManager.songChanged(mCurrSong)
        }
        saveSongProgress()
    }
    private fun broadcastSongStateChange(playing: Boolean) {
        playbackStateManager.songStateChanged(playing)
    }
    private fun updateMediaSession() {
        val imageScreen = getSongCoverImage() ?: mPlaceholder
        mCurrSongCover = imageScreen
        val song = mCurrSong
        if (song == null) {
            mMediaSession?.setMetadata(null)
            return
        }
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration.toLong())
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (findIndex() + 1).toLong())
            .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year.toLong())
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, imageScreen)
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, mSongs.size.toLong())

        mMediaSession?.setMetadata(metadata.build())
    }
    private fun updateMediaSessionState() {
        val builder = PlaybackStateCompat.Builder()
        val dismissAction = PlaybackStateCompat.CustomAction.Builder(
            Constant.DISMISS,
            getString(R.string.dismiss),
            R.drawable.ic_close
        ).build()

        builder.setActions(PLAYBACK_STATE_ACTIONS)
            .setState(
                if (isPlaying()) {
                    PlaybackStateCompat.STATE_PLAYING
                } else {
                    PlaybackStateCompat.STATE_PAUSED
                },
                (mPlayer?.position() ?: 0).toLong(), 1f
            )
            .addCustomAction(dismissAction)
        try {
            mMediaSession?.setPlaybackState(builder.build())
        } catch (ignored: Exception) {
        }
    }
    private fun getSongCoverImage(): Bitmap? {
        if (mPlaceholder == null) {
            mPlaceholder = BitmapFactory.decodeResource(resources, R.drawable.ic_start_music)
        }
        val mContext = baseContext ?: applicationContext
        val coverResolver = when (settings.coverMode) {
            Constant.COVER_OFF -> return null
            Constant.COVER_MEDIA_STORE -> mCurrSong?.getAlbumArt(mContext)
            else -> mCurrSong?.getTrackArt(mContext)
        }
        if (coverResolver == null) {
            if (Constant.isQPlus()) {
                if (mCurrSong?.path?.startsWith("content://") == true) {
                    try {
                        val size = Size(512, 512)
                        return contentResolver.loadThumbnail(mCurrSong!!.path.toUri(), size, null)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        return coverResolver
    }
    private fun destroyPlayer() {
        saveSongProgress()
        mCurrSong = null
        mPlayer?.release()
        mPlayer = null
        songStateChanged(playing = false, notify = false)
        songChanged()
        isServiceInit = false
    }
    private fun isEndedPlaylist(): Boolean {
        return when (mSongs.size) {
            0, 1 -> true
            else -> mCurrSong?.id == mSongs.last().id
        }
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    private fun initMediaPlayerIfNeeded() {
        if (mPlayer != null) return
        mPlayer = MusicPlayer(
            app = applicationContext as Application,
            listener = this
        )
    }
    private fun startForegroundOrNotify() {
        val notificationUtils = this.notificationUtils ?: return
        notificationHandler.removeCallbacksAndMessages(null)
        notificationHandler.postDelayed({
            if (mCurrSongCover?.isRecycled == true) {
                mCurrSongCover = BitmapFactory.decodeResource(resources, R.drawable.ic_start_music)
            }
            notificationUtils.createMusicNotification(
                song = mCurrSong,
                playing = isPlaying(),
                largeIcon = mCurrSongCover
            ) {
                notificationUtils.notify(NotificationUtils.NOTIFICATION_ID, it)
                try {
                    if (Constant.isQPlus()) {
                        startForeground(
                            NotificationUtils.NOTIFICATION_ID, it,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NotificationUtils.NOTIFICATION_ID, it)
                    }
                } catch (ignored: IllegalStateException) {
                }
            }
        }, 200L)
    }
    private fun prepareNext(nextSong: Song? = null) {
        mNextSong = if (nextSong != null) {
            nextSong
        } else {
            val queueItem = getSkipQueue(true)
            mSongs.firstOrNull { it.id == queueItem.songId } ?: return
        }
        try {
            val songUri = mNextSong!!.toContentUri()
            mPlayer!!.setNextDataSource(songUri) {
                songChanged()
            }
        } catch (ignored: Exception) {
        }
    }
    private fun maybePrepareNext() {
        val isGapLess = settings.gaplessPlayback
        val isPlayerInitialized = mPlayer != null && mPlayer!!.isInitialized
        if (!isGapLess || !isPlayerInitialized) {
            return
        }
        prepareNext()
    }
    override fun onPrepared() {
        retriedSongCount = 0
        if (playOnPrepare) {
            mPlayer!!.start()
        }
        if (setProgressOnPrepare > 0) {
            mPlayer!!.seekTo(setProgressOnPrepare)
            broadcastSongProgress(setProgressOnPrepare)
            setProgressOnPrepare = 0
        }
        maybePrepareNext()
        songStateChanged()
    }
    override fun onTrackEnded() {
        if (!settings.autoplay) return
        val playbackRepeat = settings.playbackRepeat

        playOnPrepare = when (playbackRepeat) {
            PlaybackRepeat.REPEAT_OFF -> !isEndedPlaylist()
            PlaybackRepeat.REPEAT_PLAYLIST, PlaybackRepeat.REPEAT_SONG -> true
            PlaybackRepeat.STOP_AFTER_CURRENT_SONG -> false
        }

        when (playbackRepeat) {
            PlaybackRepeat.REPEAT_OFF -> {
                if (isEndedPlaylist()) {
                    broadcastSongProgress(0)
                    setupNextSong()
                } else {
                    setupNextSong()
                }
            }
            PlaybackRepeat.REPEAT_PLAYLIST -> setupNextSong()
            PlaybackRepeat.REPEAT_SONG -> if (mCurrSong != null) setSong(mCurrSong!!.id, false)
            PlaybackRepeat.STOP_AFTER_CURRENT_SONG -> {
                broadcastSongProgress(0)
                if (mCurrSong != null) setSong(mCurrSong!!.id, false)
            }
        }
    }
    override fun onTrackWentToNext() {
        mCurrSong = mNextSong
        maybePrepareNext()
        songStateChanged()
    }
    override fun onPlayStateChanged() {
        songStateChanged()
        updateMediaSessionState()
    }
    private fun stopForegroundOrNotification() {
        try {
            notificationHandler.removeCallbacksAndMessages(null)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notificationUtils?.cancel(NotificationUtils.NOTIFICATION_ID)
        } catch (ignored: IllegalArgumentException) {
        }
    }
    private fun saveSongProgress() {
        val currSong = mCurrSong ?: return
        Constant.ensureBackgroundThread {
            val position = mPlayer?.position()
            queueDAO.apply {
                resetCurrent()
                if (position == null || position == 0) {
                    saveCurrentSong(currSong.id)
                } else {
                    saveCurrentSongProgress(currSong.id, position)
                }
            }
        }
    }
    private inner class MediaButtonSessionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (onHandleMediaButton(intent) && isOrderedBroadcast) {
                abortBroadcast()
            }
        }
    }
    companion object {
        private const val PLAYBACK_STATE_ACTIONS = PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_PAUSE

        private var mNextSong: Song? = null
        var mPlayer: MusicPlayer? = null
        var mCurrSong: Song? = null
        var mSongs = mutableListOf<Song>()
        var isGlobalPlayAnim = false

        fun isMusicPlayer() = mPlayer != null
        fun isPlaying() = mPlayer != null && mPlayer!!.isPlaying()
        fun findIndex() = mSongs.indexOf(mCurrSong)
    }
}