/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.imports

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Playlist import engine. Detects the source service from a URL, fetches the
 * full track list (pagination included) and resolves every track against the
 * app's own catalog via YouTube search when needed.
 */
object PlaylistImporter {

    enum class Source {
        YOUTUBE,
        YOUTUBE_MUSIC,
        SPOTIFY,
    }

    data class SourceTrack(
        val title: String,
        val artists: List<String>,
        val album: String? = null,
        val duration: Int? = null,
        val titleIdHint: String? = null,
    )

    data class FetchedPlaylist(
        val source: Source,
        val sourceId: String,
        val name: String,
        val description: String? = null,
        val thumbnail: String? = null,
        val trackCount: Int,
        val tracks: List<SourceTrack>? = null,
        val nativeSongs: List<SongItem>? = null,
    )

    data class ResolvedTrack(
        val source: SourceTrack,
        val song: SongItem?,
        val score: Float,
        val inLibrary: Boolean,
    )

    class ImportException(
        val kind: Kind,
    ) : Exception(kind.name) {
        enum class Kind {
            INVALID_URL,
            UNSUPPORTED_SERVICE,
            INVALID_PLAYLIST,
            NETWORK,
            RATE_LIMIT,
            NO_SPOTIFY_CREDENTIALS,
            SPOTIFY_AUTH,
        }

        companion object {
            fun mapHttpError(status: Int): ImportException =
                when (status) {
                    400, 404, 403 -> ImportException(Kind.INVALID_PLAYLIST)
                    401 -> ImportException(Kind.SPOTIFY_AUTH)
                    429 -> ImportException(Kind.RATE_LIMIT)
                    else -> ImportException(Kind.NETWORK)
                }
        }
    }

    private val spotifyPlaylistIdRegex = Regex("[A-Za-z0-9]{22}")

    /**
     * Returns the source service and playlist id for a supported URL/URI,
     * or null when the link is not a supported playlist link.
     */
    fun detectSource(rawUrl: String): Pair<Source, String>? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null

        // Spotify playlist links & URIs
        if (url.startsWith("spotify:playlist:")) {
            spotifyPlaylistIdRegex.find(url)?.let { match ->
                return Source.SPOTIFY to match.value
            }
            return null
        }
        if (url.contains("spotify.com/playlist")) {
            spotifyPlaylistIdRegex.find(url)?.let { match ->
                return Source.SPOTIFY to match.value
            }
            return null
        }

        val low = url.lowercase()
        if (!low.contains("youtube.com")) return null

        val list = listParamRegex.find(url)?.groupValues?.get(1)
        if (list == null) return null

