package com.example.myapplication3.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SharedDataManager {
    private val _recentExpressions = MutableStateFlow<List<FaceExpression>>(emptyList())
    val recentExpressions: StateFlow<List<FaceExpression>> = _recentExpressions.asStateFlow()

    private var _lastFatigueAlertTime: Long = 0L
    private var _fatigueAlertCount: Int = 0
    private var _lastSaveTime: Long = 0L
    private var _isSaving: Boolean = false

    fun addExpression(expression: FaceExpression) {
        val currentList = _recentExpressions.value.toMutableList()
        currentList.add(expression)
        _recentExpressions.value = currentList.toList()
        android.util.Log.d("SharedDataManager", "addExpression called, new size = ${currentList.size}")
    }

    fun clear() {
        _recentExpressions.value = emptyList()
    }

    fun getExpressions(): List<FaceExpression> = _recentExpressions.value
    
    fun size(): Int = _recentExpressions.value.size

    fun getLastFatigueAlertTime(): Long = _lastFatigueAlertTime

    fun setLastFatigueAlertTime(time: Long) {
        _lastFatigueAlertTime = time
    }

    fun getFatigueAlertCount(): Int = _fatigueAlertCount

    fun incrementFatigueAlertCount() {
        _fatigueAlertCount++
    }

    fun resetFatigueAlertTime() {
        _lastFatigueAlertTime = 0L
    }

    fun resetFatigueAlertCount() {
        _fatigueAlertCount = 0
    }

    fun getLastSaveTime(): Long = _lastSaveTime

    fun setLastSaveTime(time: Long) {
        _lastSaveTime = time
    }

    fun isSaving(): Boolean = _isSaving

    fun setSaving(saving: Boolean) {
        _isSaving = saving
    }
}
