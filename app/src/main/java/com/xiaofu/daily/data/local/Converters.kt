package com.xiaofu.daily.data.local

import androidx.room.TypeConverter
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.Gender
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.data.local.model.Severity

/** 枚举 ↔ TEXT（存常量名）。注意：常量名即数据库值，改名等同数据迁移。 */
class Converters {

    @TypeConverter
    fun recordTypeToString(value: RecordType?): String? = value?.name

    @TypeConverter
    fun stringToRecordType(value: String?): RecordType? = value?.let(RecordType::valueOf)

    @TypeConverter
    fun mealKindToString(value: MealKind?): String? = value?.name

    @TypeConverter
    fun stringToMealKind(value: String?): MealKind? = value?.let(MealKind::valueOf)

    @TypeConverter
    fun dewormKindToString(value: DewormKind?): String? = value?.name

    @TypeConverter
    fun stringToDewormKind(value: String?): DewormKind? = value?.let(DewormKind::valueOf)

    @TypeConverter
    fun severityToString(value: Severity?): String? = value?.name

    @TypeConverter
    fun stringToSeverity(value: String?): Severity? = value?.let(Severity::valueOf)

    @TypeConverter
    fun genderToString(value: Gender?): String? = value?.name

    @TypeConverter
    fun stringToGender(value: String?): Gender? = value?.let(Gender::valueOf)
}
