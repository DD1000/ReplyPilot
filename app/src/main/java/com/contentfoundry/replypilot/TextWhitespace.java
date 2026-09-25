package com.contentfoundry.replypilot;

import java.util.regex.Pattern;

/** Unicode White_Space expressed without the desktop-only inline U regex flag. */
final class TextWhitespace {
    // Android's ICU and the desktop JDK both support these explicit categories.
    static final String CHARACTER_CLASS="[\\p{Z}\\x09-\\x0d\\u0085]";
    static final Pattern RUN=Pattern.compile(CHARACTER_CLASS+"+");
}
