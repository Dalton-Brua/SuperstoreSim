package com.example.superstoresimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val itemDao: ItemDao
) : ViewModel() {

    // ponytail: Room dispatches suspend queries off the main thread itself; no withContext(IO) needed.
    suspend fun getItemById(itemId: String): Item? = itemDao.getItemById(itemId)
}
