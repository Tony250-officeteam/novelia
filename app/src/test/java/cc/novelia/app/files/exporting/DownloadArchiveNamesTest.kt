package cc.novelia.app.files.exporting

import cc.novelia.app.data.model.BookCard
import cc.novelia.app.data.model.BookRef
import cc.novelia.app.data.model.DownloadEntry
import cc.novelia.app.data.model.WenkuDetail
import cc.novelia.app.data.storage.appJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class DownloadArchiveNamesTest {
    private val ref = BookRef("wenku", "series")
    private val book = BookCard(ref, "星之旅", authors = listOf("作者甲"), artists = listOf("画师乙"))
    private fun entry(id: String, name: String, card: BookCard? = book) =
        DownloadEntry(id, name, "$id-$name", "https://example.invalid/file", status = "已完成",
            sourceBook = card?.ref, sourceCard = card)

    @Test fun volumesAndFormatsShareOneFolderDespiteDifferentTitleSnapshots() {
        val entries = listOf(
            entry("one", "第一卷.txt", book.copy(title = "旧书名")),
            entry("two", "第二卷.epub"),
            entry("three", "第一卷.epub", book.copy(title = "另一旧书名")),
        )
        val paths = downloadArchivePaths(entries, listOf(book))
        assertEquals(listOf("[作者甲×画师乙] 星之旅/第一卷.txt", "[作者甲×画师乙] 星之旅/第二卷.epub",
            "[作者甲×画师乙] 星之旅/第一卷.epub"), paths.values.toList())
    }

    @Test fun distinctWorksWithTheSameNameUseSeparateFoldersAndFileSuffixesStayWithinTheirFolder() {
        val other = book.copy(ref = BookRef("wenku", "other"))
        val third = book.copy(ref = BookRef("kakuyomu", "series"))
        val paths = downloadArchivePaths(listOf(entry("one", "卷.txt"), entry("two", "卷.txt"),
            entry("three", "卷.txt", other), entry("four", "卷.txt", third)), emptyList())
        assertEquals(listOf("[作者甲×画师乙] 星之旅/卷.txt", "[作者甲×画师乙] 星之旅/卷（2）.txt",
            "[作者甲×画师乙] 星之旅（2）/卷.txt", "[作者甲×画师乙] 星之旅（3）/卷.txt"), paths.values.toList())
    }

    @Test fun multipleContributorsAreTrimmedAndDeduplicatedAndMissingCreditsUseTheTitle() {
        val contributors = book.copy(authors = listOf(" 作者甲 ", "", "作者乙", "作者甲"), artists = listOf("画师甲", " 画师乙 ", ""))
        assertEquals("[作者甲、作者乙×画师甲、画师乙] 星之旅/卷.txt",
            downloadArchivePaths(listOf(entry("one", "卷.txt", contributors)), emptyList()).getValue("one"))
        for(artists in listOf(null, emptyList(), listOf(" "))) {
            assertEquals("[作者甲] 星之旅/卷.txt",
                downloadArchivePaths(listOf(entry("one", "卷.txt", book.copy(artists = artists))), emptyList()).getValue("one"))
        }
        assertEquals("星之旅/卷.txt",
            downloadArchivePaths(listOf(entry("one", "卷.txt", book.copy(authors = emptyList()))), emptyList()).getValue("one"))
        assertEquals("原文书名/卷.txt",
            downloadArchivePaths(listOf(entry("one", "卷.txt", book.copy(title = "", originalTitle = "原文书名", authors = emptyList()))), emptyList()).getValue("one"))
    }

    @Test fun legacyUrlsGroupVolumesAndCachedDetailsSupplyMissingMetadataOffline() {
        val entries = listOf(
            entry("one", "第一卷.txt", null).copy(url = "https://n.novelia.cc/api/wenku/series/file/first.txt"),
            entry("two", "第二卷.epub", null).copy(sourceBook = ref, sourceCard = book.copy(artists = null)),
        )
        val reads = mutableListOf<BookRef>()
        val books = downloadArchiveBooks(entries, listOf(book.copy(artists = null))) { requested ->
            reads += requested
            appJson.encodeToString(WenkuDetail(title = "原文书名", titleZh = "星之旅",
                authors = listOf("作者甲"), artists = listOf("画师乙")))
        }
        assertEquals(listOf(ref), reads)
        assertEquals(listOf("[作者甲×画师乙] 星之旅/第一卷.txt", "[作者甲×画师乙] 星之旅/第二卷.epub"),
            downloadArchivePaths(entries, books).values.toList())
    }

    @Test fun legacyWebUrlsUseCachedAuthorAndTitle() {
        val entries = listOf(
            entry("one", "jp.旧书名.txt", null).copy(url = "https://n.novelia.cc/api/novel/syosetu/n1234/file"),
            entry("two", "zh.旧书名.epub", null).copy(url = "https://n.novelia.cc/api/novel/syosetu/n1234/file"),
        )
        val books = downloadArchiveBooks(entries, emptyList()) {
            """{"titleJp":"原文书名","titleZh":"译名","authors":[{"name":"作者甲"}]}"""
        }
        assertEquals(listOf("[作者甲] 译名/jp.旧书名.txt", "[作者甲] 译名/zh.旧书名.epub"),
            downloadArchivePaths(entries, books).values.toList())
    }

    @Test fun missingOrCorruptMetadataDoesNotBlockExportOrMergeUnrelatedOldDownloads() {
        val entries = listOf(entry("one", "卷.txt", null), entry("two", "卷.txt", null),
            entry("three", "另一卷.epub", null).copy(sourceBook = ref))
        val books = downloadArchiveBooks(entries, emptyList()) { "not json" }
        assertTrue(books.isEmpty())
        assertEquals(listOf("卷.txt/卷.txt", "卷.txt（2）/卷.txt", "另一卷.epub/另一卷.epub"),
            downloadArchivePaths(entries, books).values.toList())
        assertEquals("未命名作品/下载文件",
            downloadArchivePaths(listOf(entry("blank", "", null)), emptyList()).getValue("blank"))
    }

    @Test fun mismatchedSourceMetadataIsIgnoredAndKnownEmptyArtistsOverrideOldSnapshots() {
        val entries = listOf(entry("one", "卷.txt", book.copy(ref = BookRef("wenku", "wrong"))).copy(sourceBook = ref))
        assertEquals("卷.txt/卷.txt", downloadArchivePaths(entries, emptyList()).getValue("one"))
        assertEquals("[作者甲] 星之旅/卷.txt",
            downloadArchivePaths(listOf(entry("one", "卷.txt")), listOf(book.copy(artists = emptyList()))).getValue("one"))
    }

    @Test fun namesCannotCreateNestedOrParentPathsAndReservedNamesRemainExtractable() {
        val unsafe = book.copy(title = "../旅途\\终点:?\n继续. ", authors = listOf("作/者"), artists = listOf("画\\师"))
        val path = downloadArchivePaths(listOf(entry("one", "CON.txt", unsafe)), emptyList()).getValue("one")
        assertEquals("[作_者×画_师] .._旅途_终点___继续/_CON.txt", path)
        assertEquals(2, path.split('/').size)
        assertFalse(path.contains('\\'))
        assertEquals("_CON/卷.txt",
            downloadArchivePaths(listOf(entry("one", "卷.txt", book.copy(title = "CON", authors = emptyList()))), emptyList()).getValue("one"))
        assertEquals("未命名作品/卷.txt",
            downloadArchivePaths(listOf(entry("one", "卷.txt", book.copy(title = "..", authors = emptyList()))), emptyList()).getValue("one"))
    }

    @Test fun collisionsAreCaseInsensitiveAndUnicodeNormalizedIncludingExistingSuffixes() {
        val entries = listOf(entry("one", "É.TXT"), entry("two", "e\u0301.txt"),
            entry("three", "É（2）.txt"), entry("four", "é.txt"))
        val paths = downloadArchivePaths(entries, emptyList()).values.map { it.substringAfter('/') }
        assertEquals(listOf("É.TXT", "e\u0301（2）.txt", "É（2）（2）.txt", "é（3）.txt"), paths)
        val a = book.copy(title = "STORY", authors = emptyList())
        val b = a.copy(ref = BookRef("wenku", "other"), title = "story")
        assertEquals(listOf("STORY/卷.txt", "story（2）/卷.txt"),
            downloadArchivePaths(listOf(entry("one", "卷.txt", a), entry("two", "卷.txt", b)), emptyList()).values.toList())
    }

    @Test fun longUnicodeFolderNamesRemainValidAndDistinctAfterTruncation() {
        val long = book.copy(title = "旅行📚".repeat(100), authors = emptyList())
        val other = long.copy(ref = BookRef("wenku", "other"), title = long.title + "第二部")
        val folders = downloadArchivePaths(listOf(entry("one", "卷.epub", long), entry("two", "卷.epub", other)), emptyList())
            .values.map { it.substringBefore('/') }
        assertEquals(2, folders.toSet().size)
        folders.forEach {
            assertTrue(it.toByteArray(Charsets.UTF_8).size <= 255)
            assertEquals(it, it.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        }
    }
}
