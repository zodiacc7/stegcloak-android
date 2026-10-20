package com.stegcloak.android;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Java port of LZUTF8 v0.5.5 (lzutf8.js by Rotem Dan) block compressor/decompressor.
 * 100% stream-format compatible with the original library:
 *  - decompress() handles every token type produced by the original compressor.
 *  - compress() emits standard tokens (raw bytes + LZ77 back-pointers) readable by the
 *    original lzutf8.js decompressor.
 *
 * Format summary (from the reference implementation):
 *   byte >= 0xC0 starts a pointer token:
 *     tokenLowest5Bits      = copy length (4..31)
 *     (byte >>> 5) == 6  -> distance is 1 following byte          (dist < 128)
 *     (byte >>> 5) == 7  -> distance is 2 following bytes, big endian
 *     if the NEXT byte has its top bit set (UTF-8 continuation), the byte is a raw literal
 *     (this works because the input is always valid UTF-8).
 *   anything else is a raw literal byte.
 */
final class Lzutf8 {
    private static final int MINIMUM_SEQUENCE_LENGTH = 4;
    private static final int MAXIMUM_SEQUENCE_LENGTH = 31;
    private static final int MAXIMUM_MATCH_DISTANCE = 32767;
    private static final int PREFIX_HASH_TABLE_SIZE = 65537;
    private static final int BUCKET_LIMIT = 64; // mirrors maximumBucketCapacity of the custom hash table

    private Lzutf8() {}

    // ------------------------------------------------------------------
    // Decompression (faithful single-shot port of Decompressor.decompressBlock)
    // ------------------------------------------------------------------
    static byte[] decompress(byte[] input) {
        byte[] out = new byte[Math.max(input.length * 4, 1024)];
        int outPos = 0;
        int r = 0;
        final int n = input.length;
        while (r < n) {
            int v = input[r] & 0xFF;
            if ((v >>> 6) != 3) {
                out = grow(out, outPos);
                out[outPos++] = (byte) v;
                r++;
                continue;
            }
            int seqId = v >>> 5;
            // Streaming remainder handling; for a complete single-shot stream we just stop.
            if (r == n - 1 || (r == n - 2 && seqId == 7)) break;
            int next = input[r + 1] & 0xFF;
            if ((next >>> 7) == 1) {
                out = grow(out, outPos);
                out[outPos++] = (byte) v;
                r++;
            } else {
                int length = v & 31;
                int distance;
                if (seqId == 6) {
                    distance = next;
                    r += 2;
                } else {
                    distance = ((next << 8) | (input[r + 2] & 0xFF));
                    r += 3;
                }
                int matchPosition = outPos - distance;
                for (int o = 0; o < length; o++) {
                    out = grow(out, outPos);
                    out[outPos++] = out[matchPosition + o];
                }
            }
        }
        // rollBackIfOutputBufferEndsWithATruncatedMultibyteSequence (no-op for well-formed streams)
        for (int offset = 1; offset <= 4 && outPos - offset >= 0; offset++) {
            int value = out[outPos - offset] & 0xFF;
            boolean truncated = (offset < 4 && (value >>> 3) == 30)
                    || (offset < 3 && (value >>> 4) == 14)
                    || (offset < 2 && (value >>> 5) == 6);
            if (truncated) {
                outPos -= offset;
                break;
            }
        }
        return Arrays.copyOf(out, outPos);
    }

    private static byte[] grow(byte[] buf, int pos) {
        return pos >= buf.length ? Arrays.copyOf(buf, buf.length * 2) : buf;
    }

