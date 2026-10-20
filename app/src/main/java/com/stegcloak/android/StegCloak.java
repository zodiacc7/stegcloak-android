package com.stegcloak.android;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * Faithful Java port of StegCloak v1.1.1 (github.com/KuroLabs/stegcloak).
 * Produces / consumes concealed texts fully interchangeable with the original
 * Node.js library, CLI and web app (stegcloak.surge.sh).
 *
 * Zero-width characters used (in index order):
 *   U+200C ZWNJ, U+200D ZWJ, U+2061 FUNCTION APPLICATION, U+2062 INVISIBLE TIMES,
 *   U+2063 INVISIBLE SEPARATOR, U+2064 INVISIBLE PLUS
 */
public final class StegCloak {
    static final char[] ZWC = {'‌', '‍', '⁡', '⁢', '⁣', '⁤'};

    private static final Random RANDOM = new Random();

    private StegCloak() {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Hides {@code message} inside {@code cover}.
     *
     * @param encrypt   encrypt with password (AES-256-CTR + PBKDF2) — like `hide` without --nocrypt
     * @param integrity add HMAC-SHA256 integrity check — like the --integrity / -i flag
     * @param password  may be empty (never null)
     * @param cover     must contain at least two words
     */
    public static String hide(String message, String password, String cover,
                              boolean encrypt, boolean integrity) throws Exception {
        if (message == null || message.isEmpty()) throw new Exception("Secret message is empty");
        if (password == null) password = "";
        if (cover == null || cover.isEmpty()) cover = "This is a confidential text";
        if (cover.split(" ", -1).length == 1) throw new Exception("Minimum two words required");

        byte[] compressed = Lzutf8.compress(message.getBytes(StandardCharsets.UTF_8));
        byte[] secret = compliment(compressed);
        byte[] payload = encrypt
                ? StegCrypto.encrypt(secret, password.getBytes(StandardCharsets.UTF_8), integrity)
                : secret;

        char flag = (integrity && encrypt) ? ZWC[0] : encrypt ? ZWC[1] : ZWC[2];
        String concealed = dataToZwc(flag, payload);
        String invisibleStream = shrink(concealed);
        return embed(cover, invisibleStream);
    }

    /**
     * Reveals the message hidden inside a concealed text.
     * Encryption/HMAC usage is auto-detected from flag characters.
     */
    public static String reveal(String concealed, String password) throws Exception {
        if (password == null) password = "";
        if (concealed == null) throw new Exception("No concealed text given");

        String detached = detach(concealed);
        String expanded = expand(detached);
        Flags flags = flagOf(expanded.charAt(0));
        byte[] data = zwcToData(expanded.substring(1));

        byte[] stream = flags.encrypt
                ? StegCrypto.decrypt(data, password.getBytes(StandardCharsets.UTF_8), flags.integrity)
                : data;

        byte[] decompressed = Lzutf8.decompress(compliment(stream));
        return new String(decompressed, StandardCharsets.UTF_8);
    }

    /** True if the string contains any StegCloak zero-width stream. */
    public static boolean containsHiddenData(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (indexOfZwc(text.charAt(i)) >= 0) return true;
        }
        return false;
    }

    /** Counts StegCloak zero-width characters in the string (UI helper). */
    public static int zeroWidthCount(String text) {
        if (text == null) return 0;
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (indexOfZwc(text.charAt(i)) >= 0) n++;
        return n;
    }

