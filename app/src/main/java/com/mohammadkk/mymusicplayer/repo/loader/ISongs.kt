package com.mohammadkk.mymusicplayer.repo.loader

import android.content.Context
import com.mohammadkk.mymusicplayer.models.Song

interface ISongs {
    suspend fun all(context: Context): List<Song>
    suspend fun otg(): List<Song>
    suspend fun id(context: Context, id: Long): Song
    suspend fun path(context: Context, path: String): Song
    suspend fun album(context: Context, albumId: Long): List<Song>
    suspend fun artist(context: Context, artistId: Long): List<Song>
    suspend fun searchByTitle(context: Context, title: String): List<Song>
}