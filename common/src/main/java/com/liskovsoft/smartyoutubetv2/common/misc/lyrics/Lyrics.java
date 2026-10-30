package com.liskovsoft.smartyoutubetv2.common.misc.lyrics;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Song lyrics. Synced lyrics carry a start time per line (LRC format), plain lyrics don't.
 */
public class Lyrics {
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private final String mTrackName;
    private final String mArtistName;
    private final List<Line> mLines;
    private final boolean mIsSynced;

    public static class Line {
        public final long startMs;
        public final String text;

        public Line(long startMs, String text) {
            this.startMs = startMs;
            this.text = text;
        }
    }

    private Lyrics(String trackName, String artistName, List<Line> lines, boolean isSynced) {
        mTrackName = trackName;
        mArtistName = artistName;
        mLines = lines;
        mIsSynced = isSynced;
    }

    @Nullable
    public static Lyrics fromLrc(String trackName, String artistName, String lrc) {
        if (lrc == null) {
            return null;
        }

        List<Line> lines = new ArrayList<>();

        for (String row : lrc.split("\n")) {
            Matcher matcher = LRC_TIME.matcher(row);
            List<Long> times = new ArrayList<>();
            int textStart = 0;

            // A row may hold several time tags: [00:12.00][01:30.00]Chorus
            while (matcher.find() && matcher.start() == textStart) {
                long min = Long.parseLong(matcher.group(1));
                long sec = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long ms = 0;
                if (fraction != null) {
                    // .5 = 500ms, .45 = 450ms, .456 = 456ms
                    ms = Long.parseLong((fraction + "00").substring(0, 3));
                }
                times.add(min * 60_000 + sec * 1_000 + ms);
                textStart = matcher.end();
            }

            String text = row.substring(textStart).trim();

            for (long time : times) {
                lines.add(new Line(time, text));
            }
        }

        if (lines.isEmpty()) {
            return null;
        }

        Collections.sort(lines, (a, b) -> Long.compare(a.startMs, b.startMs));

        return new Lyrics(trackName, artistName, lines, true);
    }

    @Nullable
    public static Lyrics fromPlain(String trackName, String artistName, String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return null;
        }

        List<Line> lines = new ArrayList<>();

        for (String row : plain.split("\n")) {
            lines.add(new Line(-1, row.trim()));
        }

        return new Lyrics(trackName, artistName, lines, false);
    }

    public String getTrackName() {
        return mTrackName;
    }

    public String getArtistName() {
        return mArtistName;
    }

    public List<Line> getLines() {
        return mLines;
    }

    public boolean isSynced() {
        return mIsSynced;
    }

    /**
     * Start of the first sung line or -1 for plain lyrics
     */
    public long getFirstLineStartMs() {
        if (!mIsSynced) {
            return -1;
        }

        for (Line line : mLines) {
            if (!line.text.isEmpty()) {
                return line.startMs;
            }
        }

        return -1;
    }

    /**
     * Index of the line that is sung at the given position or -1 if the song hasn't started yet.
     */
    public int findLineIndex(long positionMs) {
        if (!mIsSynced) {
            return -1;
        }

        int low = 0;
        int high = mLines.size() - 1;
        int result = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (mLines.get(mid).startMs <= positionMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return result;
    }
}
