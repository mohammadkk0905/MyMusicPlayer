package com.mohammadkk.mymusicplayer.repo.loader

import android.content.Context
import com.mohammadkk.mymusicplayer.models.Album

interface IAlbums {
    suspend fun all(context: Context): List<Album>
    suspend fun id(context: Context, id: Long): Album
    suspend fun searchByName(context: Context, query: String): List<Album>
    suspend fun artist(context: Context, artistId: Long): List<Album>
}