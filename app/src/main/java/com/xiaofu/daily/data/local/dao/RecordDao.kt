package com.xiaofu.daily.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.RecordType
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    // ---- 基础增改删 ----

    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): RecordEntity?

    // ---- 时间线 ----

    /** 某日记录（首页当日时间线、日历选日），倒序 */
    @Query(
        "SELECT * FROM records WHERE occurred_at >= :startMs AND occurred_at < :endMs " +
            "ORDER BY occurred_at DESC",
    )
    fun observeDay(startMs: Long, endMs: Long): Flow<List<RecordEntity>>

    /** keyset 分页：取早于 beforeMs 的最近 limit 条，供无限上滑 */
    @Query("SELECT * FROM records WHERE occurred_at < :beforeMs ORDER BY occurred_at DESC LIMIT :limit")
    fun observeTimeline(beforeMs: Long, limit: Int): Flow<List<RecordEntity>>

    /** 带 id 兜底键的 keyset 分页（同一毫秒多条时不丢数据） */
    @Query(
        "SELECT * FROM records WHERE (occurred_at < :beforeMs OR (occurred_at = :beforeMs AND id < :beforeId)) " +
            "ORDER BY occurred_at DESC, id DESC LIMIT :limit",
    )
    fun observeTimelinePage(beforeMs: Long, beforeId: Long, limit: Int): Flow<List<RecordEntity>>

    /** 日历月历标记：取区间内记录，由调用方按本地日期分组（规避 SQL 时区问题） */
    @Query(
        "SELECT * FROM records WHERE occurred_at >= :startMs AND occurred_at < :endMs " +
            "ORDER BY occurred_at ASC",
    )
    fun observeByRange(startMs: Long, endMs: Long): Flow<List<RecordEntity>>

    // ---- 记录页 ----

    @Query("SELECT * FROM records WHERE type = :type ORDER BY occurred_at DESC LIMIT :limit")
    fun observeByType(type: RecordType, limit: Int): Flow<List<RecordEntity>>

    // ---- 首页概览聚合 ----

    /** 今日喝水合计（无记录返回 0） */
    @Query(
        "SELECT IFNULL(SUM(amount_ml), 0) FROM records WHERE type = :type " +
            "AND occurred_at >= :startMs AND occurred_at < :endMs",
    )
    fun observeDayWaterMl(type: RecordType, startMs: Long, endMs: Long): Flow<Int>

    /** 今日喝水次数（概览卡副标题） */
    @Query(
        "SELECT COUNT(*) FROM records WHERE type = :type " +
            "AND occurred_at >= :startMs AND occurred_at < :endMs",
    )
    fun observeDayWaterCount(type: RecordType, startMs: Long, endMs: Long): Flow<Int>

    /** 今日进食汇总，按餐类分组 */
    @Query(
        "SELECT meal_kind AS meal_kind, " +
            "IFNULL(SUM(serving_count), 0) AS servings, " +
            "IFNULL(SUM(total_grams), 0) AS grams " +
            "FROM records WHERE type = :type AND occurred_at >= :startMs AND occurred_at < :endMs " +
            "GROUP BY meal_kind",
    )
    fun observeDayFoodSummary(
        type: RecordType,
        startMs: Long,
        endMs: Long,
    ): Flow<List<FoodDaySummary>>

    // ---- 最近一条 / 趋势 ----

    @Query("SELECT * FROM records WHERE type = :type ORDER BY occurred_at DESC LIMIT 1")
    fun observeLatestOfType(type: RecordType): Flow<RecordEntity?>

    /** 体重曲线数据（按时间正序），UI 以"斤"展示 */
    @Query("SELECT * FROM records WHERE type = :type ORDER BY occurred_at ASC")
    fun observeAllOfType(type: RecordType): Flow<List<RecordEntity>>

    /** 喝水弹窗预填：最近一条喝水的 ml */
    @Query(
        "SELECT amount_ml FROM records WHERE type = :type AND amount_ml IS NOT NULL " +
            "ORDER BY occurred_at DESC LIMIT 1",
    )
    suspend fun getLastWaterAmount(type: RecordType): Int?

    // ---- 驱虫 / 疫苗 ----

    /** 内驱或外驱的最近一条，用于计算下次到期与倒计时 */
    @Query(
        "SELECT * FROM records WHERE type = :type AND deworm_kind = :kind " +
            "ORDER BY occurred_at DESC LIMIT 1",
    )
    fun observeLatestDeworm(type: RecordType, kind: DewormKind): Flow<RecordEntity?>

    /** 最近到期的疫苗记录（含下次接种日期，取最早到期的一条） */
    @Query(
        "SELECT * FROM records WHERE type = :type AND next_due_date IS NOT NULL " +
            "ORDER BY next_due_date ASC LIMIT 1",
    )
    fun observeNearestVaccineDue(type: RecordType): Flow<RecordEntity?>

    /** 提醒扫描：到期日在 [fromDay, toDay] 区间的驱虫/疫苗记录 */
    @Query(
        "SELECT * FROM records WHERE next_due_date IS NOT NULL " +
            "AND next_due_date BETWEEN :fromDay AND :toDay ORDER BY next_due_date ASC",
    )
    suspend fun getUpcomingDue(fromDay: Long, toDay: Long): List<RecordEntity>
}
