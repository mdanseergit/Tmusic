/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.imports

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.imports.PlaylistImporter.SourceTrack
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Text normalization and matching helpers for importing playlists.
 * All normalization is locale-agnostic and geared toward comparing
 * song titles/artists across different services (YouTube vs Spotify).
 */
object TrackNormalizer {

    private val combiningDiacriticalMarksRegex = "\\p{Mn}+".toRegex()
    private val nonAlphanumericRegex = Regex("[^\\p{L}\\p{N}\\s]")
    private val whitespaceRegex = Regex("\\s+")
    private val parensContentsRegex = Regex("[\\[(][^\\[\\]]*[\\])]")

    // Tokens that add no musical identity and only inflate match scores
    private val fillerTokens =
        setOf(
            "official", "officialvideo", "video", "videoclip", "clip", "lyrics", "lyric",
            "hd", "4k", "audio", "remastered", "remaster", "version", "take", "single",
            "album", "bonus", "track", "topic", "mv", "visualizer", "vocals",
        )

    /** Lowercase, strip diacritics and punctuation, collapse whitespace. */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(combiningDiacriticalMarksRegex, "")
            .lowercase()
            .replace(nonAlphanumericRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    /** Throws away genres/annotations in parentheses or brackets. */
    fun stripAnnotations(title: String?): String =
        (title ?: "").replace(parensContentsRegex, " ").replace(whitespaceRegex, " ").trim()

    fun tokenSet(text: String?): Set<String> =
        normalize(text)
            .split(' ')
            .filter { it.isNotBlank() && it !in fillerTokens }
            .toSet()

    /**
     * Scores how well a candidate YouTube [SongItem] matches a [SourceTrack].
     * Returns a value in [0, 1]; higher is better.
     */
    fun similarity(source: SourceTrack, candidate: SongItem): Float {
        val sourceTitleTokens = tokenSet(stripAnnotations(source.title))
        val candidateTitleTokens = tokenSet(candidate.title)

        val titleScore =
            if (sourceTitleTokens.isEmpty() || candidateTitleTokens.isEmpty()) {
                0f
            } else {
                val intersection = sourceTitleTokens.intersect(candidateTitleTokens)
                val containment = intersection.size.toFloat() / sourceTitleTokens.size
                val jaccard =
                    intersection.size.toFloat() /
                        (sourceTitleTokens.union(candidateTitleTokens)).size
                0.65f * containment + 0.35f * jaccard
            }

        val sourceArtistTokens = source.artists.flatMap { tokenSet(it) }.toSet()
        val candidateArtistTokens =
            candidate.artists.flatMap { tokenSet(it.name) }.toSet()
        val artistScore =
            if (sourceArtistTokens.isEmpty()) {
                0.5f
            } else if (candidateArtistTokens.isEmpty()) {
                0f
            } else {
                val intersection = sourceArtistTokens.intersect(candidateArtistTokens)
                val anyOverlap = if (intersection.isNotEmpty()) 1f else 0f
                val coversAll = if (sourceArtistTokens.all { it in candidateArtistTokens }) 1f else 0f
                0.5f * anyOverlap + 0.5f * coversAll
            }

        val durationScore =
            run {
                val sourceDuration = source.duration
                val candidateDuration = candidate.duration
                if (sourceDuration != null && candidateDuration != null && candidateDuration > 0) {
                    val diff = abs(sourceDuration - candidateDuration).toFloat()
                    1f - diff / max(sourceDuration.toFloat(), candidateDuration.toFloat())
                } else {
                    0.5f
                }
            }

        return 0.6f * titleScore + 0.3f * artistScore + 0.1f * durationScore.coerceIn(0f, 1f)
    }

    /** Minimum overall score for a candidate to be accepted. */
    const val MATCH_THRESHOLD = 0.55f

    /** Minimum title contribution for acceptance (protects against artist-only matches). */
    const val MIN_TITLE_CONTRIBUTION = 0.42f

    fun isMatchAccepted(source: SourceTrack, candidate: SongItem, score: Float): Boolean {
        if (source.artists.isEmpty()) {
            return score >= MATCH_THRESHOLD + 0.1f
        }
        return score >= MATCH_THRESHOLD
    }

    /**
     * Orders candidates best-first, prefering video ids that have not been used
     * by an earlier track in the playlist.
     */
    fun pickBest(
        source: SourceTrack,
        candidates: List<SongItem>,
        usedVideoIds: Set<String>,
    ): Pair<SongItem?, Float> {
        val scored =
            candidates
                .map { it to similarity(source, it) }
                .sortedWith(compareByDescending<Pair<SongItem, Float>> { it.second }
                    .thenBy { if (it.first.id in usedVideoIds) 1 else 0 })

        val accepted = scored.filter { (candidate, score) ->
            candidate.id != source.titleIdHint && isMatchAccepted(source, candidate, score)
        }
        if (accepted.isEmpty()) {
            return null to 0f
        }
        val best = accepted.first()
        val unused = accepted.firstOrNull { it.first.id !in usedVideoIds }
        return if (unused != null && unused.second >= best.second - 0.15f) unused else best
    }
}