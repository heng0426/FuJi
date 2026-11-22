package com.xiaofu.daily.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaofu.daily.data.local.dao.RecordDao
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.entity.RecordPhotoEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: XiaoFuDatabase
    private lateinit var recordDao: RecordDao

    private val dayStart = 1_700_000_000_000L
    private val dayEnd = dayStart + 86_400_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, XiaoFuDatabase::class.java)
            .addCallback(XiaoFuDatabase.seedCallback)
            .build()
        recordDao = db.recordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedDataIsComplete() = runBlocking {
        val tags = db.anomalyTagDao().observeAll().first()
        assertEquals(8, tags.size)
        assertEquals("呕吐", tags.first().name)
        assertTrue(tags.first().isPreset)

        assertEquals("小福", db.profileDao().getProfile()?.name)
        assertEquals("15", db.settingsDao().get("serving_grams_current"))
        assertEquals("90", db.settingsDao().get("deworm_internal_cycle_days"))
        assertEquals("30", db.settingsDao().get("deworm_external_cycle_days"))
    }

    @Test
    fun waterSumAndLastAmount() = runBlocking {
        recordDao.insert(water(120, dayStart + 1_000))
        recordDao.insert(water(80, dayStart + 2_000))
        recordDao.insert(water(500, dayStart - 1_000)) // 前一天，不计入当日

        assertEquals(200, recordDao.observeDayWaterMl(RecordType.WATER, dayStart, dayEnd).first())
        assertEquals(80, recordDao.getLastWaterAmount(RecordType.WATER))

        val day = recordDao.observeDay(dayStart, dayEnd).first()
        assertEquals(2, day.size)
        assertEquals(80, day.first().amountMl) // 倒序：最新在前
    }

    @Test
    fun foodSummaryGroupsByMealKind() = runBlocking {
        recordDao.insert(food(MealKind.MAIN, 1.5, 15, 23, dayStart + 1_000))
        recordDao.insert(food(MealKind.SNACK, 2.0, 10, 20, dayStart + 2_000, snackType = "冻干"))

        val summary = recordDao.observeDayFoodSummary(RecordType.FOOD, dayStart, dayEnd).first()
        assertEquals(2, summary.size)

        val main = summary.first { it.mealKind == MealKind.MAIN }
        assertEquals(1.5, main.servings, 0.001)
        assertEquals(23, main.grams)

        val snack = summary.first { it.mealKind == MealKind.SNACK }
        assertEquals(2.0, snack.servings, 0.001)
        assertEquals(20, snack.grams)
    }

    @Test
    fun timelineKeysetPaging() = runBlocking {
        recordDao.insert(water(10, dayStart + 1_000))
        recordDao.insert(water(20, dayStart + 2_000))
        recordDao.insert(water(30, dayStart + 3_000))

        val firstPage = recordDao.observeTimeline(dayStart + 4_000, 2).first()
        assertEquals(listOf(30, 20), firstPage.map { it.amountMl })

        val secondPage = recordDao.observeTimeline(dayStart + 2_000, 2).first()
        assertEquals(listOf(10), secondPage.map { it.amountMl })
    }

    @Test
    fun updateAndDelete() = runBlocking {
        val id = recordDao.insert(water(50, dayStart + 1_000))
        val saved = requireNotNull(recordDao.getById(id))
        recordDao.update(saved.copy(note = "加了一点", updatedAt = dayStart + 5_000))
        assertEquals("加了一点", recordDao.getById(id)?.note)

        recordDao.deleteById(id)
        assertNull(recordDao.getById(id))
    }

    @Test
    fun deletingRecordCascadesPhotos() = runBlocking {
        val id = recordDao.insert(water(50, dayStart + 1_000))
        db.photoDao().insertAll(
            listOf(
                RecordPhotoEntity(recordId = id, fileName = "a.jpg"),
                RecordPhotoEntity(recordId = id, fileName = "b.jpg", sortOrder = 1),
            ),
        )
        assertEquals(2, db.photoDao().observeByRecord(id).first().size)

        recordDao.deleteById(id)
        assertEquals(0, db.photoDao().observeByRecord(id).first().size)
    }

    @Test
    fun dewormLatestAndUpcomingDue() = runBlocking {
        val today = dayStart / 86_400_000L
        recordDao.insert(
            RecordEntity(
                type = RecordType.DEWORM,
                occurredAt = dayStart + 1_000,
                createdAt = dayStart,
                updatedAt = dayStart,
                dewormKind = DewormKind.INTERNAL,
                medicine = "海乐妙",
                nextDueDate = today + 90,
            ),
        )
        recordDao.insert(
            RecordEntity(
                type = RecordType.DEWORM,
                occurredAt = dayStart + 2_000,
                createdAt = dayStart,
                updatedAt = dayStart,
                dewormKind = DewormKind.EXTERNAL,
                nextDueDate = today + 30,
            ),
        )

        assertEquals(
            DewormKind.INTERNAL,
            recordDao.observeLatestDeworm(RecordType.DEWORM, DewormKind.INTERNAL).first()?.dewormKind,
        )

        val due = recordDao.getUpcomingDue(today, today + 31)
        assertEquals(1, due.size)
        assertEquals(DewormKind.EXTERNAL, due.first().dewormKind)
    }

    private fun water(ml: Int, at: Long) = RecordEntity(
        type = RecordType.WATER,
        occurredAt = at,
        createdAt = at,
        updatedAt = at,
        amountMl = ml,
    )

    private fun food(
        kind: MealKind,
        servings: Double,
        gramsPerServing: Int,
        totalGrams: Int,
        at: Long,
        snackType: String? = null,
    ) = RecordEntity(
        type = RecordType.FOOD,
        occurredAt = at,
        createdAt = at,
        updatedAt = at,
        mealKind = kind,
        servingCount = servings,
        gramsPerServing = gramsPerServing,
        totalGrams = totalGrams,
        snackType = snackType,
    )
}
