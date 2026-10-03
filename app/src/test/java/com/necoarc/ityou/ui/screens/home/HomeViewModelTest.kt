package com.necoarc.ityou.ui.screens.home

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticlePage
import com.necoarc.ityou.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * HomeViewModel 的刷新计数与并发守卫测试。
 *
 * 重点覆盖两类此前存在的缺陷：
 * 1. 快速连点刷新会重复发起请求、并把「更新了几篇」算错；
 * 2. 刷新与触底加载并发时，旧游标的结果会被追加到新列表上。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 可编排返回值的假仓库：按调用次数依次返回预设页。 */
    private class FakeRepository(
        private val pages: List<Result<ArticlePage>>
    ) : ArticleRepository() {
        var callCount = 0
            private set
        val requestedCursors = mutableListOf<Long>()

        override suspend fun getArticlePage(
            category: ArticleCategory,
            cursor: Long
        ): Result<ArticlePage> {
            requestedCursors += cursor
            val result = pages.getOrElse(callCount) { pages.last() }
            callCount++
            return result
        }
    }

    private fun article(id: String) = Article(id = id, title = "title-$id")

    @Test
    fun `刷新时报告新增文章数`() = runTest(dispatcher) {
        val repo = FakeRepository(
            listOf(
                Result.success(ArticlePage(articles = listOf(article("a"), article("b")), hasMore = true)),
                // 刷新后：a 仍在，新增 c、d
                Result.success(
                    ArticlePage(
                        articles = listOf(article("c"), article("d"), article("a")),
                        hasMore = true
                    )
                )
            )
        )
        val vm = HomeViewModel(repo)

        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.articles.size)

        vm.refresh()
        advanceUntilIdle()

        val result = vm.uiState.value.refreshResult
        assertNotNull("刷新后应产生提示结果", result)
        assertEquals("新增 2 篇（c、d）", 2, result!!.newCount)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `刷新无新内容时报告 0 篇`() = runTest(dispatcher) {
        val same = listOf(article("a"), article("b"))
        val repo = FakeRepository(
            listOf(
                Result.success(ArticlePage(articles = same, hasMore = true)),
                Result.success(ArticlePage(articles = same, hasMore = true))
            )
        )
        val vm = HomeViewModel(repo)

        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.refreshResult?.newCount)
    }

    @Test
    fun `刷新在途时重复刷新会被忽略`() = runTest(dispatcher) {
        val repo = FakeRepository(
            listOf(Result.success(ArticlePage(articles = listOf(article("a")), hasMore = true)))
        )
        val vm = HomeViewModel(repo)

        advanceUntilIdle()
        val callsAfterInitialLoad = repo.callCount

        // 快速连点：第一次刷新的协程体在 StandardTestDispatcher 下尚未执行，
        // 因此这里主要验证「后一次调用会取消前一次、最终只产生一次请求」这一可观测契约。
        vm.refresh()
        vm.refresh()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(
            "连点刷新只应发起一次请求",
            callsAfterInitialLoad + 1,
            repo.callCount
        )
        // 且最终只产生一条结果提示，不会叠加多次
        assertNotNull(vm.uiState.value.refreshResult)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `刷新失败且列表非空时保留列表并暴露错误`() = runTest(dispatcher) {
        val repo = FakeRepository(
            listOf(
                Result.success(ArticlePage(articles = listOf(article("a")), hasMore = true)),
                Result.failure(RuntimeException("网络错误"))
            )
        )
        val vm = HomeViewModel(repo)

        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals("失败时不应清空已有列表", 1, state.articles.size)
        assertEquals("网络错误", state.errorMessage)
        assertNull("失败时不应产生更新提示", state.refreshResult)
    }

    @Test
    fun `刷新启动后触底加载会被同步守卫拦下`() = runTest(dispatcher) {
        val repo = FakeRepository(
            listOf(
                Result.success(ArticlePage(articles = listOf(article("a")), nextCursor = 100L, hasMore = true)),
                // 刷新结果：替换整个列表
                Result.success(ArticlePage(articles = listOf(article("fresh")), hasMore = true))
            )
        )
        val vm = HomeViewModel(repo)

        advanceUntilIdle()
        val callsAfterInitialLoad = repo.callCount

        // 刷新启动后立刻触底。
        // 由于 isRefreshing 在 loadFirstPage 中是**同步**置位的，
        // loadMore 的守卫应当立即拦下，不产生额外请求。
        vm.refresh()
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(
            "触底加载不应在被刷新拦下后仍发起请求",
            callsAfterInitialLoad + 1,
            repo.callCount
        )
        assertEquals(listOf("fresh"), vm.uiState.value.articles.map { it.id })
    }

    @Test
    fun `加载更多使用同步快照的游标`() = runTest(dispatcher) {
        val repo = FakeRepository(
            listOf(
                Result.success(ArticlePage(articles = listOf(article("a")), nextCursor = 42L, hasMore = true)),
                Result.success(ArticlePage(articles = listOf(article("b")), nextCursor = 84L, hasMore = true))
            )
        )
        val vm = HomeViewModel(repo)

        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(
            "第二页应携带首页返回的游标",
            listOf(0L, 42L),
            repo.requestedCursors
        )
        assertTrue(vm.uiState.value.articles.map { it.id }.containsAll(listOf("a", "b")))
    }
}
