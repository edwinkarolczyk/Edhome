package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Pure presentation settings: every EDHOME skin shares the same screens and data. */
final class UiSkin {
    static final String MODERN_LIGHT = "Nowoczesny jasny";
    static final String ELEGANT_DARK = "Elegancki ciemny";
    static final String FOREST = "Leśny / naturalny";
    static final String NEON = "Neonowy";
    static final String NATURE = "Naturalny";
    static final String PASTEL = "Pastelowy";
    static final String GLASS = "Szklany";
    static final String WMM = "WMM";
    static final String TRAINER = "Trener 2";
    static final String[] THEMES = {
        MODERN_LIGHT, ELEGANT_DARK, FOREST,
        NEON, NATURE, PASTEL, GLASS, WMM, TRAINER
    };

    final String name;
    final int background, surface, foreground, secondary, accent, accentInk;
    final int tileTop, tileBottom, outline, iconBacking, buttonForeground;
    final boolean light;

    private UiSkin(String name, String background, String surface,
            String foreground, String secondary, String accent, String accentInk,
            String tileTop, String tileBottom, String outline,
            String iconBacking, String buttonForeground, boolean light) {
        this.name = name;
        this.background = Color.parseColor(background);
        this.surface = Color.parseColor(surface);
        this.foreground = Color.parseColor(foreground);
        this.secondary = Color.parseColor(secondary);
        this.accent = Color.parseColor(accent);
        this.accentInk = Color.parseColor(accentInk);
        this.tileTop = Color.parseColor(tileTop);
        this.tileBottom = Color.parseColor(tileBottom);
        this.outline = Color.parseColor(outline);
        this.iconBacking = Color.parseColor(iconBacking);
        this.buttonForeground = Color.parseColor(buttonForeground);
        this.light = light;
    }

    static boolean accepted(String value) {
        if (value == null) return false;
        for (String name : THEMES) if (name.equals(value)) return true;
        return "Grafitowy".equals(value) || "Leśny".equals(value)
            || "Jasny".equals(value) || "Trener 2".equals(value);
    }

    static UiSkin forName(String requested) {
        if (MODERN_LIGHT.equals(requested)) {
            return new UiSkin(MODERN_LIGHT, "#F4F7FB", "#FFFFFF", "#0B1830",
                "#667085", "#4A90FF", "#10213A", "#FFFFFF", "#EEF4FF",
                "#D8E2F1", "#EAF2FF", "#FFFFFF", true);
        }
        if (ELEGANT_DARK.equals(requested)) {
            return new UiSkin(ELEGANT_DARK, "#07111D", "#0D1B2A", "#F7FAFF",
                "#A6B7C9", "#2EA8FF", "#071522", "#101F31", "#091521",
                "#23364D", "#132A40", "#FFFFFF", false);
        }
        if (FOREST.equals(requested)) {
            return new UiSkin(FOREST, "#0D1B14", "#193024", "#F7F4EA",
                "#CAD6C7", "#62C85A", "#10210F", "#27412D", "#17291D",
                "#4B624D", "#314A35", "#F7F4EA", false);
        }
        // Old installations and JSON backups retain their saved theme.
        if (NATURE.equals(requested) || "Leśny".equals(requested)) {
            return new UiSkin(NATURE, "#11241D", "#203A30", "#F6F5E9",
                "#BACFC1", "#B4E8B7", "#102A1E", "#284637", "#1B332B",
                "#53745F", "#233A2F", "#142D21", false);
        }
        if (PASTEL.equals(requested) || "Jasny".equals(requested)) {
            return new UiSkin(PASTEL, "#F7F9F7", "#FFFFFF", "#172A2C",
                "#536D70", "#BEF4DD", "#174234", "#FFFFFF", "#F1F8F4",
                "#DCE8E2", "#E5F6EF", "#17382C", true);
        }
        if (GLASS.equals(requested)) {
            return new UiSkin(GLASS, "#0B1D32", "#1B344C", "#F4FAFF",
                "#B6CEDD", "#82EBDF", "#0D2432", "#274A64", "#162D48",
                "#527A97", "#24445D", "#102D3B", false);
        }
        // EDHOME-only palettes inspired by the other apps, not shared preferences.
        if (WMM.equals(requested)) {
            return new UiSkin(WMM, "#11171E", "#232C35", "#F3F6F9",
                "#B7C6D0", "#67C7B9", "#102522", "#303B45", "#1D262E",
                "#4B6473", "#24323D", "#12282C", false);
        }
        if (TRAINER.equals(requested)) {
            return new UiSkin(TRAINER, "#090909", "#171717", "#F4F4F4",
                "#B4B4B4", "#EF2B2D", "#FFFFFF", "#282828", "#161616",
                "#494949", "#242424", "#FFFFFF", false);
        }
        // Old Grafitowy chooses the new dark default; data survives.
        return new UiSkin(NEON, "#111C2A", "#24374B", "#F4F9FF",
            "#AAC5D8", "#8DE8CE", "#12312D", "#2B4259", "#203247",
            "#425E79", "#1B2D40", "#112A2B", false);
    }

    boolean showcase() {
        return MODERN_LIGHT.equals(name) || ELEGANT_DARK.equals(name)
            || FOREST.equals(name);
    }

    boolean forest() { return FOREST.equals(name); }

    GradientDrawable page(Context context) {
        String top;
        String bottom;
        if (MODERN_LIGHT.equals(name)) {
            top = "#FAFCFF";
            bottom = "#EDF4FF";
        } else if (FOREST.equals(name)) {
            top = "#102519";
            bottom = "#07120D";
        } else if (ELEGANT_DARK.equals(name)) {
            top = "#07111D";
            bottom = "#030A12";
        } else {
            top = String.format("#%08X", background);
            bottom = top;
        }
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.parseColor(top), Color.parseColor(bottom)});
        drawable.setCornerRadius(0f);
        return drawable;
    }

    static int dp(Context context, float size) {
        return (int) (context.getResources().getDisplayMetrics().density
            * size + 0.5f);
    }

    GradientDrawable panel(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, 1), outline);
        return drawable;
    }

    GradientDrawable tile(Context context, boolean primary, int tint) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            primary ? new int[]{tint, tint}
                : new int[]{tileTop, tileBottom});
        drawable.setCornerRadius(dp(context, 28));
        drawable.setStroke(dp(context, primary ? 2 : 1),
            primary ? (light ? Color.parseColor("#71CDA3") : accent) : outline);
        return drawable;
    }

    GradientDrawable pill(Context context, int tint) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{tint, tint});
        drawable.setCornerRadius(dp(context, 26));
        drawable.setStroke(dp(context, 1), outline);
        return drawable;
    }

    int tileTint(String code) {
        if ("blue".equals(code)) return light
            ? Color.parseColor("#DBEFFA") : Color.parseColor("#315A81");
        if ("amber".equals(code)) return light
            ? Color.parseColor("#FFE8BD") : Color.parseColor("#6B4B2D");
        if ("violet".equals(code)) return light
            ? Color.parseColor("#EEE4FF") : Color.parseColor("#55416D");
        return accent;
    }

    int tileText(int tint) {
        if (!light && tint != accent) return Color.WHITE;
        return accentInk;
    }
}
