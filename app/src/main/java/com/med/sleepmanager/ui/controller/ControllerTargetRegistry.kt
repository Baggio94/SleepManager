package com.med.sleepmanager.ui.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/** In-memory UI-only input targets. Nothing here touches sleep/wake state. */
internal data class ControllerAction(
    val label: String,
    val enabled: Boolean = true,
    val invoke: () -> Unit
)

internal class ControllerTargetRegistry {
    private data class Target(
        val id: String,
        val section: String,
        val requester: FocusRequester,
        val activate: () -> Unit,
        val actions: () -> List<ControllerAction>,
        var y: Float = Float.NaN,
        val order: Long
    )

    private val targets = linkedMapOf<String, Target>()
    private var registrationOrder = 0L
    var selectedId by mutableStateOf<String?>(null)
        private set
    var selectedAction by mutableIntStateOf(0)
        private set

    fun register(
        section: String, id: String, requester: FocusRequester,
        activate: () -> Unit, actions: () -> List<ControllerAction>
    ) {
        val key = "$section:$id"
        targets[key] = Target(id, section, requester, activate, actions, order = ++registrationOrder)
    }

    fun unregister(section: String, id: String) {
        targets.remove("$section:$id")
        // Preserve selectedId across LazyColumn disposal and free scrolling.
    }

    fun position(section: String, id: String, y: Float) {
        targets["$section:$id"]?.y = y
    }

    fun select(section: String, id: String) {
        val key = "$section:$id"
        if (selectedId != key) {
            selectedId = key
            selectedAction = 0
        }
    }

    private fun visible(section: String): List<Target> =
        targets.values.filter { it.section == section }
            .sortedWith(compareBy<Target> { if (it.y.isNaN()) Float.MAX_VALUE else it.y }
                .thenBy { it.order })

    fun move(section: String, direction: Int): Boolean {
        val ordered = visible(section)
        if (ordered.isEmpty()) return false
        val index = ordered.indexOfFirst { selectedId == "$section:${it.id}" }
        val next = if (index < 0) {
            if (direction < 0) ordered.lastIndex else 0
        } else (index + direction).coerceIn(0, ordered.lastIndex)
        if (index == next) return false
        val target = ordered[next]
        select(section, target.id)
        return runCatching { target.requester.requestFocus(); true }.getOrDefault(false)
    }

    /** First A selects rather than toggles an arbitrary default setting. */
    fun activate(section: String): Boolean {
        val target = targets[selectedId]
        if (target == null || target.section != section) {
            move(section, +1)
            return true
        }
        val actions = target.actions().filter { it.enabled }
        if (actions.isNotEmpty()) actions[selectedAction.coerceIn(0, actions.lastIndex)].invoke()
        else target.activate()
        return true
    }

    fun changeAction(section: String, direction: Int): Boolean {
        val target = targets[selectedId] ?: return false
        if (target.section != section) return false
        val available = target.actions().filter { it.enabled }
        if (available.size < 2) return false
        selectedAction = (selectedAction + direction).coerceIn(0, available.lastIndex)
        return true
    }

    fun activeActionLabel(section: String, id: String): String? {
        if (selectedId != "$section:$id") return null
        val available = targets[selectedId]?.actions()?.filter { it.enabled }.orEmpty()
        return available.getOrNull(selectedAction)?.label
    }
}
