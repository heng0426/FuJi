package com.xiaofu.daily.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterBottleTest {

    @Test
    fun subtractsRemainingFromFilledBottle() {
        assertEquals(250, bottleDrinkMl("500", "250"))
        assertEquals(500, bottleDrinkMl("500", "0"))
    }

    @Test
    fun emptyOrNonNumericCannotCompute() {
        assertNull(bottleDrinkMl("", "250"))
        assertNull(bottleDrinkMl("500", ""))
        assertNull(bottleDrinkMl("", ""))
        assertNull(bottleDrinkMl("五百", "250"))
    }

    @Test
    fun remainingAboveFilledIsInvalid() {
        assertNull(bottleDrinkMl("300", "400"))
        assertEquals("本次剩余不能超过上次剩余", bottleHint("300", "400"))
    }

    @Test
    fun zeroDrinkIsInvalid() {
        assertNull(bottleDrinkMl("500", "500"))
        assertEquals("喝水量为 0，不用记", bottleHint("500", "500"))
    }

    @Test
    fun filledBottleMustBeWithinDailyLimit() {
        assertNull(bottleDrinkMl("0", "0"))
        assertNull(bottleDrinkMl("5001", "0"))
        assertNull(bottleHint("0", "0"))
        assertNull(bottleHint("5001", "0"))
    }

    @Test
    fun emptyInputShowsNoHint() {
        assertNull(bottleHint("", ""))
        assertNull(bottleHint("500", ""))
        assertNull(bottleHint("", "250"))
    }
}
