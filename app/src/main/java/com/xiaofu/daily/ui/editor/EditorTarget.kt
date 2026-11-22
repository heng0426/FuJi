package com.xiaofu.daily.ui.editor

import com.xiaofu.daily.data.local.entity.RecordEntity

/** 全屏录入/编辑路由（体重、驱虫、疫苗、异常四类） */
sealed interface EditorTarget {
    val record: RecordEntity?

    data class Weight(override val record: RecordEntity? = null) : EditorTarget
    data class Deworm(override val record: RecordEntity? = null) : EditorTarget
    data class Vaccine(override val record: RecordEntity? = null) : EditorTarget
    data class Anomaly(override val record: RecordEntity? = null) : EditorTarget
}
