package eu.kanade.tachiyomi.extension.zh.fengchemh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 风车漫画 (fengchemh.com) source for Mihon / Tachyomi-compatible apps (incl. Tachimanga).
 *
 * Site layout:
 *  - detail :  {base}/comic/{id}
 *  - chapter:  {base}/chapter/{id}.html   (id is an encrypted string)
 *  - browse :  {base}/category/list/{1..4}/page/{n}   (国产/日本/韩国/欧美)
 *  - latest :  {base}/custom/update       (single page, ~50 entries)
 *  - search :  {base}/search?key=         (blocked by an image captcha, unusable)
 *
 * The chapter page embeds `params = '<base64>'`; the plaintext JSON (AES-128-CBC,
 * key "9S8\$vJnU2ANeSRoF", IV = first 16 bytes of the payload) contains the CDN image
 * URLs plus `source_id`. For `source_id == 12` the image bytes themselves are encrypted
 * (AES-128-CBC, key = IV = "my2ecret782ecret") and decrypted at fetch time (see
 * [DecryptInterceptor]); all other source ids serve plain images.
 */
class FengcheMh : HttpSource() {

    override val name = "风车漫画"

    override val baseUrl = "https://www.fengchemh.com"

    override val lang = "zh"

    override val supportsLatest = true

    // Komikku-only feature we don't provide.
    override val supportsRelatedMangas = false

    companion object {
        /** Marker appended to image URLs that need runtime decryption (source_id == 12). */
        private const val DECRYPT_MARKER = "#fengche:decrypt"

        /** Header used to tag decrypted-fetch requests; removed before the real request. */
        private const val DECRYPT_HEADER = "X-Fengche-Decrypt"
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(DecryptInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "zh-CN,zh;q=0.9")

    // ------------------------------------------------------------- catalogue / browse

    override fun popularMangaRequest(page: Int): Request {
        // 4 categories rotate: pages 1-4 -> categories 1-4 page 1, pages 5-8 -> page 2, ...
        val category = (page - 1) % 4 + 1
        val subPage = (page - 1) / 4 + 1
        return GET("$baseUrl/category/list/$category/page/$subPage", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.use { Jsoup.parse(it.body!!.string()) }
        val mangas = doc.select("a[href^=\"/comic/\"]")
            .mapNotNull { comicFromLink(it) }
            .distinctBy { it.url }
        return MangasPage(mangas, hasNextPage = true)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        // The update page has no pagination links; the query is only used to know the
        // requested page inside latestUpdatesParse.
        GET("$baseUrl/custom/update?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        if (page > 1) {
            response.close()
            return MangasPage(emptyList(), hasNextPage = false)
        }
        val doc = response.use { Jsoup.parse(it.body!!.string()) }
        val mangas = doc.select("a[href^=\"/comic/\"]")
            .mapNotNull { comicFromLink(it) }
            .distinctBy { it.url }
        return MangasPage(mangas, hasNextPage = false)
    }

    // --------------------------------------------------------------------- search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search?key=$encoded&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        // The site gates /search behind an image captcha, so it is not usable from an
        // extension. Keep the request/parse contract but always return no results.
        response.close()
        return MangasPage(emptyList(), hasNextPage = false)
    }

    // -------------------------------------------------------------------- details

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.use { Jsoup.parse(it.body!!.string()) }
        val manga = SManga.create()
        manga.url = doc.selectFirst("h1 a")?.attr("href")
            ?: response.request.url.encodedPath
        manga.title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        doc.selectFirst("img.lazyload")?.let {
            manga.thumbnail_url = it.attr("data-original")
                .ifEmpty { it.attr("src") }
                .normalizeUrl()
        }
        doc.selectFirst("span:contains(作者)")?.let {
            manga.author = it.text().substringAfter("作者", "")
                .removePrefix("：").removePrefix(":")
                .trim()
        }
        doc.selectFirst("span:contains(标签)")?.let {
            manga.genre = it.text().substringAfter("标签", "")
                .removePrefix("：").removePrefix(":")
                .trim()
        }
        doc.selectFirst("meta[name=\"description\"]")?.attr("content")?.let { desc ->
            manga.description = desc.substringAfter("简介", desc).trim().trimStart(':', '：')
        }
        manga.initialized = true
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.use { Jsoup.parse(it.body!!.string()) }
        // The site lists chapters oldest-first; keep that order.
        return doc.select("li.ewave-playlist-item a[href^=\"/chapter/\"]").map { a ->
            SChapter.create().apply {
                url = a.attr("href")
                name = a.text().trim()
                chapter_number = Regex("""第\s*(\d+(?:\.\d+)?)\s*话""")
                    .find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                date_upload = 0
            }
        }
    }

    // --------------------------------------------------------------------- pages

    override fun pageListParse(response: Response): List<Page> {
        val html = response.use { it.body!!.string() }
        val params = FengcheMhCrypto.decryptParams(html)
        val sourceId = params.optString("source_id", "")
        val encrypted = sourceId == "12"
        val images = params.optJSONArray("images") ?: org.json.JSONArray()
        return (0 until images.length()).map { i ->
            val raw = images.optString(i)
            val url = when {
                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                raw.startsWith("//") -> "https:$raw"
                encrypted -> "https://img1.baipiaoguai.org$raw"
                else -> "$baseUrl$raw"
            }
            val imageUrl = if (encrypted) "$url$DECRYPT_MARKER" else url
            Page(i, url = "", imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("imageUrl is always set in pageListParse")

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: page.url
        val builder = GET(imageUrl, headers).newBuilder()
        if (imageUrl.endsWith(DECRYPT_MARKER)) {
            builder.header(DECRYPT_HEADER, "1")
        }
        return builder.build()
    }

    // --------------------------------------------------------------------- helpers

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun getFilterList(): FilterList = FilterList()

    /**
     * Builds an [SManga] from a list-card anchor. Cards render two anchors per comic
     * (cover + title); callers should de-duplicate by url.
     */
    private fun comicFromLink(a: Element): SManga? {
        val href = a.attr("href")
        if (!href.startsWith("/comic/")) return null
        val title = a.attr("title").takeIf { it.isNotBlank() } ?: return null
        val cover = a.selectFirst("div.img-wrapper, img")
            ?.let { el ->
                el.attr("data-original")
                    .ifEmpty { el.attr("data-background") }
                    .ifEmpty { el.attr("src") }
            }
            ?.takeIf { it.isNotBlank() }
        return SManga.create().apply {
            url = href
            this.title = title
            thumbnail_url = cover?.normalizeUrl()
        }
    }

    /** Upgrades the site's http://www.fengchemh.com cover URLs to https. */
    private fun String.normalizeUrl(): String =
        if (startsWith("http://www.fengchemh.com")) replaceFirst("http://", "https://") else this

    /**
     * Fetches the (possibly encrypted) image and returns a synthetic response whose body
     * holds the decrypted bytes. Only requests tagged with [DECRYPT_HEADER] are handled.
     */
    private object DecryptInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header(DECRYPT_HEADER) == null) return chain.proceed(request)

            // Strip our marker fragment + header before hitting the real server.
            val clean = request.newBuilder()
                .removeHeader(DECRYPT_HEADER)
                .url(request.url.newBuilder().fragment(null).build())
                .build()
            val response = chain.proceed(clean)
            val body = response.body ?: return response
            val cipher = body.bytes()
            response.close()
            val plain = FengcheMhCrypto.decryptImage(cipher)
            return response.newBuilder()
                .body(plain.toResponseBody("image/webp".toMediaType()))
                .build()
        }
    }
}
