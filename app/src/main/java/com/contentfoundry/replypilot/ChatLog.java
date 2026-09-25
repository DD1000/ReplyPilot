package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure text parsing; imported logs never write to SMS storage or contact a service. */
public final class ChatLog {
    public static final int MAX_RAW_CHARS = 262_144;
    public static final int MAX_SAMPLES_CHARS = 8_000;
    public static final int MAX_TURNS = 50;

    public record Turn(String speaker, String text) {}
    public record Result(String samples, int messageCount, int ownerCount,
                         int incomingCount, boolean truncated) {}

    private record NamedTurn(String speaker, String text) {}
    private record Canonical(List<Turn> turns, boolean owner, boolean incoming) {}
    private static final Pattern LABEL = Pattern.compile("^(Me|You|Them):[ \\t]*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED = Pattern.compile("^([^:\\r\\n]{1,100}):[ \\t]*(.*)$");
    private static final Pattern SPEAKER_LIKE = Pattern.compile("^([\\p{L}\\p{N}+][\\p{L}\\p{N} ._'()+-]{0,79}):.*$");
    private static final Pattern EMBEDDED_LABEL = Pattern.compile("(?i)^.+\\s(?:me|you|them):.*$");
    private static final String DATE = "[0-9]{1,4}[-/.][0-9]{1,2}[-/.][0-9]{1,4}";
    private static final String TIME = "[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:[ \\t\\u00a0\\u202f]{0,4}[AaPp][Mm])?";
    // Export formatting uses only a few spaces. Bounded separators avoid costly
    // regex backtracking when a malformed file contains enormous whitespace runs.
    private static final String SPACE = "[ \\t\\u00a0\\u202f]{0,8}";
    private static final String DATE_TIME_SEPARATOR = "(?:," + SPACE + "|[ \\t\\u00a0\\u202f]{1,8})";
    private static final Pattern BRACKET = Pattern.compile("^\\[" + SPACE + DATE + DATE_TIME_SEPARATOR + TIME + SPACE + "\\]" + SPACE + "(.*)$");
    private static final Pattern DASH = Pattern.compile("^" + DATE + DATE_TIME_SEPARATOR + TIME + SPACE + "-" + SPACE + "(.*)$");

    private ChatLog() {}

    /** Imports a two-person log. Named exports require an explicitly selected owner. */
    public static Result parse(String raw, String ownerLabel) {
        String input = cleanInput(raw, MAX_RAW_CHARS);
        String first = firstNonempty(input);
        List<Turn> turns;
        if (LABEL.matcher(first).matches()) {
            Canonical parsed = canonical(input, false);
            if (!parsed.owner || !parsed.incoming) {
                throw new IllegalArgumentException("Include messages from both Me (or You) and Them.");
            }
            if (ownerLabel != null && key(ownerLabel).equals("them")) {
                throw new IllegalArgumentException("Them identifies the other person. Use Me or You for your messages.");
            }
            turns = parsed.turns;
        } else if (datedPayload(first) != null) {
            turns = whatsapp(input, ownerLabel);
        } else {
            throw new IllegalArgumentException("Use a two-person WhatsApp text export, or label messages Me: and Them:.");
        }
        return bounded(turns);
    }

    /**
     * Reads only explicitly labeled sample text for local readiness checks. A
     * one-sided sample is useful alongside real SMS; malformed/freeform samples
     * return no turns. Continuations produced by this class are indented so a
     * quoted Me: line cannot acquire the owner's voice when parsed again.
     */
    public static List<Turn> parseCanonical(String existingSamples) {
        if (existingSamples == null || existingSamples.trim().isEmpty()
                || existingSamples.length() > MAX_SAMPLES_CHARS) return Collections.emptyList();
        try {
            return Collections.unmodifiableList(new ArrayList<>(canonical(
                    cleanInput(existingSamples, MAX_SAMPLES_CHARS), true).turns));
        } catch (IllegalArgumentException ignored) {
            return Collections.emptyList();
        }
    }

    private static Canonical canonical(String input, boolean decodeIndent) {
        List<Turn> turns = new ArrayList<>();
        String speaker = null;
        StringBuilder text = new StringBuilder();
        Set<String> selfLabels = new HashSet<>();
        boolean owner = false, incoming = false;
        for (String line : input.split("\n", -1)) {
            Matcher match = LABEL.matcher(line);
            if (match.matches()) {
                if (speaker != null) turns.add(new Turn(speaker, message(text)));
                String label = match.group(1).toLowerCase(Locale.ROOT);
                if (label.equals("them")) { speaker = "Them"; incoming = true; }
                else {
                    selfLabels.add(label);
                    if (selfLabels.size() > 1) {
                        throw new IllegalArgumentException("Use one label for your messages: Me or You, not both.");
                    }
                    speaker = "Me"; owner = true;
                }
                text = new StringBuilder(match.group(2));
            } else {
                if (speaker == null) {
                    if (line.trim().isEmpty()) continue;
                    throw new IllegalArgumentException("Every chat log must begin with a recognized speaker label.");
                }
                if (unrecognizedSpeaker(line)) {
                    throw new IllegalArgumentException("Only two labeled speakers are supported. Use Me (or You) and Them.");
                }
                appendLine(text, decodeIndent && line.startsWith("  ") ? line.substring(2) : line);
            }
        }
        if (speaker != null) turns.add(new Turn(speaker, message(text)));
        if (turns.isEmpty()) throw new IllegalArgumentException("No labeled messages were found.");
        return new Canonical(turns, owner, incoming);
    }

    private static boolean unrecognizedSpeaker(String line) {
        // Indentation explicitly marks quoted/continued text, never a new speaker.
        if (line.startsWith(" ") || line.startsWith("\t") || EMBEDDED_LABEL.matcher(line).matches()) return false;
        Matcher match = SPEAKER_LIKE.matcher(line);
        if (!match.matches()) return false;
        String label = match.group(1).toLowerCase(Locale.ROOT);
        return !label.equals("http") && !label.equals("https") && !label.equals("mailto");
    }

    private static List<Turn> whatsapp(String input, String ownerLabel) {
        String owner = key(ownerLabel == null ? "" : ownerLabel);
        if (owner.isEmpty()) throw new IllegalArgumentException("Enter your name exactly as it appears in the exported chat.");
        List<NamedTurn> parsed = new ArrayList<>();
        Set<String> speakers = new HashSet<>();
        String speaker = null;
        StringBuilder text = new StringBuilder();
        for (String line : input.split("\n", -1)) {
            String payload = datedPayload(line);
            if (payload != null) {
                Matcher match = NAMED.matcher(payload);
                if (!match.matches()) {
                    if (encryptionNotice(payload) && speaker == null && parsed.isEmpty()) continue;
                    throw new IllegalArgumentException("This export contains an unsupported event or an unlabeled message.");
                }
                if (speaker != null) parsed.add(new NamedTurn(speaker, message(text)));
                speaker = key(match.group(1));
                if (speaker.isEmpty()) throw new IllegalArgumentException("A message has no speaker name.");
                speakers.add(speaker);
                if (speakers.size() > 2) throw new IllegalArgumentException("Group chats are not supported. Choose a chat with exactly two people.");
                text = new StringBuilder(match.group(2));
            } else {
                if (speaker == null) {
                    if (line.trim().isEmpty()) continue;
                    throw new IllegalArgumentException("The export contains text before its first recognized message.");
                }
                appendLine(text, line);
            }
        }
        if (speaker != null) parsed.add(new NamedTurn(speaker, message(text)));
        if (speakers.size() != 2) throw new IllegalArgumentException("Choose a chat containing messages from exactly two people.");
        if (!speakers.contains(owner)) throw new IllegalArgumentException("Your name does not match either speaker in this export. Check the spelling.");
        List<Turn> turns = new ArrayList<>();
        for (NamedTurn turn : parsed) turns.add(new Turn(turn.speaker.equals(owner) ? "Me" : "Them", turn.text));
        return turns;
    }

    private static String datedPayload(String line) {
        String header = stripDirectionMarks(line);
        Matcher bracket = BRACKET.matcher(header);
        if (bracket.matches()) return originalPayload(line, bracket.start(1));
        Matcher dash = DASH.matcher(header);
        return dash.matches() ? originalPayload(line, dash.start(1)) : null;
    }

    private static String originalPayload(String line, int plainOffset) {
        // Ignore export formatting marks in the header without deleting marks
        // that belong to the user's actual message body.
        int offset = 0, plain = 0;
        while (offset < line.length() && plain < plainOffset) {
            if (!directionMark(line.charAt(offset))) plain++;
            offset++;
        }
        return line.substring(offset);
    }

    private static boolean encryptionNotice(String payload) {
        String notice = payload.toLowerCase(Locale.ROOT);
        return notice.startsWith("messages and calls are end-to-end encrypted.")
                || notice.startsWith("messages to this chat and calls are now secured with end-to-end encryption.");
    }

    private static Result bounded(List<Turn> parsed) {
        List<Turn> retained = new ArrayList<>();
        int length = 0;
        boolean truncated = false;
        for (int i = parsed.size() - 1; i >= 0; i--) {
            Turn turn = parsed.get(i);
            String encoded = encode(turn);
            if (encoded.length() > MAX_SAMPLES_CHARS) { truncated = true; continue; }
            int addition = encoded.length() + (retained.isEmpty() ? 0 : 1);
            if (retained.size() == MAX_TURNS || length + addition > MAX_SAMPLES_CHARS) {
                truncated = true; break;
            }
            retained.add(turn); length += addition;
        }
        if (retained.isEmpty()) throw new IllegalArgumentException("No complete message fits the 8,000-character sample limit. Import a shorter section.");
        Collections.reverse(retained);
        StringBuilder samples = new StringBuilder();
        int owner = 0;
        for (Turn turn : retained) {
            if (samples.length() != 0) samples.append('\n');
            samples.append(encode(turn));
            if (turn.speaker.equals("Me")) owner++;
        }
        return new Result(samples.toString(), retained.size(), owner,
                retained.size() - owner, truncated || retained.size() != parsed.size());
    }

    private static String encode(Turn turn) {
        return turn.speaker + ": " + turn.text.replace("\n", "\n  ");
    }

    private static String cleanInput(String raw, int limit) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Paste or choose a text chat log first.");
        if (raw.length() > limit) throw new IllegalArgumentException("This chat log is too large. Choose a text file no larger than 256 KB.");
        if (raw.indexOf('\0') >= 0) throw new IllegalArgumentException("Choose a plain-text chat log.");
        String input = raw.replace("\r\n", "\n").replace('\r', '\n');
        return input.startsWith("\uFEFF") ? input.substring(1) : input;
    }

    private static String firstNonempty(String input) {
        for (String line : input.split("\n", -1)) if (!line.trim().isEmpty()) return line;
        return "";
    }

    private static void appendLine(StringBuilder text, String line) { text.append('\n').append(line); }

    private static String message(StringBuilder text) {
        String value = text.toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("A labeled message is empty. Remove empty message labels and try again.");
        return value;
    }

    private static String key(String label) {
        return Normalizer.normalize(stripDirectionMarks(label).trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String stripDirectionMarks(String value) {
        return value.replace("\u200E", "").replace("\u200F", "")
                .replace("\u202A", "").replace("\u202B", "").replace("\u202C", "")
                .replace("\u202D", "").replace("\u202E", "")
                .replace("\u2066", "").replace("\u2067", "").replace("\u2068", "").replace("\u2069", "");
    }

    private static boolean directionMark(char c) {
        return c == '\u200E' || c == '\u200F' || c >= '\u202A' && c <= '\u202E'
                || c >= '\u2066' && c <= '\u2069';
    }
}