        val source = if (low.contains("music.youtube.com")) Source.YOUTUBE_MUSIC else Source.YOUTUBE
        return source to stripVlPrefix(list)
    }

    /**
     * Fetches the full playlist for a supported URL. For YouTube sources the
     * returned [FetchedPlaylist.nativeSongs] hold the exact catalog entries,
     * so no fuzzy matching is required. For Spotify only metadata is returned.
     */
    suspend fun fetchPlaylist(url: String, onProgress: (Int) -> Unit): FetchedPlaylist {
        val (source, playlistId) =
            detectSource(url)
                ?: throw ImportException(ImportException.Kind.INVALID_URL)

        return when (source) {
            Source.YOUTUBE, Source.YOUTUBE_MUSIC ->
                fetchYouTubePlaylist(playlistId, source, onProgress)

            Source.SPOTIFY -> throw ImportException(ImportException.Kind.NO_SPOTIFY_CREDENTIALS)
        }
    }

    suspend fun fetchYouTubePlaylist(
        playlistId: String,
        source: Source,
        onProgress: (Int) -> Unit,
    ): FetchedPlaylist {
        val firstPage =
            try {
                YouTube.playlist(playlistId).getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw ImportException(ImportException.Kind.NETWORK)
            } ?: throw ImportException(ImportException.Kind.INVALID_PLAYLIST)

        val songs = firstPage.songs.toMutableList()
        onProgress(songs.size)

        var continuation = firstPage.songsContinuation
        var pageCount = 1
        while (continuation != null && continuation.isNotBlank() && pageCount < FETCH_PAGE_LIMIT) {
            val next =
                try {
                    YouTube.playlistContinuation(continuation).getOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw ImportException(ImportException.Kind.NETWORK)
                } ?: break
            songs += next.songs
            continuation = next.continuation
            pageCount++
            onProgress(songs.size)
        }

        return importFromPlaylistPage(firstPage, source, songs)
    }

    private fun importFromPlaylistPage(
        page: PlaylistPage,
        source: Source,
        songs: List<SongItem>,
    ): FetchedPlaylist =
        FetchedPlaylist(
            source = source,
            sourceId = page.playlist.id,
            name = page.playlist.title.trim().ifBlank { "Imported playlist" },
            thumbnail = page.playlist.thumbnail,
            trackCount = songs.size,
            nativeSongs = songs,
            tracks = songs.map { it.toSourceTrack() },
        )

    private fun SongItem.toSourceTrack(): SourceTrack =
        SourceTrack(
            title = title,
            artists = artists.map { it.name },
            album = album?.name,
            duration = duration,
            titleIdHint = id,
        )

    /**
     * Resolves fetched tracks to concrete catalog [SongItem]s. Native YT
     * songs pass through untouched; Spotify tracks are matched by search.
     */
    suspend fun resolve(
        fetched: FetchedPlaylist,
        inLibrary: (String) -> Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<ResolvedTrack> {
        val native = fetched.nativeSongs
        if (native != null) {
            val seen = HashSet<String>()
            val resolved = native.mapNotNull { song ->
                if (!seen.add(song.id)) {
                    null
                } else {
                    ResolvedTrack(
                        source = song.toSourceTrack(),
                        song = song,
                        score = 1f,
                        inLibrary = inLibrary(song.id),
                    )
                }
            }
            onProgress(resolved.size, resolved.size)
            return resolved
        }

        val tracks =
            fetched.tracks.orEmpty().distinctBy { track ->
                "${TrackNormalizer.normalize(track.title)}|${track.artists.joinToString(",") { TrackNormalizer.normalize(it) }}"
            }

        val queryCache = HashMap<String, List<SongItem>>()
        val usedVideoIds = HashSet<String>()
        val resolved = ArrayList<ResolvedTrack>(tracks.size)
        tracks.forEachIndexed { index, track ->
            val matched = matchTrack(track, queryCache, usedVideoIds)
            resolved +=
                ResolvedTrack(
                    source = track,
                    song = matched,
                    score = if (matched != null) TrackNormalizer.similarity(track, matched) else 0f,
                    inLibrary = matched?.let { inLibrary(it.id) } == true,
                )
            onProgress(index + 1, tracks.size)
        }
        return resolved
    }

    private suspend fun matchTrack(
        track: SourceTrack,
        queryCache: MutableMap<String, List<SongItem>>,
        usedVideoIds: MutableSet<String>,
    ): SongItem? {
        val bareTitle = TrackNormalizer.stripAnnotations(track.title)
        if (TrackNormalizer.tokenSet(bareTitle).isEmpty()) return null

        val query = buildQuery(track)
        val candidates =
            queryCache.getOrPut(query) {
                try {
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()
                        ?.items
                        .orEmpty()
                        .filterIsInstance<SongItem>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }

        val (best, _) = TrackNormalizer.pickBest(track, candidates, usedVideoIds)
        best?.let { usedVideoIds += it.id }
        return best
    }

    private fun buildQuery(track: SourceTrack): String {
        val title = TrackNormalizer.stripAnnotations(track.title).trim()
        val artist = track.artists.firstOrNull()?.trim()
        return if (artist.isNullOrBlank()) title else "$title $artist"
    }

    private fun stripVlPrefix(id: String): String =
        if (id.length > 2 && id.startsWith("VL")) id.substring(2) else id

    private const val FETCH_PAGE_LIMIT = 50
    private val listParamRegex = Regex("[?&]list=([A-Za-z0-9_-]{1,64})")
}