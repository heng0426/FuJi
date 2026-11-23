package com.xiaofu.daily.data.local.model

/** 记录类型，与时间线图标、Tab 样式一一映射。DB 存常量名，上线后不可改名。 */
enum class RecordType {
    WATER,
    FOOD,
    WEIGHT,
    DEWORM,
    VACCINE,
    ANOMALY,
    VET,
}

/** 喂食类别：主粮 / 加餐 */
enum class MealKind {
    MAIN,
    SNACK,
}

/** 驱虫类别：内驱 / 外驱 */
enum class DewormKind {
    INTERNAL,
    EXTERNAL,
}

/** 异常严重程度：轻 / 中 / 重 */
enum class Severity {
    LIGHT,
    MEDIUM,
    HEAVY,
}

/** 性别，未填写时为 null */
enum class Gender {
    MALE,
    FEMALE,
}
