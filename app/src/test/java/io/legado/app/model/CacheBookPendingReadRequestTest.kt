package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheBookPendingReadRequestTest {

    @Test
    fun firstRelayoutRequestPreservesReadAloudPosition() {
        val request = mergePendingReadRequest(
            previous = null,
            resetPageOffset = false,
            preserveReadAloudPosition = true,
        )

        assertEquals(
            PendingReadRequest(
                resetPageOffset = false,
                preserveReadAloudPosition = true,
            ),
            request,
        )
    }

    @Test
    fun resetPageOffsetIsRetainedAcrossPendingRequests() {
        val request = mergePendingReadRequest(
            previous = PendingReadRequest(
                resetPageOffset = true,
                preserveReadAloudPosition = true,
            ),
            resetPageOffset = false,
            preserveReadAloudPosition = true,
        )

        assertTrue(request.resetPageOffset)
        assertTrue(request.preserveReadAloudPosition)
    }

    @Test
    fun anyNavigationRequestDisablesPositionPreservation() {
        val request = mergePendingReadRequest(
            previous = PendingReadRequest(
                resetPageOffset = false,
                preserveReadAloudPosition = true,
            ),
            resetPageOffset = false,
            preserveReadAloudPosition = false,
        )

        assertFalse(request.preserveReadAloudPosition)
    }

    @Test
    fun laterRelayoutCannotOverrideNavigationRequest() {
        val request = mergePendingReadRequest(
            previous = PendingReadRequest(
                resetPageOffset = false,
                preserveReadAloudPosition = false,
            ),
            resetPageOffset = false,
            preserveReadAloudPosition = true,
        )

        assertFalse(request.preserveReadAloudPosition)
    }
}
