package com.subtracker.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subtracker.data.AppDatabase
import com.subtracker.data.Subscription
import com.subtracker.widget.SubWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val subs: StateFlow<List<Subscription>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(sub: Subscription) = viewModelScope.launch {
        dao.upsert(sub)
        SubWidget().updateAll(getApplication())
    }

    fun delete(sub: Subscription) = viewModelScope.launch {
        dao.delete(sub)
        SubWidget().updateAll(getApplication())
    }
}
