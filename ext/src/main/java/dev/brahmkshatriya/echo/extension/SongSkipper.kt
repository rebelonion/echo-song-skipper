package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ControllerClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings

class SongSkipper : ExtensionClient, ControllerClient {
    override suspend fun onExtensionSelected() {} // no-op

    private lateinit var setting: Settings
    private var cachedArtistRegexes: List<Regex>? = null
    private var lastArtistString: String? = null

    private fun getArtistRegexes(artistString: String?): List<Regex>? {
        if (artistString == null) return null
        if (artistString == lastArtistString && cachedArtistRegexes != null) {
            return cachedArtistRegexes
        }

        val patterns = artistString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { pattern ->
                if (setting.getBoolean("song_skipper_regex_enabled") == true) {
                    pattern.toRegex()
                } else {
                    ".*${Regex.escape(pattern)}.*".toRegex(RegexOption.IGNORE_CASE)
                }
            }

        lastArtistString = artistString
        cachedArtistRegexes = patterns
        return patterns
    }
    override val settingItems: List<Setting>
        get() = listOf(
            SettingCategory(
                "Skipping Options",
                "song_skipper_skip_options",
                listOf(
                    SettingSwitch(
                        "Enable Regex",
                        "song_skipper_regex_enabled",
                        "Enable regex pattern matching for artist names",
                        setting.getBoolean("song_skipper_regex_enabled") == true
                    ),
                    SettingList(
                        "When to Skip",
                        "song_skipper_when_to_skip",
                        "When to skip the song",
                        listOf("Remove from playlist", "Skip to next song"),
                        listOf("Remove", "Skip"),
                        if (setting.getString("song_skipper_when_to_skip") == "Remove") 0 else 1
                    ),
                    SettingTextInput(
                        "Skipped Artists",
                        "song_skipper_skipped_artists",
                        "Artists to skip, comma separated (supports regex)",
                        setting.getString("song_skipper_skipped_artists") ?: ""
                    )
                )
            )
        )

    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override var onPlayRequest: (suspend () -> Unit)? = null
    override var onPauseRequest: (suspend () -> Unit)? = null
    override var onNextRequest: (suspend () -> Unit)? = null
    override var onPreviousRequest: (suspend () -> Unit)? = null
    override var onSeekRequest: (suspend (Double) -> Unit)? = null
    override var onMovePlaylistItemRequest: (suspend (Int, Int) -> Unit)? = null
    override var onRemovePlaylistItemRequest: (suspend (Int) -> Unit)? = null
    override var onShuffleModeRequest: (suspend (Boolean) -> Unit)? = null
    override var onRepeatModeRequest: (suspend (Int) -> Unit)? = null
    override var onVolumeRequest: (suspend (Double) -> Unit)? = null

    override suspend fun onPlaybackStateChanged(
        isPlaying: Boolean,
        position: Double,
        track: Track
    ) {
        val whenToSkip = setting.getString("song_skipper_when_to_skip")
        if (whenToSkip != "Skip") return

        val artistString = setting.getString("song_skipper_skipped_artists")
        val artistRegexes = getArtistRegexes(artistString) ?: return

        if (artistRegexes.isNotEmpty() && artistRegexes.any { it.matches(track.artists.joinToString()) }) {
            onNextRequest?.invoke()
        }
    }

    override suspend fun onPlaylistChanged(playlist: List<Track>) {
        val whenToSkip = setting.getString("song_skipper_when_to_skip")
        if (whenToSkip != "Remove") return

        val artistString = setting.getString("song_skipper_skipped_artists")
        val artistRegexes = getArtistRegexes(artistString) ?: return

        if (artistRegexes.isNotEmpty()) {
            for (track in playlist) {
                if (artistRegexes.any { it.matches(track.artists.joinToString()) }) {
                    onRemovePlaylistItemRequest?.invoke(playlist.indexOf(track))
                }
            }
        }
    }

    override suspend fun onPlaybackModeChanged(isShuffle: Boolean, repeatState: Int) {} // no-op

    override suspend fun onPositionChanged(position: Double) {} // no-op

    override suspend fun onVolumeChanged(volume: Double) {} // no-op
}