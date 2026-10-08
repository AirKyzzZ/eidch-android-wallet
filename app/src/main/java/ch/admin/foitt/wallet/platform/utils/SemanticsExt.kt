package ch.admin.foitt.wallet.platform.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Modifier.traversalIndex(index: TraversalIndex) = semantics {
    traversalIndex = index.value
}

fun Modifier.setIsTraversalGroup(isTraversalGroup: Boolean = true, index: TraversalIndex = TraversalIndex.DEFAULT) = semantics {
    this.isTraversalGroup = isTraversalGroup
    this.traversalIndex = index.value
}

fun Modifier.replaceContentDescription(description: String) = clearAndSetSemantics {
    contentDescription = description
}

fun Modifier.contentDescription(description: String) = semantics {
    contentDescription = description
}

private const val SPACE = "\u200B"

fun Modifier.periodicLiveRegion(
    description: String,
    delay: Duration = 5.seconds,
    liveRegionMode: LiveRegionMode = LiveRegionMode.Polite,
) = composed {
    var message by remember { mutableStateOf("") }
    var addSpace by remember { mutableStateOf(false) }
    RepeatOnLifeCycleHandler {
        while (isActive) {
            message = if (addSpace) description + SPACE else description
            addSpace = !addSpace
            delay(delay)
        }
    }
    semantics {
        liveRegion = liveRegionMode
        contentDescription = message
    }
}
