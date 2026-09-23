package dev.lookup.service

import dev.lookup.detection.EngineSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide bridge between the [OverlayService] (producer) and the UI
 * (consumer). The service owns the [dev.lookup.detection.DetectionEngine];
 * screens only read from here.
 */
object DetectionBus {
    /**
     * MutableStateFlow is exposed on purpose: [OverlayService] is the only writer,
     * screens only collect. (StateFlow.value is a read-only val on the interface.)
     */
    val snapshot: MutableStateFlow<EngineSnapshot> = MutableStateFlow(EngineSnapshot.INITIAL)
    val running: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val overlayPermissionLost: MutableStateFlow<Boolean> = MutableStateFlow(false)
}
