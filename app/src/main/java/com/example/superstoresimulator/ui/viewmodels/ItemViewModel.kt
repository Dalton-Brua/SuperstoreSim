package com.example.superstoresimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val itemDao: ItemDao
) : ViewModel() {

    suspend fun getItemById(itemId: String): Item? {
        return withContext(Dispatchers.IO) {
            itemDao.getItemById(itemId)
        }
    }
}
