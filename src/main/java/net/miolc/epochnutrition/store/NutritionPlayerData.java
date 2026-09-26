package net.miolc.epochnutrition.store;

import java.util.HashSet;
import java.util.Set;

/**
 * 单个玩家的营养数据（缓存态）。
 */
public final class NutritionPlayerData {

    /** 四项营养值，下标与 NutritionType.ordinal() 对应。 */
    public final double[] values = new double[NutritionTypeLength()];
    /** 已解锁的图鉴分类 id。 */
    public final Set<String> unlocked = new HashSet<>();
    /** 最近在线时间戳（毫秒），用于离线衰减结算。 */
    public long lastSeen = System.currentTimeMillis();
    /** 因哪种营养清零而死亡（grain/protein），复活后清除并补值。 */
    public String deathByNutrient;
    /** 玩家名（最近一次进服时记录）。 */
    public String name;
    /** 是否有未保存修改。 */
    public boolean dirty;

    private static int NutritionTypeLength() {
        return net.miolc.epochnutrition.NutritionType.ALL.length;
    }
}
