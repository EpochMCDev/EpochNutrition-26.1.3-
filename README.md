# EpochNutrition 营养系统

四项独立营养值玩法插件（碳水 / 维生素 / 蛋白质 / 脂肪），按瓜始皇规格实现。

- **版本**：26.1.3
- **适配**：Paper 1.21.x以上 + CraftEngine **26.1.3**（软依赖，无硬 API 依赖）
- **指令**：`/enu`
- **CE 物品对接**：customcrops 包（玉米/番茄/卷心菜/白菜/扁豆/茄子/菠萝/葡萄/火龙果/蓝莓）

## 构建

```bash
mvn package        # 需要 JDK 21 + Maven，产物 target/EpochNutrition-26.1.3.jar
```

仓库内已附带一份用 JDK21 javac 直接编译打包好的 `target/EpochNutrition-26.1.3.jar`（未跑 Maven，功能一致）。

## 与规格的逐条对应

| 规格条目 | 实现 |
| --- | --- |
| 四项营养值 grain/vitamin/protein/fat，0~120 两位小数 | `NutritionType` + `NutritionManager`（data.yml 持久化） |
| Tab 显示"碳水：xx%" | `TabTask`，footer 每秒刷新，模式 footer/header/both/off 可配 |
| 按天消耗 34/22/18/16，游戏日 20 分钟 | `DecayTask`，interval-minutes 可改扣取间隔（0.5=每30秒） |
| 食物营养 = 分类系数 × 饱食度（不管饱和度） | `ConsumeListener` + `NutritionCalculator`，饱食度读真实食物组件 |
| 6 大分类 + CE 物品 | `config.yml → categories`，CE 模式支持结尾 `*` 通配（含银星/金星品质变体） |
| 料理从原料计算：(基础×(1+0.05(n-1)))/m | `CraftListener`：n=原料数（工具不计入），m=单次产物数；结果写入产物 PDC，进食优先采用 |
| 工具（菜刀/瓶子/碗）不计入 n | `crafting.tool-materials`（原版）+ `crafting.tool-items`（CE 模式，如 `senro_kitchen:knife*`） |
| 饱食度/饱和度可走累加 | `crafting.food-accumulate: true` 开启（默认关，非食物产物需 CONSUMABLE 组件才能食用） |
| 四档位收益/惩罚（含缓慢5/黑暗/凋零2 等） | `effects` 配置段，按 TXT 默认值全量填好 |
| 碳水/蛋白质清零立刻死亡 | `checkLethal`（衰减/进食/指令修改后均校验），自定义死亡消息 |
| 清零死亡复活后补 30 | `DeathListener`，`death.respawn-bonus: 30` |
| 图鉴仅显示 >0 的营养项 | `GuideMenu` lore 逐项判断 |
| `/enu set/add/remove/getguide` | `EnuCommand`，支持中文别名（碳水/维生素/蛋白质/脂肪）与 `all`，带 Tab 补全 |
| 附加 | `/enu query [玩家]`、`/enu guide`（自己开图鉴）、`/enu reload` |

## CraftEngine 26.1.3 兼容设计

无编译期 CE 依赖，全部走 Paper 26.X 数据组件：

1. **CE 物品识别**（`ce.detection: auto`）：
   - `item_model`：非 minecraft 命名空间的模型键 → `customcrops:item/customcrops/crop/corn/corn`
   - `custom_model_data` 字符串：同上路径
   - id 推导：`命名空间:路径最后一段` → `customcrops:corn`（银星/金星变体 → `customcrops:tomato_silver_star`，被 `customcrops:tomato*` 通配命中）
   - 若 CE 26.1.3 识别键不同，把 `ce.detection` 换成 `item_model` 或 `custom_model_data` 单测即可
2. **饱食度读取**：优先物品显式 FOOD 组件；CE 物品读不到组件时走 `ce.hunger-overrides` 兜底表（已按作物预填合理值，**请按 CE 包实际饱食度校对**）；原版物品回退材质默认组件
3. **图鉴 CE 图标**：`PAPER + item_model` 指向 CE 模型路径，客户端资源包直接渲染正确图标
4. **CE 料理**（无法被原版合成事件拦截）：`manual-recipes` 手动配方表，走同一套公式：

```yaml
manual-recipes:
  "senro_kitchen:sandwich":
    output-amount: 1
    ingredients: ["BREAD|面包", "BEEF|牛排", "customcrops:tomato"]
```

森罗厨房后续也可直接调 `EpochNutrition.get().addNutrition(...)` 等 API，或在 CE 料理完成时调用 PDC 写入。

## 数值示例

面包（饱食度 5，精制主食 碳水1.5/蛋白质0.1/维生素0.15/脂肪0.10）：
碳水 7.5；蛋白质 0.5；维生素 0.75；脂肪 0.5 ✓

三明治 = 面包×1 + 牛排×1 + 胡萝卜×1（n=3, m=1）：
最终营养 = 三项原营养之和 × (1 + 0.05×2) / 1 = 累计 × 1.10 ✓

## 目录结构

```
EpochNutrition/
├── pom.xml
├── target/EpochNutrition-26.1.3.jar     # 已打包产物
└── src/main/
    ├── java/net/miolc/epochnutrition/
    │   ├── EpochNutritionPlugin.java    # 主类 + 对外 API
    │   ├── NutritionType / Tier         # 营养类型与四档位
    │   ├── config/Settings              # 配置解析
    │   ├── registry/                    # 分类、CE 识别、饱食度
    │   ├── store/                       # 玩家数据 + data.yml
    │   ├── calc/NutritionCalculator     # 进食/配方计算核心
    │   ├── listener/                    # 进食/合成/死亡/进出服
    │   ├── task/                        # 衰减/效果/Tab 三个定时任务
    │   ├── guide/                       # 图鉴 GUI
    │   ├── command/EnuCommand           # /enu
    │   └── util/Text
    └── resources/
        ├── plugin.yml
        └── config.yml                   # 全部按 TXT 数值预填
```

## QA 清单（建议进服验证）

1. `/enu query` 四项显示 75；等 1 分钟看碳水下降 1.7/分钟
2. 吃面包 → actionbar 显示 +碳水7.5；饥饿值不影响营养
3. 工作台 面包+牛排+胡萝卜 合成 → 产物有营养覆盖（可 `/enu add` 前后对比）；shift 批量合成建议单次合成核对（CraftBukkit 对 shift 合成的 setCurrentItem 有兼容性限制）
4. `/enu set 玩家 grain 0` → 立即死亡，死亡消息为碳水文案；复活后碳水 +30
5. `/enu getguide 玩家 staple` → `/enu guide` 图鉴里"碳水主食"解锁，条目 lore 仅显示 >0 营养项
6. 手持 customcrops 玉米吃下 → 营养按 蔬菜/碳水主食 系数×配置饱食度增加
