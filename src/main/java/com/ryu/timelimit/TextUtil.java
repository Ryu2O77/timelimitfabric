package com.ryu.timelimit;

import net.minecraft.network.chat.Component;

public final class TextUtil {
    public static String colorizeRaw(String s) {
        if (s == null) return "";
        return s.replace('&', '§');
    }
    public static Component color(String s) {
        return Component.literal(colorizeRaw(s));
    }
    public static String formatTimeHMS(int seconds) {
        int h = seconds / 3600;
        int rem = seconds % 3600;
        int m = rem / 60;
        int s = rem % 60;
        return h + "h " + m + "m " + s + "s";
    }
    public static String applyPlaceholders(String template, int remainingSec) {
        int h = remainingSec / 3600;
        int rem = remainingSec % 3600;
        int m = rem / 60;
        int s = rem % 60;
        String t = template == null ? "" : template;
        t = t.replace("%time%", formatTimeHMS(remainingSec));
        t = t.replace("%hours%", String.valueOf(h));
        t = t.replace("%minutes%", String.valueOf(m));
        t = t.replace("%seconds%", String.valueOf(s));
        return t;
    }
}