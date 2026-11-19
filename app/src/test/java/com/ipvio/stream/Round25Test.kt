package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class Round25Test {
    @Test fun regionPrefixBeforeLanguageTagIsGerman() {
        assertEquals("de", LangTagLogic.detect("EU | DE | Filme und Serie"))
        assertEquals("de", LangTagLogic.detect("|DE| Netflix"))
        assertEquals("de", LangTagLogic.detect("DE - Filme"))
        assertEquals("de", LangTagLogic.detect("DE: Serien"))
        assertEquals("de", LangTagLogic.detect("GERMANY"))
    }

    @Test fun channelSearchNormalizesAccentsAndPunctuation() {
        assertEquals("das erste hd", ChannelSearch.norm("Das Erste  HD"))
        assertEquals("tele 5", ChannelSearch.norm("Tele-5"))
        assertEquals("cafe", ChannelSearch.norm("Café"))
    }

    @Test fun liveJsonContainsFavoriteAndNowPlaying() {
        val base = RemoteLogic.item("c1", "Das Erste", null, "HD", "DE")
        val j = RemoteLogic.live(base, true, "Tagesschau", 100L, 200L)
        assertTrue(j.endsWith("}"))
        assertTrue(j.contains("\"f\":true"))
        assertTrue(j.contains("\"np\":\"Tagesschau\""))
        assertTrue(j.contains("\"ns\":100") && j.contains("\"ne\":200"))
        val none = RemoteLogic.live(base, false, null, 0L, 0L)
        assertTrue(none.contains("\"f\":false") && !none.contains("\"np\""))
    }

    private fun ch(id: String, title: String, lang: String = "de", q: String? = null) =
        Item(id, Kind.LIVE, title, null, "http://x/$id", null, "g", quality = q, lang = lang)

    @Test fun bundlesQualityVariantsOfOneChannel() {
        val r = LiveBundleLogic.bundle(listOf(
            ch("1", "DE: Das Erste SD"), ch("2", "DE: Das Erste 4K"), ch("3", "DE: Das Erste HD"), ch("4", "DE: Das Erste FHD"),
            ch("5", "RTL HD"), ch("6", "Super RTL HD"), ch("7", "RTL")
        ))
        assertEquals(listOf("2", "5", "6"), r.list.map { it.id })
        assertEquals(listOf("2", "4", "3", "1"), r.variants["2"]!!.map { it.id })
        assertEquals(listOf("5", "7"), r.variants["5"]!!.map { it.id })
    }

    @Test fun bundlesWithResolutionPreferencesAndNearestFallback() {
        val channels = listOf(
            ch("1", "DE: Das Erste SD"), ch("2", "DE: Das Erste 4K"), ch("3", "DE: Das Erste HD"), ch("4", "DE: Das Erste FHD")
        )
        // FHD preferred -> selects FHD (id "4")
        val rFhd = LiveBundleLogic.bundle(channels, PlaybackPrefs.LIVE_RES_FHD)
        assertEquals("4", rFhd.list.first().id)

        // HD preferred -> selects HD (id "3")
        val rHd = LiveBundleLogic.bundle(channels, PlaybackPrefs.LIVE_RES_HD)
        assertEquals("3", rHd.list.first().id)

        // SD preferred -> selects SD (id "1")
        val rSd = LiveBundleLogic.bundle(channels, PlaybackPrefs.LIVE_RES_SD)
        assertEquals("1", rSd.list.first().id)

        // 4K requested but only FHD and HD exist -> nearest is FHD
        val channelsWithout4k = listOf(ch("3", "DE: Das Erste HD"), ch("4", "DE: Das Erste FHD"))
        val rNearest4k = LiveBundleLogic.bundle(channelsWithout4k, PlaybackPrefs.LIVE_RES_4K)
        assertEquals("4", rNearest4k.list.first().id)

        // SD requested but only 4K and HD exist -> nearest is HD
        val channelsWithoutSd = listOf(ch("2", "DE: Das Erste 4K"), ch("3", "DE: Das Erste HD"))
        val rNearestSd = LiveBundleLogic.bundle(channelsWithoutSd, PlaybackPrefs.LIVE_RES_SD)
        assertEquals("3", rNearestSd.list.first().id)
    }

    @Test fun differentLanguagesAreNotBundled() {
        val r = LiveBundleLogic.bundle(listOf(ch("1", "Sport1 HD", "de"), ch("2", "Sport1", "tr")))
        assertEquals(2, r.list.size)
    }

    @Test fun parsesLetterboxdCsv() {
        val rows = ListImportLogic.parse("watchlist.csv", "Date,Name,Year,Letterboxd URI\n2020-01-01,\"Fast, Furious\",2001,http://x\n2020-01-02,Heat,1995,http://y\n".toByteArray())
        assertEquals(listOf("Fast, Furious", "Heat"), rows.map { it.title })
        assertEquals(1995, rows[1].year)
        assertEquals(ListImportLogic.Status.WATCHLIST, rows[0].status)
    }

    @Test fun parsesMalXml() {
        val xml = "<myanimelist><anime><series_title><![CDATA[Cowboy Bebop]]></series_title><series_type>TV</series_type><my_status>Completed</my_status></anime>" +
            "<anime><series_title>Akira &amp; Co</series_title><series_type>Movie</series_type><my_status>Plan to Watch</my_status></anime></myanimelist>"
        val rows = ListImportLogic.parse("animelist.xml", xml.toByteArray())
        assertEquals(listOf("Cowboy Bebop", "Akira & Co"), rows.map { it.title })
        assertEquals(ListImportLogic.Status.WATCHLIST, rows[1].status)
        assertEquals(false, rows[1].series)
    }

    @Test fun parsesTraktJsonTolerantly() {
        val json = "[{\"watched_at\":\"x\",\"movie\":{\"title\":\"Heat\",\"year\":1995,\"ids\":{\"imdb\":\"tt0113277\"}}},{\"show\":{\"title\":\"Lost\",\"year\":2004}}]"
        val rows = ListImportLogic.parse("watched.json", json.toByteArray())
        assertEquals(listOf("Heat", "Lost"), rows.map { it.title })
        assertEquals(true, rows[1].series)
    }
}
