package eu.kanade.tachiyomi.extension.zh.fengchemh

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class FengcheMhFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(FengcheMh())
}
