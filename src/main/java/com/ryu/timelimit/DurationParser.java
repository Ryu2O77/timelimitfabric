package com.ryu.timelimit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(?i)(\\d+)\\s*(h|m|s)?");

    // Acepta: "600", "10m", "15s", "1h", "1h30m", "1h 30m 15s", "10 min"
    // Sin sufijo => segundos
    public static int parseSeconds(String spec) {
        if (spec == null) throw new IllegalArgumentException("Duración vacía");
        String input = spec.trim();
        if (input.isEmpty()) throw new IllegalArgumentException("Duración vacía");

        // Normalización de palabras comunes
        input = input.replaceAll("(?i)\\bmins?\\b", "m")
                .replaceAll("(?i)\\bmin\\b", "m")
                .replaceAll("(?i)\\bseconds?\\b", "s")
                .replaceAll("(?i)\\bsecs?\\b", "s")
                .replaceAll("(?i)\\bsegs?\\b", "s")
                .replaceAll("(?i)\\bseg\\b", "s")
                .replaceAll("(?i)\\bhoras?\\b", "h")
                .replaceAll("(?i)\\bh\\b", "h");

        Matcher m = TOKEN.matcher(input);
        int total = 0;
        int consumed = 0;

        while (m.find()) {
            String numStr = m.group(1);
            String unit = m.group(2);
            int v = Integer.parseInt(numStr);
            if (unit == null) {
                total += v; // sin sufijo => segundos
                consumed += numStr.length();
            } else {
                switch (unit.toLowerCase()) {
                    case "h": total += v * 3600; consumed += numStr.length() + 1; break;
                    case "m": total += v * 60;   consumed += numStr.length() + 1; break;
                    case "s": total += v;        consumed += numStr.length() + 1; break;
                    default: throw new IllegalArgumentException("Unidad inválida: " + unit);
                }
            }
        }

        // Verifica consumo total ignorando espacios
        if (consumed != input.replace(" ", "").length()) {
            throw new IllegalArgumentException("Formato de duración inválido: " + spec);
        }
        return Math.max(0, total);
    }
}