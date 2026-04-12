package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.cache.IImagePreloader
import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.domain.model.TemplateSource
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PrepareOfflineUseCaseTest {

    private lateinit var useCase: PrepareOfflineUseCase
    private lateinit var fakeRepo: FakeSpotifyRepo
    private lateinit var fakeCache: FakePlaylistCache
    private lateinit var fakeTemplates: FakeTemplateRepo
    private lateinit var fakePreloader: FakeImagePreloader

    @Before
    fun setUp() {
        fakeRepo = FakeSpotifyRepo()
        fakeCache = FakePlaylistCache()
        fakeTemplates = FakeTemplateRepo()
        fakePreloader = FakeImagePreloader()
        useCase = PrepareOfflineUseCase(fakeRepo, fakeCache, fakeTemplates, fakePreloader)
    }

    @Test
    fun `all-playlists mode emits correct phases`() = runTest {
        fakeRepo.playlists = listOf(
            playlist("p1", "Salsa Mix"),
            playlist("p2", "Bachata Mix")
        )
        fakeRepo.tracksByPlaylist = mapOf(
            "p1" to listOf(track("t1", "http://img1.jpg")),
            "p2" to listOf(track("t2", "http://img2.jpg"))
        )

        val progress = useCase(templateId = null).toList()

        assertTrue(progress.any { it.phase == PrepareOfflineUseCase.OfflineProgress.Phase.PLAYLISTS })
        assertTrue(progress.any { it.phase == PrepareOfflineUseCase.OfflineProgress.Phase.TRACKS })
        assertTrue(progress.any { it.phase == PrepareOfflineUseCase.OfflineProgress.Phase.IMAGES })
        assertTrue(progress.any { it.phase == PrepareOfflineUseCase.OfflineProgress.Phase.DONE })
    }

    @Test
    fun `template mode preloads only referenced playlists`() = runTest {
        fakeRepo.playlists = listOf(
            playlist("p1", "Salsa Mix"),
            playlist("p2", "Bachata Mix"),
            playlist("p3", "Unused")
        )
        fakeRepo.tracksByPlaylist = mapOf(
            "p1" to listOf(track("t1")),
            "p2" to listOf(track("t2")),
            "p3" to listOf(track("t3"))
        )
        fakeTemplates.templates[1L] = GeneratorTemplate(
            id = 1L, name = "Event",
            sources = listOf(
                TemplateSource(0, "p1", "Salsa Mix", 10),
                TemplateSource(1, "p2", "Bachata Mix", 10)
            )
        )

        useCase(templateId = 1L).toList()

        // p3 nie powinno być fetchowane
        assertTrue(fakeRepo.fetchedPlaylistIds.contains("p1"))
        assertTrue(fakeRepo.fetchedPlaylistIds.contains("p2"))
        assertFalse(fakeRepo.fetchedPlaylistIds.contains("p3"))
    }

    @Test
    fun `progress reports correct track counts`() = runTest {
        fakeRepo.playlists = listOf(
            playlist("p1", "Mix 1"),
            playlist("p2", "Mix 2")
        )
        fakeRepo.tracksByPlaylist = mapOf(
            "p1" to listOf(track("t1")),
            "p2" to listOf(track("t2"))
        )

        val trackPhases = useCase(templateId = null).toList()
            .filter { it.phase == PrepareOfflineUseCase.OfflineProgress.Phase.TRACKS }

        assertEquals(2, trackPhases.size)
        assertEquals(1, trackPhases[0].current)
        assertEquals(2, trackPhases[0].total)
        assertEquals("Mix 1", trackPhases[0].currentPlaylistName)
        assertEquals(2, trackPhases[1].current)
    }

    @Test
    fun `error in one playlist does not stop others`() = runTest {
        fakeRepo.playlists = listOf(
            playlist("p1", "Good"),
            playlist("p2", "Bad")
        )
        fakeRepo.tracksByPlaylist = mapOf("p1" to listOf(track("t1")))
        fakeRepo.failingPlaylistIds = setOf("p2")

        val progress = useCase(templateId = null).toList()
        val done = progress.last()

        assertEquals(PrepareOfflineUseCase.OfflineProgress.Phase.DONE, done.phase)
        assertTrue(done.errors.isNotEmpty())
        assertTrue(done.errors.any { it.contains("Bad") })
    }

    @Test
    fun `images are preloaded from album art urls`() = runTest {
        fakeRepo.playlists = listOf(playlist("p1", "Mix"))
        fakeRepo.tracksByPlaylist = mapOf(
            "p1" to listOf(
                track("t1", "http://img1.jpg"),
                track("t2", "http://img2.jpg"),
                track("t3", "http://img1.jpg") // dupe
            )
        )

        useCase(templateId = null).toList()

        // Deduplicated
        assertEquals(2, fakePreloader.preloadedUrls.size)
    }

    // ── Fakes ───────────────────────────────────────────────────────────

    private fun playlist(id: String, name: String) = Playlist(
        id = id, name = name, description = null, imageUrl = null,
        trackCount = 10, ownerId = "owner"
    )

    private fun track(id: String, artUrl: String? = null) = Track(
        id = id, title = "Track $id", artist = "Artist", album = "Album",
        albumArtUrl = artUrl, durationMs = 180_000, popularity = 50,
        uri = "spotify:track:$id"
    )

    private class FakeSpotifyRepo : ISpotifyRepository {
        var playlists: List<Playlist> = emptyList()
        var tracksByPlaylist: Map<String, List<Track>> = emptyMap()
        var failingPlaylistIds: Set<String> = emptySet()
        val fetchedPlaylistIds = mutableSetOf<String>()

        override suspend fun getUserPlaylists() = playlists
        override suspend fun getUserPlaylists(policy: CachePolicy) = playlists
        override suspend fun getPlaylistTracks(playlistId: String): List<Track> {
            fetchedPlaylistIds.add(playlistId)
            if (playlistId in failingPlaylistIds) throw RuntimeException("Network error")
            return tracksByPlaylist[playlistId] ?: emptyList()
        }
        override suspend fun getPlaylistTracks(playlistId: String, policy: CachePolicy) =
            getPlaylistTracks(playlistId)
        override suspend fun getLikedTracks() = emptyList<Track>()
        override suspend fun getLikedTracks(policy: CachePolicy) = emptyList<Track>()
        override suspend fun createPlaylist(name: String, description: String) = "new-id"
        override suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>) {}
        override suspend fun fetchAndCacheCurrentUser() = throw NotImplementedError()
        override suspend fun getUserProfile() = throw NotImplementedError()
        override suspend fun getTopArtists() = emptyList<com.spotify.playlistmanager.data.model.TopArtist>()
        override suspend fun getLikedTracksCount() = 0
        override suspend fun addToQueue(uri: String) {}
    }

    private class FakePlaylistCache : IPlaylistCacheRepository {
        override suspend fun getCachedPlaylists() = emptyList<Playlist>()
        override suspend fun isPlaylistsCacheFresh(ttlMs: Long, now: Long) = false
        override suspend fun cachePlaylists(playlists: List<Playlist>, now: Long) {}
        override suspend fun areTracksFresh(playlistId: String, expectedSnapshotId: String?) = false
        override suspend fun isLikedTracksFresh(ttlMs: Long, now: Long) = false
        override suspend fun getCachedTracks(playlistId: String) = null
        override suspend fun cacheTracks(playlist: Playlist, tracks: List<Track>, snapshotId: String?, now: Long) {}
        override suspend fun invalidateTracks(playlistId: String) {}
        override suspend fun invalidatePlaylistsList() {}
        override suspend fun playlistsCount() = 0
        override suspend fun tracksCount() = 0
        override suspend fun getTracksFetchedAt(playlistId: String) = null
        override suspend fun clearAll() {}
    }

    private class FakeTemplateRepo : IGeneratorTemplateRepository {
        val templates = mutableMapOf<Long, GeneratorTemplate>()
        override fun observeAll(): Flow<List<GeneratorTemplate>> = flowOf(templates.values.toList())
        override fun observeCount(): Flow<Int> = flowOf(templates.size)
        override suspend fun getById(id: Long) = templates[id]
        override suspend fun save(template: GeneratorTemplate) = 1L
        override suspend fun overwrite(template: GeneratorTemplate) {}
        override suspend fun rename(id: Long, newName: String) {}
        override suspend fun delete(id: Long) {}
    }

    private class FakeImagePreloader : IImagePreloader {
        val preloadedUrls = mutableSetOf<String>()
        override suspend fun preload(url: String) { preloadedUrls.add(url) }
        override suspend fun preloadBatch(urls: List<String>, onProgress: (Int, Int) -> Unit) {
            urls.forEachIndexed { i, url ->
                preloadedUrls.add(url)
                onProgress(i + 1, urls.size)
            }
        }
    }
}
