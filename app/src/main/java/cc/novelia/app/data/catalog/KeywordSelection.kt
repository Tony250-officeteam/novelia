package cc.novelia.app.data.catalog

enum class KeywordSelectionState { Unselected, Included, Excluded }

/** 分类操作只展开当前成员，之后每个标签独立编辑，不保存分类与条件的绑定。 */
data class KeywordSelection(val included: List<String> = emptyList(), val excluded: List<String> = emptyList()) {
    fun stateOf(original: String): KeywordSelectionState = when(original) {
        in excluded -> KeywordSelectionState.Excluded
        in included -> KeywordSelectionState.Included
        else -> KeywordSelectionState.Unselected
    }

    fun cycle(original: String): KeywordSelection = when(stateOf(original)) {
        KeywordSelectionState.Unselected -> add(listOf(original), true)
        KeywordSelectionState.Included -> add(listOf(original), false)
        KeywordSelectionState.Excluded -> remove(original)
    }

    fun remove(original: String): KeywordSelection = copy(included = included - original, excluded = excluded - original)

    fun add(originals: Collection<String>, include: Boolean): KeywordSelection {
        val selected = originals.filter(KeywordCatalog::canSearch).distinct()
        val selectedSet = selected.toSet()
        return if(include) copy(included = (included + selected).distinct(), excluded = excluded.filterNot { it in selectedSet })
        else copy(excluded = (excluded + selected).distinct(), included = included.filterNot { it in selectedSet })
    }
}
