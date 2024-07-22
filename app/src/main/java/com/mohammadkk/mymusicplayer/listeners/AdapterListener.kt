package com.mohammadkk.mymusicplayer.listeners


interface AdapterListener {
    fun onReloadLibrary(mode: String?) {}
    fun onDestroyService() {}
}