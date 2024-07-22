package com.mohammadkk.mymusicplayer.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mohammadkk.mymusicplayer.models.Song

class SubViewModel : ViewModel() {
    private val liveData = MutableLiveData<List<Song>>()

    fun getListData(): LiveData<List<Song>> = liveData

    fun updateList(list: List<Song>) {
        liveData.value = list
    }
}