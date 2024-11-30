package com.mohammadkk.mymusicplayer.repo.loader

import android.content.Context
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.models.Song

interface IGenres {
    suspend fun all(context: Context): List<Genre>
    suspend fun id(context: Context, id: Long): Genre
    suspend fun songs(context: Context, genreId: Long): List<Song>
}