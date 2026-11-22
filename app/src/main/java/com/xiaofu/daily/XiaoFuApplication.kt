package com.xiaofu.daily

import android.app.Application
import android.content.Context
import com.xiaofu.daily.data.RecordRepository
import com.xiaofu.daily.data.local.XiaoFuDatabase

/** 轻量服务定位器：单进程、单数据库实例，避免引入 DI 框架 */
object AppContainer {
    lateinit var repository: RecordRepository
        private set

    fun init(context: Context) {
        val db = XiaoFuDatabase.build(context)
        repository = RecordRepository(db, context.applicationContext)
    }
}

class XiaoFuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
