package com.mohammadkk.mymusicplayer.repo.loader

import android.content.Context
import com.mohammadkk.mymusicplayer.models.Artist

interface IArtists {
    suspend fun all(context: Context): List<Artist>
    suspend fun id(context: Context, id: Long): Artist
    suspend fun searchByName(context: Context, query: String): List<Artist>
}