    // ------------------------------------------------------------------
    // Compression (port of Compressor: greedy longest match over 4-byte prefix buckets)
    // ------------------------------------------------------------------
    static byte[] compress(byte[] utf8) {
        if (utf8 == null || utf8.length == 0) return new byte[0];
        final int len = utf8.length;
        byte[] out = new byte[len]; // output is never larger than input
        int outPos = 0;
        @SuppressWarnings("unchecked")
        ArrayList<Integer>[] buckets = new ArrayList[PREFIX_HASH_TABLE_SIZE];

        int latestMatchEndPosition = 0;
        for (int p = 0; p < len; p++) {
            boolean withinAMatchedRange = p < latestMatchEndPosition;
            int bucketIndex = -1;
            if (p > len - MINIMUM_SEQUENCE_LENGTH) {
                if (!withinAMatchedRange) out[outPos++] = utf8[p];
                continue;
            }
            bucketIndex = bucketIndexForPrefix(utf8, p);
            if (!withinAMatchedRange) {
                ArrayList<Integer> bucket = buckets[bucketIndex];
                if (bucket != null) {
                    int longestMatchLength = 0;
                    int longestMatchDistance = -1;
                    boolean returnNow = false;
                    for (int i = bucket.size() - 1; i >= 0; i--) {
                        int testedSequencePosition = bucket.get(i);
                        int testedSequenceDistance = p - testedSequencePosition;
                        int lengthToSurpass;
                        if (longestMatchDistance == -1) lengthToSurpass = MINIMUM_SEQUENCE_LENGTH - 1;
                        else if (longestMatchDistance < 128 && testedSequenceDistance >= 128)
                            lengthToSurpass = longestMatchLength + (longestMatchLength >>> 1);
                        else lengthToSurpass = longestMatchLength;
                        if (testedSequenceDistance > MAXIMUM_MATCH_DISTANCE
                                || lengthToSurpass >= MAXIMUM_SEQUENCE_LENGTH
                                || p + lengthToSurpass >= len) break;
                        if ((utf8[testedSequencePosition + lengthToSurpass] & 0xFF)
                                != (utf8[p + lengthToSurpass] & 0xFF)) continue;
                        int offset = 0;
                        while (true) {
                            if (p + offset == len
                                    || (utf8[testedSequencePosition + offset] & 0xFF) != (utf8[p + offset] & 0xFF)) {
                                if (offset > lengthToSurpass) {
                                    longestMatchDistance = testedSequenceDistance;
                                    longestMatchLength = offset;
                                }
                                break;
                            } else if (offset == MAXIMUM_SEQUENCE_LENGTH) {
                                out = outputPointer(out, outPos, MAXIMUM_SEQUENCE_LENGTH, testedSequenceDistance);
                                outPos += (testedSequenceDistance < 128) ? 2 : 3;
                                latestMatchEndPosition = p + MAXIMUM_SEQUENCE_LENGTH;
                                returnNow = true;
                                break;
                            }
                            offset++;
                        }
                        if (returnNow) break;
                    }
                    if (returnNow) { /* fallthrough to bucket add below */ }
                    else if (longestMatchDistance != -1) {
                        out = outputPointer(out, outPos, longestMatchLength, longestMatchDistance);
                        outPos += (longestMatchDistance < 128) ? 2 : 3;
                        latestMatchEndPosition = p + longestMatchLength;
                        withinAMatchedRange = true;
                    }
                }
            }
            if (!withinAMatchedRange && latestMatchEndPosition <= p) {
                out[outPos++] = utf8[p];
            }
            if (latestMatchEndPosition > p) {
                // inside match; nothing emitted
            }
            ArrayList<Integer> b = buckets[bucketIndex];
            if (b == null) {
                b = new ArrayList<>(4);
                buckets[bucketIndex] = b;
            }
            b.add(p);
            if (b.size() > BUCKET_LIMIT) b.remove(0);
        }
        return Arrays.copyOf(out, outPos);
    }

    private static byte[] outputPointer(byte[] out, int pos, int length, int distance) {
        if (distance < 128) {
            out[pos] = (byte) (192 | length);
            out[pos + 1] = (byte) distance;
        } else {
            out[pos] = (byte) (224 | length);
            out[pos + 1] = (byte) (distance >>> 8);
            out[pos + 2] = (byte) (distance & 255);
        }
        return out;
    }

    private static int bucketIndexForPrefix(byte[] input, int p) {
        long v = (input[p] & 0xFF) * 7880599L
                + (input[p + 1] & 0xFF) * 39601L
                + (input[p + 2] & 0xFF) * 199L
                + (input[p + 3] & 0xFF);
        return (int) (v % PREFIX_HASH_TABLE_SIZE);
    }
}
