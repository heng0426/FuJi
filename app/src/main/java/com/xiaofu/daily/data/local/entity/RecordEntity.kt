package com.xiaofu.daily.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.data.local.model.Severity

/**
 * 核心记录表（单表多态）：六类记录共用，类型专属列可空。
 * 哪类记录必填哪些列由仓储层校验，DB 不做类型级约束。
 */
@Entity(
    tableName = "records",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["type", "occurred_at"]),
        Index(value = ["next_due_date"]),
    ],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "pet_id")
    val petId: Long = 1,
    @ColumnInfo(name = "type")
    val type: RecordType,
    /** 发生时刻，epoch 毫秒；可修改（补录） */
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    // ---- 喝水 ----
    @ColumnInfo(name = "amount_ml")
    val amountMl: Int? = null,

    // ---- 喂食 ----
    @ColumnInfo(name = "meal_kind")
    val mealKind: MealKind? = null,
    /**
     * 份数。列类型保留 REAL 以兼容 Room v1 schema，但应用层只写整份 1–20；
     * 历史半份在数据库打开时统一四舍五入为整份（0.5 → 1）。
     */
    @ColumnInfo(name = "serving_count")
    val servingCount: Double? = null,
    @ColumnInfo(name = "grams_per_serving")
    val gramsPerServing: Int? = null,
    /** 本次总克数：冗余存库，设置变更不改写历史 */
    @ColumnInfo(name = "total_grams")
    val totalGrams: Int? = null,
    /** 加餐类型（冻干/罐头/零食/自制/其他），仅加餐使用 */
    @ColumnInfo(name = "snack_type")
    val snackType: String? = null,

    // ---- 体重（存克；UI 以"斤"展示，0.1 斤 = 50 g） ----
    @ColumnInfo(name = "weight_grams")
    val weightGrams: Int? = null,

    // ---- 驱虫 ----
    @ColumnInfo(name = "deworm_kind")
    val dewormKind: DewormKind? = null,
    @ColumnInfo(name = "medicine")
    val medicine: String? = null,
    /** 下次到期日，epochDay；驱虫默认 = 记录日 + 周期，可手动覆盖 */
    @ColumnInfo(name = "next_due_date")
    val nextDueDate: Long? = null,

    // ---- 疫苗 ----
    @ColumnInfo(name = "vaccine_name")
    val vaccineName: String? = null,

    // ---- 异常 ----
    /** 分类标签文字快照；删除标签库条目不影响历史记录 */
    @ColumnInfo(name = "anomaly_category")
    val anomalyCategory: String? = null,
    @ColumnInfo(name = "severity")
    val severity: Severity? = null,

    // ---- 就医 ----
    @ColumnInfo(name = "vet_hospital")
    val vetHospital: String? = null,
    @ColumnInfo(name = "vet_doctor")
    val vetDoctor: String? = null,
    @ColumnInfo(name = "vet_diagnosis")
    val vetDiagnosis: String? = null,
    /** 费用（元），选填；不做小数，避免金额精度问题 */
    @ColumnInfo(name = "vet_cost_yuan")
    val vetCostYuan: Int? = null,
)
