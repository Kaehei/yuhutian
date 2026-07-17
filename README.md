# 玉壶天 (YuHuTian)

Minecraft 1.21.1 空岛模组 —— 基于 Architectury API 的 Fabric/Forge 双端支持。

## 功能

- 自定义虚空维度"玉壶天"
- 每位玩家独立空岛（X轴线性递增分配）
- 结构模板自动生成空岛
- 空岛 NPC 管理面板（添加/移除信任玩家）
- 领地保护系统（仅岛主和信任成员可操作）

## 构建

```bash
./gradlew build
```

产物位于:
- `fabric/build/libs/` — Fabric 版本
- `forge/build/libs/` — Forge 版本

## 依赖

- Minecraft 1.21.1
- Architectury API 13.0.8
- Java 21