    /** True if the hidden stream (if any) is password-encrypted (UI helper). */
    public static boolean isEncrypted(String concealed) {
        try {
            return flagOf(expand(detach(concealed)).charAt(0)).encrypt;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // compliment (util.js): bytewise NOT
    // ------------------------------------------------------------------
    private static byte[] compliment(byte[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) out[i] = (byte) ~in[i];
        return out;
    }

    // ------------------------------------------------------------------
    // message.js: data <-> ZWC stream conversions
    // ------------------------------------------------------------------
    /** flag + 2 bits per ZWC char (byteToBin + toConceal in one pass). */
    private static String dataToZwc(char flag, byte[] data) {
        StringBuilder sb = new StringBuilder(1 + data.length * 4);
        sb.append(flag);
        for (byte b : data) {
            int v = b & 0xFF;
            sb.append(ZWC[(v >>> 6) & 3]);
            sb.append(ZWC[(v >>> 4) & 3]);
            sb.append(ZWC[(v >>> 2) & 3]);
            sb.append(ZWC[v & 3]);
        }
        return sb.toString();
    }

    private static byte[] zwcToData(String s) throws Exception {
        int bits = s.length() * 2;
        if (bits % 8 != 0) throw new Exception("Malformed concealed stream");
        byte[] out = new byte[bits / 8];
        for (int i = 0; i < s.length(); i++) {
            int idx = indexOfZwc(s.charAt(i));
            if (idx < 0) throw new Exception("Malformed concealed stream");
            int byteIndex = (i * 2) / 8;
            int shift = 6 - ((i * 2) % 8);
            out[byteIndex] |= (byte) (idx << shift);
        }
        return out;
    }

    private static int indexOfZwc(char c) {
        for (int i = 0; i < ZWC.length; i++) if (ZWC[i] == c) return i;
        return -1;
    }

    private static final class Flags {
        final boolean encrypt, integrity;
        Flags(boolean e, boolean i) { encrypt = e; integrity = i; }
    }

    private static Flags flagOf(char c) throws Exception {
        int i = indexOfZwc(c);
        if (i == 0) return new Flags(true, true);
        if (i == 1) return new Flags(true, false);
        if (i == 2) return new Flags(false, false);
        throw new Exception("Malformed concealed stream (bad flag)");
    }

    // ------------------------------------------------------------------
    // message.js: detach + embed
    // ------------------------------------------------------------------
    private static String detach(String str) throws Exception {
        String[] words = str.split(" ", -1);
        String detached = "";
        for (String word : words) {
            boolean hasZwc = false;
            for (int i = 0; i < word.length(); i++) {
                if (indexOfZwc(word.charAt(i)) >= 0) { hasZwc = true; break; }
            }
            if (hasZwc) {
                int limit = -1; // JS findIndex returns -1 when nothing found
                for (int i = 0; i < word.length(); i++) {
                    if (indexOfZwc(word.charAt(i)) < 0) { limit = i; break; }
                }
                detached = (limit == -1)
                        ? word.substring(0, word.length() - 1) // JS slice(0, -1)
                        : word.substring(0, limit);
            }
        }
        if (detached.isEmpty()) {
            throw new Exception("Invisible stream not detected! Please copy and paste the StegCloak text sent by the sender.");
        }
        return detached;
    }

    private static String embed(String cover, String secret) {
        String[] arr = cover.split(" ", -1);
        int targetIndex = (int) Math.floor(RANDOM.nextDouble() * Math.floor(arr.length / 2.0));
        StringBuilder sb = new StringBuilder(cover.length() + secret.length());
        for (int i = 0; i <= targetIndex; i++) sb.append(arr[i]).append(' ');
        if (targetIndex + 1 < arr.length) {
            sb.append(secret).append(arr[targetIndex + 1]);
            for (int i = targetIndex + 2; i < arr.length; i++) sb.append(' ').append(arr[i]);
        } else {
            // cover ended with space(s): secret lands on the trailing empty word
            sb.append(secret);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // compact.js: huffman-like shrink/expand over the ZWC alphabet
    // ------------------------------------------------------------------
    private static final String[] PAIRS = {
            "" + ZWC[0] + ZWC[1], "" + ZWC[0] + ZWC[2], "" + ZWC[0] + ZWC[3],
            "" + ZWC[1] + ZWC[2], "" + ZWC[1] + ZWC[3], "" + ZWC[2] + ZWC[3]
    };

    /** findOptimal from compact.js — picks the two ZWCs whose doubled runs save the most chars. */
    private static char[] findOptimal(String secret) {
        // scores per char per run-length, in JS iteration order: chars z0..z3, counts ascending
        @SuppressWarnings("unchecked")
        TreeMap<Integer, Integer>[] dict = new TreeMap[4];
        for (int i = 0; i < 4; i++) dict[i] = new TreeMap<>();
        int size = secret.length();
        for (int j = 0; j < size; j++) {
            int count = 1;
            while (j < size && secret.charAt(j) == charAtOrZero(secret, j + 1)) {
                count++;
                j++;
            }
            if (count >= 2) {
                int charIndex = indexOfZwc(secret.charAt(j));
                if (charIndex < 0 || charIndex > 3) continue;
                TreeMap<Integer, Integer> inner = dict[charIndex];
                for (int itr = count; itr >= 2; itr--) {
                    int gain = (count / itr) * (itr - 1);
                    Integer prev = inner.get(itr);
                    inner.put(itr, (prev == null ? 0 : prev) + gain);
                }
            }
        }
        // build ranked table (stable sort by score, descending)
        List<long[]> entries = new ArrayList<>(); // packs [charIndex, count, score]
        for (int ci = 0; ci < 4; ci++) {
            for (java.util.Map.Entry<Integer, Integer> entry : dict[ci].entrySet()) {
                entries.add(new long[]{ci, entry.getKey(), entry.getValue()});
            }
        }
        entries.sort((a, b) -> Long.compare(b[2], a[2])); // List.sort is stable
        List<Character> chosen = new ArrayList<>();
        for (long[] e : entries) {
            if (e[1] == 2) { // count == "2"
                chosen.add(ZWC[(int) e[0]]);
                if (chosen.size() == 2) break;
            }
        }
        if (chosen.size() < 2) {
            for (int i = 0; i < 4 && chosen.size() < 2; i++) {
                if (!chosen.contains(ZWC[i])) chosen.add(ZWC[i]);
            }
        }
        // JS .sort() on the two chars (ascending code units)
        char a = chosen.get(0), b = chosen.get(1);
        return a <= b ? new char[]{a, b} : new char[]{b, a};
    }

    private static char charAtOrZero(String s, int i) {
        return i < s.length() ? s.charAt(i) : ' ';
    }

    private static String shrink(String secret) {
        char[] opt = findOptimal(secret);
        // _getCompressFlag(c1, c2)
        int flagIndex = -1;
        String key = "" + opt[0] + opt[1];
        for (int i = 0; i < PAIRS.length; i++) if (PAIRS[i].equals(key)) { flagIndex = i; break; }
        // recursiveReplace replaces LAST pattern first: c2c2 -> ZWC[5], then c1c1 -> ZWC[4]
        String body = secret.replace("" + opt[1] + opt[1], "" + ZWC[5]);
        body = body.replace("" + opt[0] + opt[0], "" + ZWC[4]);
        return ZWC[flagIndex] + body;
    }

    private static String expand(String secret) throws Exception {
        char flag = secret.charAt(0);
        int flagIndex = indexOfZwc(flag);
        if (flagIndex < 0 || flagIndex > 5) throw new Exception("Malformed concealed stream (bad huffman flag)");
        String pair = PAIRS[flagIndex];
        String body = secret.substring(1);
        // recursiveReplace: LAST pattern first -> ZWC[5] to (pair[1] x2), then ZWC[4] to (pair[0] x2)
        body = body.replace("" + ZWC[5], "" + pair.charAt(1) + pair.charAt(1));
        body = body.replace("" + ZWC[4], "" + pair.charAt(0) + pair.charAt(0));
        return body;
    }

    // ------------------------------------------------------------------
    // debug helpers
    // ------------------------------------------------------------------
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    static String describe(String text) {
        int zwc = 0;
        for (int i = 0; i < text.length(); i++) if (indexOfZwc(text.charAt(i)) >= 0) zwc++;
        return "chars=" + text.length() + " zwc=" + zwc;
    }
}
