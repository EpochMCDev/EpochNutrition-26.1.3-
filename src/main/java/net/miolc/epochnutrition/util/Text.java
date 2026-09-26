package net.miolc.epochnutrition.util;

import org.bukkit.ChatColor;

import java.util.Locale;

public final class Text {

    private Text() {
    }

    /** 翻译 & 颜色代码。 */
    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /** 数值显示：整数不带小数，小数最多保留两位并去掉末尾 0。 */
    public static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        String s = String.format(Locale.US, "%.2f", v);
        if (s.endsWith("00")) s = s.substring(0, s.length() - 3);
        else if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 按指定小数位格式化（Tab 显示用）。 */
    public static String fmt(double v, int decimals) {
        int d = Math.max(0, Math.min(2, decimals));
        return String.format(Locale.US, "%." + d + "f", v);
    }
}
