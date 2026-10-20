package com.stegcloak.android;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Desktop test harness: verifies the port against the original Node.js implementation. */
public class CliTest {
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "hide": {
                String secret = read(a[1]);
                String cover = read(a[2]);
                String pw = read(a[3]);
                boolean enc = a[4].equals("1");
                boolean integ = a[5].equals("1");
                String out = StegCloak.hide(secret, pw, cover, enc, integ);
                Files.write(Paths.get(a[6]), out.getBytes(StandardCharsets.UTF_8));
                System.out.println(StegCloak.describe(out));
                break;
            }
            case "reveal": {
                String text = read(a[1]);
                String pw = read(a[2]);
                String msg = StegCloak.reveal(text, pw);
                Files.write(Paths.get(a[3]), msg.getBytes(StandardCharsets.UTF_8));
                break;
            }
            case "pbkdf2": {
                byte[] pw = read(a[1]).getBytes(StandardCharsets.UTF_8);
                byte[] salt = new byte[8];
                for (int i = 0; i < 8; i++) salt[i] = (byte) (i + 1);
                System.out.println(StegCloak.hex(StegCrypto.pbkdf2Sha512(pw, salt, 10000, 48)));
                break;
            }
            default:
                throw new IllegalArgumentException("unknown command");
        }
    }

    static String read(String p) throws Exception {
        return new String(Files.readAllBytes(Paths.get(p)), StandardCharsets.UTF_8);
    }
}
