package net.miolc.epochnutrition.registry;

import org.bukkit.Material;

/**
 * 物品匹配模式：
 *  - 原版材质名（BREAD）
 *  - CE 物品模式（customcrops:corn / customcrops:tomato*，支持结尾 * 通配）
 */
public final class ItemPattern {

    private final String raw;
    private final boolean ce;
    private final String namespace;
    private final String namePrefix;
    private final Material material;

    private ItemPattern(String raw, boolean ce, String namespace, String namePrefix, Material material) {
        this.raw = raw;
        this.ce = ce;
        this.namespace = namespace;
        this.namePrefix = namePrefix;
        this.material = material;
    }

    public static ItemPattern of(String raw) {
        String s = raw.trim();
        int idx = s.indexOf(':');
        if (idx > 0) {
            String ns = s.substring(0, idx).toLowerCase(java.util.Locale.ROOT);
            String name = s.substring(idx + 1).toLowerCase(java.util.Locale.ROOT);
            boolean wildcard = name.endsWith("*");
            if (wildcard) name = name.substring(0, name.length() - 1);
            return new ItemPattern(s, true, ns, name, null);
        }
        return new ItemPattern(s, false, null, null, Material.matchMaterial(s));
    }

    public boolean isCe() {
        return ce;
    }

    public Material material() {
        return material;
    }

    public String raw() {
        return raw;
    }

    public boolean valid() {
        return ce || material != null;
    }

    /** 匹配 CE 物品 id（如 customcrops:tomato_silver_star）。 */
    public boolean matchesCe(String ceId) {
        if (!ce || ceId == null) return false;
        String id = ceId.toLowerCase(java.util.Locale.ROOT);
        int idx = id.indexOf(':');
        if (idx <= 0) return false;
        return id.substring(0, idx).equals(namespace) && id.substring(idx + 1).startsWith(namePrefix);
    }
}
