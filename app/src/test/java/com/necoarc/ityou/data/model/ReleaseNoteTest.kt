package com.necoarc.ityou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNoteTest {

    @Test
    fun appReleaseHistory_hasValidVersionStructure() {
        val history = AppReleaseHistory
        assertTrue("版本历史不应为空", history.isNotEmpty())

        val latest = history.first()
        assertTrue("第一个版本应为最新版本", latest.isLatest)
        assertEquals("最新版本号应为 v1.8.1", "v1.8.1", latest.version)
        assertTrue("最新版本应当包含变更项目", latest.changes.isNotEmpty())

        history.drop(1).forEach { older ->
            assertFalse("历史版本不应被标记为最新版本", older.isLatest)
            assertTrue("每个版本应当有变更内容", older.changes.isNotEmpty())
        }
    }
}
