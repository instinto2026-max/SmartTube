package com.liskovsoft.smartyoutubetv2.common.misc.lyrics;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Pattern;

/**
 * Lyrics lookup on LRCLIB (https://lrclib.net), a free and open lyrics database with time-synced lyrics.<br/>
 * All methods are blocking, call them off the main thread.
 */
public class LrcLibClient {
    private static final String TAG = LrcLibClient.class.getSimpleName();
    private static final String API_URL = "https://lrclib.net/api";
    // LRCLIB asks clients to identify themselves
    private static final String USER_AGENT = "SmartTube-redesign (https://github.com/instinto2026-max/SmartTube)";
    private static final int TIMEOUT_MS = 10_000;
    // Used to tell apart a matching song from a different version (live, remix, extended cut)
    private static final long MAX_DURATION_DIFF_SEC = 5;
    // Fewer lines than this means a broken or test upload, not real lyrics
    private static final int MIN_TEXT_LINES = 5;
    private static final Pattern BRACKETS = Pattern.compile("\\s*[(\\[【][^)\\]】]*[)\\]】]");
    private static final Pattern FEAT = Pattern.compile("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$");
    private static final Pattern NOISE = Pattern.compile("(?i)\\s*(official\\s+(music\\s+)?video|official\\s+audio|lyric\\s+video|video\\s+oficial|letra|lyrics?)\\s*$");
    private static final Pattern DASH = Pattern.compile("\\s+[-–—]\\s+");
    private static final Pattern CHANNEL_NOISE = Pattern.compile("(?i)(\\s*-\\s*topic|vevo|\\s+official)$");

    /**
     * Finds lyrics for a YouTube video.
     * @param videoTitle e.g. "Artist - Song (Official Video)"
     * @param channelName fallback artist when the title doesn't name one
     * @param durationMs video length, used to skip other versions of the song. Pass 0 if unknown.
     * @return synced lyrics when available, otherwise plain lyrics, otherwise null
     */
    @Nullable
    public static Lyrics find(String videoTitle, @Nullable String channelName, long durationMs) {
        if (videoTitle == null || videoTitle.trim().isEmpty()) {
            return null;
        }

        long durationSec = durationMs / 1_000;
        String cleanTitle = cleanTitle(videoTitle);
        String artist;
        String track;

        String[] parts = DASH.split(cleanTitle, 2);
        if (parts.length == 2) {
            artist = parts[0].trim();
            track = FEAT.matcher(parts[1]).replaceAll("").trim();
        } else {
            artist = channelName != null ? CHANNEL_NOISE.matcher(channelName.trim()).replaceAll("").trim() : null;
            track = FEAT.matcher(cleanTitle).replaceAll("").trim();
        }

        try {
            Lyrics result = null;

            if (artist != null && !artist.isEmpty() && durationSec > 0) {
                result = get(artist, track, durationSec);
            }

            if (result == null && artist != null && !artist.isEmpty()) {
                result = search("track_name=" + encode(track) + "&artist_name=" + encode(artist), durationSec);
            }

            if (result == null) {
                // Also covers "Song - Artist" titles
                result = search("q=" + encode(cleanTitle), durationSec);
            }

            return result;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Lyrics lookup failed: %s", e.getMessage());
            return null;
        }
    }

    static String cleanTitle(String title) {
        String result = BRACKETS.matcher(title).replaceAll("");
        int pipe = result.indexOf('|');
        if (pipe > 0) {
            result = result.substring(0, pipe);
        }
        result = NOISE.matcher(result.trim()).replaceAll("");
        return result.replaceAll("[\"“”]", "").trim();
    }

    @Nullable
    private static Lyrics get(String artist, String track, long durationSec) throws IOException, JSONException {
        String body = request(API_URL + "/get?artist_name=" + encode(artist) + "&track_name=" + encode(track) + "&duration=" + durationSec);
        return body != null ? toLyrics(new JSONObject(body)) : null;
    }

    @Nullable
    private static Lyrics search(String query, long durationSec) throws IOException, JSONException {
        String body = request(API_URL + "/search?" + query);

        if (body == null) {
            return null;
        }

        JSONArray items = new JSONArray(body);
        Lyrics bestPlain = null;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            if (durationSec > 0 && Math.abs(item.optLong("duration", 0) - durationSec) > MAX_DURATION_DIFF_SEC) {
                continue;
            }

            Lyrics lyrics = toLyrics(item);

            if (lyrics == null) {
                continue;
            }

            if (lyrics.isSynced()) {
                return lyrics;
            }

            if (bestPlain == null) {
                bestPlain = lyrics;
            }
        }

        return bestPlain;
    }

    @Nullable
    private static Lyrics toLyrics(JSONObject item) {
        if (item.optBoolean("instrumental", false)) {
            return null;
        }

        String track = item.optString("trackName");
        String artist = item.optString("artistName");
        Lyrics synced = item.isNull("syncedLyrics") ? null : Lyrics.fromLrc(track, artist, item.optString("syncedLyrics"));

        if (isComplete(synced)) {
            return synced;
        }

        Lyrics plain = item.isNull("plainLyrics") ? null : Lyrics.fromPlain(track, artist, item.optString("plainLyrics"));

        return isComplete(plain) ? plain : null;
    }

    /**
     * LRCLIB has junk uploads, e.g. a lone "[00:00.00]probe" line. Treat such entries as missing so the lookup goes on.
     */
    private static boolean isComplete(@Nullable Lyrics lyrics) {
        if (lyrics == null) {
            return false;
        }

        int textLines = 0;

        for (Lyrics.Line line : lyrics.getLines()) {
            if (!line.text.isEmpty()) {
                textLines++;
            }
        }

        return textLines >= MIN_TEXT_LINES;
    }

    @Nullable
    private static String request(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);

        try {
            int code = connection.getResponseCode();

            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }

            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("LRCLIB responded with " + code);
            }

            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
