package com.example.bizmanagezz.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bizmanagezz.data.model.Dashboard
import com.example.bizmanagezz.data.repository.BizRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: BizRepository = BizRepository()
) : ViewModel() {

    private val _dashboard = MutableStateFlow<Dashboard?>(null)
    val dashboard: StateFlow<Dashboard?> = _dashboard.asStateFlow()

    fun loadDashboard() {
        viewModelScope.launch {
            _dashboard.value = repository.getDashboard()
        }
    }
}
