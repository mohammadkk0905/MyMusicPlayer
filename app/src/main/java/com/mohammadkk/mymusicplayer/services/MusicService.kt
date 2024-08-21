package com.mohammadkk.mymusicplayer.services

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.BuildConfig
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.Constant.makeShuffleList
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.musicPlaybackQueue
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.extensions.toImmutableFlag
import com.mohammadkk.mymusicplayer.extensions.toMediaSessionQueue
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.image.GlideExtensions.getCoverOptions
import com.mohammadkk.mymusicplayer.image.GlideExtensions.getSongModel
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.models.Song.Companion.emptySong
import com.mohammadkk.mymusicplayer.providers.PersistentStorage
import com.mohammadkk.mymusicplayer.services.notification.PlayingNotification
import com.mohammadkk.mymusicplayer.services.notification.PlayingNotificationMaterial
import com.mohammadkk.mymusicplayer.services.playback.Playback
import com.mohammadkk.mymusicplayer.services.playback.PlaybackManager
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicService : Service(), Playback.PlaybackCallbacks {
    private val musicBinder: IBinder = MusicBinder()
    private val settings = BaseSettings.getInstance()

    @JvmField
    var nextPosition = -1

    @JvmField
    var pendingQuit = false

    private lateinit var playbackManager: PlaybackManager

    private lateinit var storage: PersistentStorage
    private val serviceScope = CoroutineScope(Job() + Main)

    @JvmField
    var position = -1

    private val headsetReceiverIntentFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
    private var headsetReceiverRegistered = false
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var mediaStoreObserver: ContentObserver
    private var musicPlayerHandlerThread: HandlerThread? = null
    private var notHandledMetaChangedForCurrentTrack = false
    private var originalPlayingQueue = ArrayList<Song>()

    @JvmField
    var playingQueue = ArrayList<Song>()

    private var playerHandler: Handler? = null

    private var playingNotification: PlayingNotification? = null

    private var queuesRestored = false

    var repeatMode = PlaybackRepeat.REPEAT_OFF
        private set(value) {
            field = value
            settings.playbackRepeat = value
            prepareNext()
            handleAndSendChangeInternal(REPEAT_MODE_CHANGED)
        }

    @JvmField
    var isShuffleMode = false
    private var receivedHeadsetConnected = false
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null) {
                if (Intent.ACTION_HEADSET_PLUG == action) {
                    when (intent.getIntExtra("state", -1)) {
                        0 -> pause()
                        1 -> if (currentSong != emptySong) {
                            play()
                        } else {
                            receivedHeadsetConnected = true
                        }
                    }
                }
            }
        }
    }
    private var throttledSeekHandler: ThrottledSeekHandler? = null
    private var uiThreadHandler: Handler? = null
    private var wakeLock: WakeLock? = null
    private var notificationManager: NotificationManager? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService<PowerManager>()
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.name)
        }
        wakeLock?.setReferenceCounted(false)
        musicPlayerHandlerThread = HandlerThread("PlaybackHandler")
        musicPlayerHandlerThread?.start()
        playerHandler = Handler(musicPlayerHandlerThread!!.looper)
        playbackManager = PlaybackManager(this)
        playbackManager.setCallbacks(this)
        setupMediaSession()
        uiThreadHandler = Handler(Looper.getMainLooper())
        notificationManager = getSystemService()
        initNotification()
        mediaStoreObserver = MediaStoreObserver(this, playerHandler!!)
        throttledSeekHandler = ThrottledSeekHandler(this, Handler(mainLooper))
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )
        restoreState()
        sendBroadcast(Intent("$APP_PACKAGE_NAME.MUSIC_SERVICE_CREATED"))
        registerHeadsetEvents()
        storage = PersistentStorage.getInstance(this)
    }
    override fun onDestroy() {
        super.onDestroy()
        if (headsetReceiverRegistered) {
            unregisterReceiver(headsetReceiver)
            headsetReceiverRegistered = false
        }
        mediaSession?.isActive = false
        quit()
        releaseResources()
        serviceScope.cancel()
        contentResolver.unregisterContentObserver(mediaStoreObserver)
        wakeLock?.release()
        sendBroadcast(Intent("$APP_PACKAGE_NAME.MUSIC_SERVICE_DESTROYED"))
    }
    private fun acquireWakeLock() {
        wakeLock?.acquire(30000)
    }
    fun cycleRepeatMode() {
        repeatMode = repeatMode.nextPlayBackRepeat
    }

    val currentSong: Song
        get() = getSongAt(getPosition())


    private fun getNextPosition(force: Boolean): Int {
        var position = getPosition() + 1
        when (repeatMode) {
            PlaybackRepeat.REPEAT_PLAYLIST -> if (isLastTrack) {
                position = 0
            }
            PlaybackRepeat.REPEAT_SONG -> if (force) {
                if (isLastTrack) position = 0
            } else {
                position -= 1
            }
            PlaybackRepeat.REPEAT_OFF -> if (isLastTrack) {
                position -= 1
            }
            else -> if (isLastTrack) position -= 1
        }
        return position
    }
    private fun getPosition(): Int {
        return position
    }
    private fun setPosition(position: Int) {
        openTrackAndPrepareNextAt(position) { success ->
            if (success) notifyChange(PLAY_STATE_CHANGED)
        }
    }
    private fun getPreviousPosition(force: Boolean): Int {
        var newPosition = getPosition() - 1
        when (repeatMode) {
            PlaybackRepeat.REPEAT_PLAYLIST -> if (newPosition < 0) {
                newPosition = playingQueue.size - 1
            }
            PlaybackRepeat.REPEAT_SONG -> if (force) {
                if (newPosition < 0) newPosition = playingQueue.size - 1
            } else {
                newPosition = getPosition()
            }
            PlaybackRepeat.REPEAT_OFF -> if (newPosition < 0) {
                newPosition = 0
            }
            else -> if (newPosition < 0) newPosition = 0
        }
        return newPosition
    }
    private fun getShuffleMode(): Boolean {
        return isShuffleMode
    }
    fun setShuffleMode(shuffleMode: Boolean) {
        settings.isShuffleEnabled = shuffleMode
        isShuffleMode = shuffleMode
        if (shuffleMode) {
            makeShuffleList(playingQueue, getPosition())
            position = 0
        } else {
            val currentSongId = currentSong.id
            playingQueue = ArrayList(originalPlayingQueue)
            var newPosition = 0
            for (song in playingQueue) {
                if (song.id == currentSongId) {
                    newPosition = playingQueue.indexOf(song)
                    break
                }
            }
            position = newPosition
        }
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED)
        notifyChange(QUEUE_CHANGED)
    }
    private fun getSongAt(position: Int): Song {
        return if ((position >= 0) && (position < playingQueue.size)) {
            playingQueue[position]
        } else {
            emptySong
        }
    }

    val songDurationMillis: Int
        get() = playbackManager.songDurationMillis

    val songProgressMillis: Int
        get() = playbackManager.songProgressMillis

    fun handleAndSendChangeInternal(what: String) {
        handleChangeInternal(what)
        sendChangeInternal(what)
    }
    private fun initNotification() {
        playingNotification = PlayingNotificationMaterial.from(
            this, notificationManager!!, mediaSession!!
        )
    }

    private val isLastTrack: Boolean
        get() = getPosition() == playingQueue.size - 1

    val isPlaying: Boolean
        get() = playbackManager.isPlaying

    private fun notifyChange(what: String) {
        handleAndSendChangeInternal(what)
        sendPublicIntent(what)
    }
    override fun onBind(intent: Intent): IBinder {
        return musicBinder
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action != null) {
            serviceScope.launch {
                restoreQueuesAndPositionIfNecessary()
                when (intent.action) {
                    ACTION_TOGGLE_PAUSE -> if (isPlaying) {
                        pause()
                    } else {
                        play()
                    }
                    ACTION_PAUSE -> pause()
                    ACTION_PLAY -> play()
                    ACTION_REWIND -> playPreviousSong(true)
                    ACTION_SKIP -> playNextSong(true)
                    ACTION_STOP, ACTION_QUIT -> {
                        pendingQuit = false
                        quit()
                    }
                    ACTION_PENDING_QUIT -> pendingQuit = true
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTrackEnded() {
        if (!settings.autoplay) {
            notifyChange(PLAY_STATE_CHANGED)
            seek(0, false)
            return
        }
        acquireWakeLock()
        val pending = pendingQuit || repeatMode == PlaybackRepeat.STOP_AFTER_CURRENT_SONG
        if (pending || repeatMode == PlaybackRepeat.REPEAT_OFF && isLastTrack) {
            notifyChange(PLAY_STATE_CHANGED)
            seek(0, false)
            if (pendingQuit) {
                pendingQuit = false
                quit()
            }
        } else {
            playNextSong(false)
        }
        releaseWakeLock()
    }
    override fun onTrackWentToNext() {
        val pending = pendingQuit || repeatMode == PlaybackRepeat.STOP_AFTER_CURRENT_SONG
        if (pending || repeatMode == PlaybackRepeat.REPEAT_OFF && isLastTrack) {
            playbackManager.setNextDataSource(null)
            pause(false)
            seek(0, false)
            if (pendingQuit) {
                pendingQuit = false
                quit()
            }
        } else {
            position = nextPosition
            prepareNextImpl()
            notifyChange(META_CHANGED)
        }
    }
    override fun onPlayStateChanged() {
        notifyChange(PLAY_STATE_CHANGED)
    }
    override fun onUnbind(intent: Intent?): Boolean {
        if (!isPlaying) stopSelf()
        return true
    }
    fun openQueue(playingQueue: List<Song>?, startPosition: Int, startPlaying: Boolean) {
        if (!playingQueue.isNullOrEmpty() && startPosition >= 0 && startPosition < playingQueue.size) {
            originalPlayingQueue = ArrayList(playingQueue)
            this.playingQueue = ArrayList(originalPlayingQueue)
            var position = startPosition
            if (isShuffleMode) {
                makeShuffleList(this.playingQueue, startPosition)
                position = 0
            }
            if (startPlaying) {
                playSongAt(position)
            } else {
                setPosition(position)
            }
            notifyChange(QUEUE_CHANGED)
        }
    }
    @Synchronized
    fun openTrackAndPrepareNextAt(position: Int, completion: (success: Boolean) -> Unit) {
        this.position = position
        openCurrent { success ->
            completion(success)
            if (success) prepareNextImpl()
            notifyChange(META_CHANGED)
            notHandledMetaChangedForCurrentTrack = false
        }
    }
    fun pause(force: Boolean = false) {
        playbackManager.pause(force) {
            notifyChange(PLAY_STATE_CHANGED)
        }
    }
    @Synchronized
    fun play() {
        playbackManager.play { playSongAt(getPosition()) }
        if (notHandledMetaChangedForCurrentTrack) {
            handleChangeInternal(META_CHANGED)
            notHandledMetaChangedForCurrentTrack = false
        }
        notifyChange(PLAY_STATE_CHANGED)
    }
    fun playNextSong(force: Boolean) {
        playSongAt(getNextPosition(force))
    }
    fun playPreviousSong(force: Boolean) {
        playSongAt(getPreviousPosition(force))
    }
    fun playSongAt(position: Int) {
        serviceScope.launch(if (playbackManager.isLocalPlayback) Default else Main) {
            openTrackAndPrepareNextAt(position) { success ->
                if (success) {
                    play()
                } else {
                    runOnUiThread { toast(R.string.unplayable_file) }
                }
            }
        }
    }
    @Synchronized
    fun prepareNextImpl() {
        try {
            val nextPosition = getNextPosition(false)
            playbackManager.setNextDataSource(getSongAt(nextPosition).toContentUri().toString())
            this.nextPosition = nextPosition
        } catch (ignored: Exception) {
        }
    }
    fun quit() {
        pause()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForeground = false
        notificationManager?.cancel(PlayingNotification.NOTIFICATION_ID)
        stopSelf()
    }
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock!!.release()
        }
    }
    private fun removeSongImpl(song: Song) {
        val deletePosition = playingQueue.indexOf(song)
        if (deletePosition != -1) {
            playingQueue.removeAt(deletePosition)
            rePosition(deletePosition)
        }
        val originalDeletePosition = originalPlayingQueue.indexOf(song)
        if (originalDeletePosition != -1) {
            originalPlayingQueue.removeAt(originalDeletePosition)
            rePosition(originalDeletePosition)
        }
    }
    fun removeSong(song: Song) {
        removeSongImpl(song)
        notifyChange(QUEUE_CHANGED)
    }
    fun removeSongs(songs: List<Song>) {
        for (song in songs) removeSongImpl(song)
        notifyChange(QUEUE_CHANGED)
    }
    private fun rePosition(deletedPosition: Int) {
        val currentPosition = getPosition()
        if (deletedPosition < currentPosition) {
            position = currentPosition - 1
        } else if (deletedPosition == currentPosition) {
            if (playingQueue.size > deletedPosition) {
                setPosition(position)
            } else {
                setPosition(position - 1)
            }
        }
    }
    private suspend fun restoreQueuesAndPositionIfNecessary() {
        if (!queuesRestored && playingQueue.isEmpty()) {
            withContext(IO) {
                val restoredQueue = musicPlaybackQueue.savedPlayingQueue
                val restoredOriginalQueue = musicPlaybackQueue.savedOriginalPlayingQueue
                val restoredPosition = storage.getPosition()
                val restoredPositionInTrack = storage.getPositionInTrack()
                if (restoredQueue.isNotEmpty() && restoredQueue.size == restoredOriginalQueue.size && restoredPosition != -1) {
                    originalPlayingQueue = ArrayList(restoredOriginalQueue)
                    playingQueue = ArrayList(restoredQueue)
                    position = restoredPosition
                    withContext(Main) {
                        openCurrent {
                            prepareNext()
                            if (restoredPositionInTrack > 0) {
                                seek(restoredPositionInTrack)
                            }
                            notHandledMetaChangedForCurrentTrack = true
                            sendChangeInternal(META_CHANGED)
                        }
                        if (receivedHeadsetConnected) {
                            play()
                            receivedHeadsetConnected = false
                        }
                    }
                    sendChangeInternal(QUEUE_CHANGED)
                    mediaSession?.setQueueTitle(getString(R.string.now_playing_queue))
                    mediaSession?.setQueue(playingQueue.toMediaSessionQueue())
                }
            }
            queuesRestored = true
        }
    }
    private fun runOnUiThread(runnable: Runnable?){
        uiThreadHandler?.post(runnable!!)
    }
    fun savePositionInTrack() {
        storage.savePositionInTrack(songProgressMillis)
    }
    @Synchronized
    fun seek(millis: Int, force: Boolean = true): Int {
        return try {
            val newPosition = playbackManager.seek(millis, force)
            throttledSeekHandler?.notifySeek()
            newPosition
        } catch (e: Exception) {
            -1
        }
    }
    fun sendPublicIntent(what: String) {
        val intent = Intent(what.replace(APP_PACKAGE_NAME, MUSIC_PACKAGE_NAME))
        val song = currentSong
        intent.putExtra("id", song.id)
        intent.putExtra("artist", song.artist)
        intent.putExtra("album", song.album)
        intent.putExtra("track", song.title)
        intent.putExtra("duration", song.duration)
        intent.putExtra("position", songProgressMillis.toLong())
        intent.putExtra("playing", isPlaying)
        intent.putExtra("scrobbling_source", APP_PACKAGE_NAME)
        @Suppress("Deprecation")
        sendStickyBroadcast(intent)
    }
    fun toggleShuffle() {
        val shuffleMode = getShuffleMode()
        setShuffleMode(!shuffleMode)
    }
    fun updateMediaSessionPlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(MEDIA_SESSION_ACTIONS)
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                songProgressMillis.toLong(), settings.playbackSpeed
            )

        stateBuilder.addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                ACTION_STOP, getString(R.string.dismiss), R.drawable.ic_close
            ).build()
        )

        mediaSession?.setPlaybackState(stateBuilder.build())
    }
    fun updateNotification() {
        if (currentSong.id != -1L) {
            updateMediaSessionMetaData {
                notificationManager?.notify(
                    PlayingNotification.NOTIFICATION_ID, playingNotification!!.build()
                )
            }
        }
    }
    @SuppressLint("CheckResult")
    fun updateMediaSessionMetaData(onCompletion: () -> Unit) {
        Log.i(TAG, "onResourceReady: ")
        val song = currentSong
        if (song.id == -1L) {
            mediaSession?.setMetadata(null)
            return
        }
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.albumArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, getPosition().plus(1).toLong())
            .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year.toLong())
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null)
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, playingQueue.size.toLong())

        try {
            Glide.with(this)
                .asBitmap()
                .getCoverOptions(song, getDrawableCompat(R.drawable.ic_audiotrack))
                .load(getSongModel(song))
                .into(object : CustomTarget<Bitmap?> (Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        metadata.putBitmap(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            BitmapFactory.decodeResource(resources, R.drawable.ic_start_music)
                        )
                        mediaSession?.setMetadata(metadata.build())
                        onCompletion()
                    }
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap?>?
                    ) {
                        metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, resource)
                        mediaSession?.setMetadata(metadata.build())
                        onCompletion()
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })

        } catch (e: IllegalArgumentException) {
            metadata.putBitmap(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                BitmapFactory.decodeResource(resources, R.drawable.ic_start_music)
            )
            mediaSession?.setMetadata(metadata.build())
            onCompletion()
        }
    }
    private fun handleChangeInternal(what: String) {
        when (what) {
            PLAY_STATE_CHANGED -> {
                updateMediaSessionPlaybackState()
                val isPlaying = isPlaying
                if (!isPlaying && songProgressMillis > 0) {
                    savePositionInTrack()
                }
                playingNotification?.setPlaying(isPlaying)
                startForegroundOrNotify()
            }
            META_CHANGED -> {
                playingNotification?.updateMetadata(currentSong) { startForegroundOrNotify() }
                updateMediaSessionMetaData(::updateMediaSessionPlaybackState)
                storage.savePosition(getPosition())
                savePositionInTrack()
                serviceScope.launch(IO) {
                    val currentSong = currentSong
                    storage.saveSong(currentSong)
                }
            }
            QUEUE_CHANGED -> {
                mediaSession?.setQueueTitle(getString(R.string.now_playing_queue))
                mediaSession?.setQueue(playingQueue.toMediaSessionQueue())
                updateMediaSessionMetaData(::updateMediaSessionPlaybackState)
                saveQueues()
                if (playingQueue.size > 0) {
                    prepareNext()
                } else {
                    stopForegroundAndNotification()
                }
            }
        }
    }
    private fun startForegroundOrNotify() {
        if (playingNotification != null && currentSong.id != -1L) {
            if (isForeground && !isPlaying) {
                if (!Constant.isSPlus()) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForeground = false
                }
            }
            if (!isForeground && isPlaying) {
                if (Constant.isQPlus()) {
                    startForeground(
                        PlayingNotification.NOTIFICATION_ID, playingNotification!!.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(
                        PlayingNotification.NOTIFICATION_ID,
                        playingNotification!!.build()
                    )
                }
                isForeground = true
            } else {
                notificationManager?.notify(
                    PlayingNotification.NOTIFICATION_ID, playingNotification!!.build()
                )
            }
        }
    }
    private fun stopForegroundAndNotification() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager?.cancel(PlayingNotification.NOTIFICATION_ID)
        isForeground = false
    }
    @Synchronized
    private fun openCurrent(completion: (success: Boolean) -> Unit) {
        playbackManager.setDataSource(currentSong, true) { success ->
            completion(success)
        }
    }
    private fun prepareNext() {
        prepareNextImpl()
    }
    private fun registerHeadsetEvents() {
        if (!headsetReceiverRegistered) {
            registerReceiver(headsetReceiver, headsetReceiverIntentFilter)
            headsetReceiverRegistered = true
        }
    }
    private fun releaseResources() {
        playerHandler?.removeCallbacksAndMessages(null)
        musicPlayerHandlerThread?.quitSafely()
        playbackManager.release()
        mediaSession?.release()
    }
    fun restoreState(completion: () -> Unit = {}) {
        isShuffleMode = settings.isShuffleEnabled
        repeatMode = settings.playbackRepeat
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED)
        handleAndSendChangeInternal(REPEAT_MODE_CHANGED)
        serviceScope.launch {
            restoreQueuesAndPositionIfNecessary()
            completion()
        }
    }
    private fun saveQueues() {
        serviceScope.launch(IO) {
            musicPlaybackQueue.saveQueues(playingQueue, originalPlayingQueue)
        }
    }
    private fun sendChangeInternal(what: String) {
        sendBroadcast(Intent(what))
    }
    private fun setupMediaSession() {
        val mediaButtonReceiverComponentName = ComponentName(
            applicationContext, MediaButtonIntentReceiver::class.java
        )
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = mediaButtonReceiverComponentName
        val mediaButtonReceiverPendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, mediaButtonIntent, (0).toImmutableFlag()
        )
        mediaSession = MediaSessionCompat(
            this,
            BuildConfig.APPLICATION_ID,
            mediaButtonReceiverComponentName,
            mediaButtonReceiverPendingIntent
        )
        val mediaSessionCallback = MediaSessionCallback(this)
        mediaSession?.setCallback(mediaSessionCallback)
        mediaSession?.isActive = true
        mediaSession?.setMediaButtonReceiver(mediaButtonReceiverPendingIntent)
    }
    inner class MusicBinder : Binder() {
        val service: MusicService get() = this@MusicService
    }
    private class MediaStoreObserver(
        private val musicService: MusicService,
        private val mHandler: Handler
    ) : ContentObserver(mHandler), Runnable {
        override fun onChange(selfChange: Boolean) {
            mHandler.removeCallbacks(this)
            mHandler.postDelayed(this, Constant.THROTTLE)
        }
        override fun run() {
            musicService.handleAndSendChangeInternal(MEDIA_STORE_CHANGED)
        }
    }
    private class ThrottledSeekHandler(
        private val musicService: MusicService,
        private val handler: Handler
    ) : Runnable {
        fun notifySeek() {
            musicService.updateMediaSessionPlaybackState()
            handler.removeCallbacks(this)
            handler.postDelayed(this, Constant.THROTTLE)
        }
        override fun run() {
            musicService.savePositionInTrack()
            musicService.sendPublicIntent(PLAY_STATE_CHANGED)
        }
    }
    companion object {
        val TAG: String = MusicService::class.java.simpleName
        const val APP_PACKAGE_NAME = "com.mohammadkk.mymusicplayer"
        const val MUSIC_PACKAGE_NAME = "com.android.music"
        const val ACTION_TOGGLE_PAUSE = "$APP_PACKAGE_NAME.togglepause"
        const val ACTION_PLAY = "$APP_PACKAGE_NAME.play"
        const val ACTION_PAUSE = "$APP_PACKAGE_NAME.pause"
        const val ACTION_REWIND = "$APP_PACKAGE_NAME.rewind"
        const val ACTION_SKIP = "$APP_PACKAGE_NAME.skip"
        const val ACTION_STOP = "$APP_PACKAGE_NAME.stop"
        const val ACTION_QUIT = "$APP_PACKAGE_NAME.quitservice"
        const val ACTION_PENDING_QUIT = "$APP_PACKAGE_NAME.pendingquitservice"

        const val META_CHANGED = "$APP_PACKAGE_NAME.metachanged"
        const val QUEUE_CHANGED = "$APP_PACKAGE_NAME.queuechanged"
        const val PLAY_STATE_CHANGED = "$APP_PACKAGE_NAME.playstatechanged"
        const val REPEAT_MODE_CHANGED = "$APP_PACKAGE_NAME.repeatmodechanged"
        const val SHUFFLE_MODE_CHANGED = "$APP_PACKAGE_NAME.shufflemodechanged"
        const val MEDIA_STORE_CHANGED = "$APP_PACKAGE_NAME.mediastorechanged"

        private const val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SEEK_TO)
    }
}