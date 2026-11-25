package com.xiaofu.daily.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterSourceTest {

    @Test
    fun subtractsRemainingFromFilledBottle() {
        assertEquals(250, sourceDrinkMl("500", "250"))
        assertEquals(500, sourceDrinkMl("500", "0"))
    }

    @Test
    fun onlyLastLeftSavesRemainingWithoutRecord() {
        // 只填上次剩余：只更新本机记录，不产生喝水记录
        assertTrue(sourceCanSave("500", ""))
        assertNull(sourceDrinkMl("500", ""))
        assertNull(sourceHint("500", ""))
    }

    @Test
    fun emptyOrNonNumericCannotCompute() {
        assertFalse(sourceCanSave("", "250"))
        assertNull(sourceDrinkMl("", "250"))
        assertNull(sourceDrinkMl("", ""))
        assertNull(sourceDrinkMl("五百", "250"))
        assertNull(sourceDrinkMl("500", "二百"))
    }

    @Test
    fun remainingAboveFilledIsInvalid() {
        assertFalse(sourceCanSave("300", "400"))
        assertNull(sourceDrinkMl("300", "400"))
        assertEquals("本次剩余不能超过上次剩余", sourceHint("300", "400"))
    }

    @Test
    fun zeroDrinkIsInvalid() {
        assertFalse(sourceCanSave("500", "500"))
        assertNull(sourceDrinkMl("500", "500"))
        assertEquals("喝水量为 0，不用记", sourceHint("500", "500"))
    }

    @Test
    fun filledBottleMustBeWithinDailyLimit() {
        assertFalse(sourceCanSave("0", ""))
        assertFalse(sourceCanSave("5001", ""))
        assertNull(sourceDrinkMl("0", "0"))
        assertNull(sourceDrinkMl("5001", "0"))
        assertNull(sourceHint("0", "0"))
        assertNull(sourceHint("5001", "0"))
    }

    @Test
    fun emptyInputShowsNoHint() {
        assertNull(sourceHint("", ""))
        assertNull(sourceHint("", "250"))
    }
}
