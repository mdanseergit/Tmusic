/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.imports

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal official Spotify Web API client using the Client Credentials flow.
 * Credentials are supplied by the user (see PlaylistImportSettings) and never
 * bundled into the app. Each import obtains a fresh short-lived app token.
 */
class SpotifyClient(
    private val clientId: String,
    private val clientSecret: String,
) : AutoCloseable {
    private val client =
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    private suspend fun accessToken(): String {
        val basic =
            "Basic " +
                Base64.encodeToString(
                    "$clientId:$clientSecret".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )

        return try {
            val response =
                client.post("https://accounts.spotify.com/api/token") {
                    header("Authorization", basic)
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build { append("grant_type", "client_credentials") },
                        ),
                    )
                }
            val token: TokenResponse = response.body()
            if (token.access_token.isNullOrBlank()) {
                throw PlaylistImporter.ImportException(
                    PlaylistImporter.ImportException.Kind.SPOTIFY_AUTH,
                )
            }
            token.access_token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PlaylistImporter.ImportException(
                PlaylistImporter.ImportException.Kind.SPOTIFY_AUTH,
            )
        }
    }

    suspend fun playlist(id: String, onProgress: (Int) -> Unit): PlaylistImporter.FetchedPlaylist {
        val token = accessToken()

        val meta =
            fetch<SpotifyPlaylistData>(token, "https://api.spotify.com/v1/playlists/$id")

        val tracks = mutableListOf<PlaylistImporter.SourceTrack>()
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$id/tracks?limit=100"
        while (nextUrl != null) {
            val page = fetch<SpotifyPagedTracks>(token, nextUrl)
            page.items.orEmpty().forEach { item ->
                val track = item?.track ?: return@forEach
                if (track.type != "track") return@forEach
                tracks +=
                    PlaylistImporter.SourceTrack(
                        title = track.name.orEmpty().trim(),
                        artists = track.artists.orEmpty().mapNotNull { it.name },
                        album = track.album?.name,
                        duration = track.duration_ms?.let { (it / 1000).toInt() },
                        titleIdHint = null,
                    )
            }
            onProgress(tracks.size)
            nextUrl = page.next
        }

        val remoteCount = meta.tracks?.total
        return PlaylistImporter.FetchedPlaylist(
            source = PlaylistImporter.Source.SPOTIFY,
            sourceId = id,
            name = meta.name.orEmpty().ifBlank { "Imported playlist" },
            description = meta.description,
            thumbnail = meta.images?.firstOrNull()?.url,
            trackCount = remoteCount ?: tracks.size,
            tracks = tracks,
        )
    }

    private suspend inline fun <reified T> fetch(token: String, url: String): T {
        try {
            val response =
                client.get(url) {
                    header("Authorization", "Bearer $token")
                }
            if (!response.status.isSuccess()) {
                throw PlaylistImporter.ImportException.mapHttpError(response.status.value)
            }
            return response.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            throw PlaylistImporter.ImportException.mapHttpError(e.response.status.value)
        } catch (e: Exception) {
            throw PlaylistImporter.ImportException(
                PlaylistImporter.ImportException.Kind.NETWORK,
            )
        }
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String? = null,
    )

    @Serializable
    data class SpotifyPlaylistData(
        val name: String? = null,
        val description: String? = null,
        val images: List<SpotifyImage>? = null,
        val tracks: SpotifyTracksSummary? = null,
    )

    @Serializable
    data class SpotifyTracksSummary(
        val total: Int? = null,
    )

    @Serializable
    data class SpotifyImage(
        val url: String? = null,
    )

    @Serializable
    data class SpotifyPagedTracks(
        val items: List<SpotifyTrackItem?>? = null,
        val next: String? = null,
    )

    @Serializable
    data class SpotifyTrackItem(
        val track: SpotifyTrack? = null,
    )

    @Serializable
    data class SpotifyTrack(
        val name: String? = null,
        val duration_ms: Long? = null,
        val type: String? = null,
        val artists: List<SpotifyArtist>? = null,
        val album: SpotifyAlbum? = null,
    )

    @Serializable
    data class SpotifyArtist(
        val name: String? = null,
    )

    @Serializable
    data class SpotifyAlbum(
        val name: String? = null,
    )

    override fun close() {
        client.close()
    }
}