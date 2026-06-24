package com.megatransportes.yokoh.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Simple navigator holding a history stack of [Screen].
 * - navigate(screen): push
 * - pop(): pop and return true if popped, false if already at root
 * - setRoot(screen): clear history and set root
 */
class Navigator(initial: Screen) {
    private val stack = mutableStateListOf<Screen>()
    var currentScreen: Screen by mutableStateOf(initial)
        private set

    // Optional onBack handler registered by the currently shown screen.
    // If set, performBack() will invoke it instead of the default pop().
    private var backHandler: (() -> Unit)? = null

    fun setBackHandler(handler: (() -> Unit)?) {
        backHandler = handler
    }

    init {
        stack.add(initial)
    }

    fun navigate(screen: Screen) {
        stack.add(screen)
        currentScreen = screen
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        currentScreen = stack.last()
        return true
    }

    /**
     * Perform the registered back handler if present, otherwise fallback to pop().
     * Returns true if an action was taken (handler invoked or popped), false if at root.
     */
    fun performBack(): Boolean {
        val handler = backHandler
        if (handler != null) {
            handler()
            return true
        }
        return pop()
    }

    fun setRoot(screen: Screen) {
        stack.clear()
        stack.add(screen)
        currentScreen = screen
    }

    fun replace(screen: Screen) {
        if (stack.isEmpty()) {
            setRoot(screen)
        } else {
            stack[stack.lastIndex] = screen
            currentScreen = screen
        }
    }
}
