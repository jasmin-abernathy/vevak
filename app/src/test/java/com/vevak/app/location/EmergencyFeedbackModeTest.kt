package com.vevak.app.location

import com.vevak.app.emergency.EmergencyFeedbackMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyFeedbackModeTest {
    @Test fun absentOrUnknownPreferenceNeverEnablesVisibleFeedback() {
        assertEquals(EmergencyFeedbackMode.Silent, EmergencyFeedbackMode.decode(null))
        assertEquals(EmergencyFeedbackMode.Silent, EmergencyFeedbackMode.decode("corrupt-or-future-mode"))
    }
    @Test fun explicitChoicesAreRestored() {
        EmergencyFeedbackMode.entries.forEach { assertEquals(it, EmergencyFeedbackMode.decode(it.name)) }
    }
}
