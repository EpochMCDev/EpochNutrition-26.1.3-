package net.miolc.epochnutrition;

/**
 * 四项独立营养值。
 * 数值范围 0~120，可含两位小数。
 */
public enum NutritionType {
    GRAIN("grain", "碳水"),
    VITAMIN("vitamin", "维生素"),
    PROTEIN("protein", "蛋白质"),
    FAT("fat", "脂肪");

    public static final NutritionType[] ALL = values();

    private final String id;
    private final String display;

    NutritionType(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public int index() {
        return ordinal();
    }

    /** 按 id 或中文别名解析（grain/碳水 均可）。 */
    public static NutritionType byId(String input) {
        if (input == null) return null;
        String k = input.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.isEmpty()) return null;
        for (NutritionType t : ALL) {
            if (t.id.equals(k) || t.display.equals(input.trim())) return t;
        }
        return null;
    }
}
