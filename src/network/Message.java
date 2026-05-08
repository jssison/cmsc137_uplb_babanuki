package network;

/**
 * Represents a message sent between the GameServer and GameClients.
 *
 * FORMAT (plain text, newline-terminated):
 *   TYPE|payload
 *
 * Examples:
 *   WELCOME|0                        -> server assigns you slot 0
 *   PLAYER_LIST|Alice,Bob,CPU 1      -> ordered list of all player names
 *   STATE|Alice:12:READY,Bob:8:COOLDOWN,CPU 1:5:SKIPPED  -> full state snapshot
 *   DRAW|1|2                         -> client requests: draw card index 2 from player slot 1
 *   TRAP_CHOICE|0                    -> client chooses player slot 0 as trap target
 *   TRAP_PROMPT|SINGKO|Bob,CPU 1     -> server asks human to pick a trap target
 *   GAME_OVER|Alice,Bob,CPU 1        -> ordered leaderboard (winner first, loser last)
 *   CHAT|Alice|hello!                -> optional chat message
 *   ERROR|message                    -> server-side error info
 */
public class Message {

    // ── Message Types ────────────────────────────────────────────────────────

    /** Server → Client: "You are player slot N" */
    public static final String WELCOME      = "WELCOME";

    /** Server → All: ordered comma-separated player names */
    public static final String PLAYER_LIST  = "PLAYER_LIST";

    /** Server → All: full game-state snapshot */
    public static final String STATE        = "STATE";

    /** Client → Server: draw card at <cardIndex> from player <targetSlot> */
    public static final String DRAW         = "DRAW";

    /** Server → specific Client: choose a trap target from the given list */
    public static final String TRAP_PROMPT  = "TRAP_PROMPT";

    /** Client → Server: I choose player slot <targetSlot> as trap target */
    public static final String TRAP_CHOICE  = "TRAP_CHOICE";

    /** Server → All: game finished, payload is the leaderboard */
    public static final String GAME_OVER    = "GAME_OVER";

    /** Either direction: chat text */
    public static final String CHAT         = "CHAT";

    /** Server → Client: something went wrong */
    public static final String ERROR        = "ERROR";

    // ── Log event broadcast ──────────────────────────────────────────────────

    /** Server → All: a game-log line to display in the EventLog */
    public static final String LOG          = "LOG";

    // ── Fields ───────────────────────────────────────────────────────────────

    public final String type;
    public final String[] parts; // everything after the type, split by '|'
    public final String raw;     // the full original string (useful for debugging)

    // ── Constructor ───────────────────────────────────────────────────────────

    private Message(String type, String[] parts, String raw) {
        this.type  = type;
        this.parts = parts;
        this.raw   = raw;
    }

    // ── Factory / Parsing ─────────────────────────────────────────────────────

    /**
     * Parse a raw line received from a socket.
     * Returns null if the line is blank or malformed.
     */
    public static Message parse(String line) {
        if (line == null || line.isBlank()) return null;
        String[] tokens = line.split("\\|", -1);
        String type     = tokens[0].trim();
        String[] parts  = new String[tokens.length - 1];
        System.arraycopy(tokens, 1, parts, 0, parts.length);
        return new Message(type, parts, line);
    }

    // ── Builder helpers (so callers don't hand-craft strings) ─────────────────

    public static String welcome(int slot) {
        return WELCOME + "|" + slot;
    }

    public static String playerList(String... names) {
        return PLAYER_LIST + "|" + String.join(",", names);
    }

    /**
     * State snapshot.
     * Each player entry: name:handSize:drawState[:isOut]
     * e.g.  Alice:12:READY,Bob:0:READY:OUT,CPU 1:5:COOLDOWN
     */
    public static String state(String encodedState) {
        return STATE + "|" + encodedState;
    }

    public static String draw(int targetSlot, int cardIndex) {
        return DRAW + "|" + targetSlot + "|" + cardIndex;
    }

    /**
     * Ask a specific human client to pick a trap target.
     * @param trapName  e.g. "SINGKO"
     * @param targetNames  comma-separated eligible target names
     */
    public static String trapPrompt(String trapName, String targetNames) {
        return TRAP_PROMPT + "|" + trapName + "|" + targetNames;
    }

    public static String trapChoice(int targetSlot) {
        return TRAP_CHOICE + "|" + targetSlot;
    }

    public static String gameOver(String leaderboardCsv) {
        return GAME_OVER + "|" + leaderboardCsv;
    }

    public static String chat(String senderName, String text) {
        return CHAT + "|" + senderName + "|" + text;
    }

    public static String log(String text) {
        return LOG + "|" + text;
    }

    public static String error(String reason) {
        return ERROR + "|" + reason;
    }

    // ── Convenience getters ───────────────────────────────────────────────────

    /** parts[0], or "" if missing */
    public String part(int i) {
        return (i < parts.length) ? parts[i] : "";
    }

    @Override
    public String toString() {
        return raw;
    }
}
