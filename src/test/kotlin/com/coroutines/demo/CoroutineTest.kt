package com.coroutines.demo

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoroutineTest {

    private val controller: Controller = mockk()

    @BeforeEach
    fun setup() {
        every { controller.prices(any()) } returns 4.5

        coEvery {
            controller.pricesSuspending(any())
        } coAnswers {
            delay(100);  7.7
        }
    }

    @Test
    fun `should map to request model in api V3`() {
        //when
        val result = runBlocking {
            controller.pricesSuspending("AMZN")
        }

        //then
        assertEquals(7.7, result)
    }
}