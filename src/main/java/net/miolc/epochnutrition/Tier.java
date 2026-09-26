package net.miolc.epochnutrition;

/**
 * 营养档位（按数值区间判断）：
 * 0~30 营养不良 / 30~60 营养缺乏 / 60~100 营养健全 / 100~120 营养盈余
 */
public enum Tier {
    MALNOURISHED("malnourished", "营养不良", 0, 30),
    DEFICIENT("deficient", "营养缺乏", 30, 60),
    SOUND("sound", "营养健全", 60, 100),
    SURPLUS("surplus", "营养盈余", 100, 120);

    private final String id;
    private final String display;
    private final int min;
    private final int max;

    Tier(String id, String display, int min, int max) {
        this.id = id;
        this.display = display;
        this.min = min;
        this.max = max;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public static Tier of(double value) {
        if (value < 30) return MALNOURISHED;
        if (value < 60) return DEFICIENT;
        if (value < 100) return SOUND;
        return SURPLUS;
    }
}
