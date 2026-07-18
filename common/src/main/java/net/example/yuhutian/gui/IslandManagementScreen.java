package net.example.yuhutian.gui;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.network.*;
import net.example.yuhutian.world.IslandInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 空岛管理面板 — 国风极简侧边栏标签页布局。
 * <p>
 * 左侧侧边栏含青色指示条的扁平标签页，右侧为内容区域。
 * 通过 currentTab 控制组件分组，切换时清除旧组件并重建。
 * 信任列表通过 S2C 包实时刷新，显示玩家名字而非 UUID。
 * </p>
 */
public class IslandManagementScreen extends AbstractContainerScreen<IslandManagementMenu> {

    // ==================== 布局常量 ====================
    private static final int SIDEBAR_W = 48;
    private static final int TAB_H = 32;
    private static final int TAB_GAP = 4;
    private static final int TAB_COUNT = 3;

    // ==================== 国风配色 ====================
    /** 主背景：70% 透明度墨黑 */
    private static final int CLR_BG       = 0xB30F0F14;
    /** 侧边栏底色 */
    private static final int CLR_SIDEBAR  = 0xE60A0A10;
    /** 激活标签页 */
    private static final int CLR_ACTIVE   = 0xE61A1A2E;
    /** 未激活标签页 */
    private static final int CLR_INACTIVE = 0xE612121A;
    /** 淡青色描边 / 指示条 */
    private static final int CLR_CYAN     = 0x66A8E3E3;
    /** 选中条目的金色高亮 */
    private static final int CLR_GOLD_BG  = 0x50FFD700;

    // 文字颜色
    private static final int TXT_TITLE  = 0xFFFFFFFF; // 象牙白
    private static final int TXT_BODY   = 0xFFCCCCCC; // 正文
    private static final int TXT_DIM    = 0xFFAAAAAA; // 次要
    private static final int TXT_FAINT  = 0xFF666666; // 极淡
    private static final int TXT_GOLD   = 0xFFFFD700; // 金色标题

    // 标签页
    private static final int TAB_TERRITORY = 0;
    private static final int TAB_SETTINGS  = 1;
    private static final int TAB_VISIT     = 2;
    private static final String[] TAB_LABELS = {"管  理", "设  置", "传  书"};

    // ==================== 状态 ====================
    private int currentTab = TAB_TERRITORY;
    private final List<AbstractWidget> tabWidgets = new ArrayList<>();

    // Tab 2 缓存
    private String cachedGreetingText;
    private String cachedSelectedSound;
    private EditBox greetingEditBox;

    // Tab 3 拜访列表
    public static List<SyncVisitableIslandsPayload.IslandEntry> visitPendingData = null;
    private List<SyncVisitableIslandsPayload.IslandEntry> visitableEntries = new ArrayList<>();
    private UUID selectedVisitUuid;
    private int visitScrollOffset;

    /** S2C 信任列表刷新标记 */
    public static boolean pendingTerritoryRefresh = false;

    // 音效选项
    private static final String[][] SOUND_OPTIONS = {
            {"成就达成", "minecraft:entity.player.levelup"},
            {"钟声", "minecraft:block.bell.use"},
            {"竖琴", "minecraft:block.note_block.harp"},
            {"铃声", "minecraft:block.note_block.bell"},
            {"木琴", "minecraft:block.note_block.xylophone"},
            {"猫叫", "minecraft:entity.cat.purr"},
            {"§6挑战达成", "minecraft:ui.toast.challenge_complete"},
    };

    // ==================== 构造 ====================

    public IslandManagementScreen(IslandManagementMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 260;
        this.cachedGreetingText = menu.getGreetingText();
        this.cachedSelectedSound = menu.getGreetingSound();
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        tabWidgets.clear();
        visitableEntries.clear();
        pendingTerritoryRefresh = false;
        rebuildTabContent();
    }

    // ==================== 标签页系统 ====================

    private void switchTab(int tab) {
        if (tab == currentTab) return;
        if (currentTab == TAB_SETTINGS && greetingEditBox != null) {
            cachedGreetingText = greetingEditBox.getValue();
        }
        currentTab = tab;
        rebuildTabContent();
    }

    private void rebuildTabContent() {
        for (AbstractWidget w : tabWidgets) removeWidget(w);
        tabWidgets.clear();
        switch (currentTab) {
            case TAB_TERRITORY -> buildTerritoryTab();
            case TAB_SETTINGS  -> buildSettingsTab();
            case TAB_VISIT     -> buildVisitTab();
        }
    }

    /** S2C 刷新入口：标记待刷新，下一帧 render() 处理 */
    public static void refreshFromServer() {
        pendingTerritoryRefresh = true;
    }

    private <T extends AbstractWidget> T addTabWidget(T w) {
        tabWidgets.add(w);
        this.addRenderableWidget(w);
        return w;
    }

    // ==================== Tab 1: 领地管理 ====================

    private void buildTerritoryTab() {
        int cx = this.leftPos + SIDEBAR_W + 10;
        int cy = this.topPos;
        int cw = this.imageWidth - SIDEBAR_W - 18;

        // —— 信任玩家删除按钮（使用名字映射） ——
        Map<UUID, String> nameMap = this.menu.getTrustedPlayerNames();
        List<UUID> allowed = this.menu.getMenuAllowedPlayers();
        int y = cy + 68;
        for (UUID uuid : allowed) {
            if (!nameMap.containsKey(uuid)) {
                nameMap.put(uuid, uuid.toString().substring(0, 8) + "...");
            }
            addTabWidget(Button.builder(Component.literal("×"), b -> {
                NetworkManager.sendToServer(new RemoveFriendPayload(uuid));
            }).pos(cx + cw - 18, y - 2).size(18, 16).build());
            y += 18;
            if (y > cy + 138) break;
        }

        // —— 在线玩家下拉 ——
        Map<UUID, String> online = this.menu.getOnlinePlayers();
        List<UUID> onlineUuids = new ArrayList<>(online.keySet());

        CycleButton<UUID> dropdown;
        if (!onlineUuids.isEmpty()) {
            dropdown = CycleButton.<UUID>builder(uuid ->
                            Component.literal(online.getOrDefault(uuid, "???")))
                    .withValues(onlineUuids)
                    .withInitialValue(onlineUuids.get(0))
                    .create(cx, cy + 164, cw - 55, 20, Component.empty());
        } else {
            UUID ph = UUID.randomUUID();
            dropdown = CycleButton.<UUID>builder(uuid -> Component.literal("无在线玩家"))
                    .withValues(ph).withInitialValue(ph)
                    .create(cx, cy + 164, cw - 55, 20, Component.empty());
            dropdown.active = false;
        }
        addTabWidget(dropdown);

        // —— 添加按钮 ——
        final CycleButton<UUID> dd = dropdown;
        addTabWidget(Button.builder(Component.literal("添加"), b -> {
            if (!onlineUuids.isEmpty() && dd.getValue() != null) {
                NetworkManager.sendToServer(new AddFriendPayload(dd.getValue()));
            }
        }).pos(cx + cw - 50, cy + 163).size(50, 22).build());

        // —— 边界开关 ——
        addTabWidget(CycleButton.<Boolean>builder(state ->
                        Component.literal(state ? "§a边界: ON" : "§c边界: OFF"))
                .withValues(true, false)
                .withInitialValue(this.menu.isShowBorder())
                .withTooltip(state -> Tooltip.create(
                        Component.literal("开启后靠近领地边界时显示粒子墙")))
                .create(cx, cy + 210, cw, 20, Component.empty(),
                        (btn, s) -> NetworkManager.sendToServer(new ToggleBorderPayload(s))));
    }

    // ==================== Tab 2: 洞天设置 ====================

    private void buildSettingsTab() {
        int cx = this.leftPos + SIDEBAR_W + 10;
        int cy = this.topPos;
        int cw = this.imageWidth - SIDEBAR_W - 18;

        // 欢迎仪式开关
        addTabWidget(Checkbox.builder(
                        Component.literal("启用入场欢迎仪式"), this.font)
                .pos(cx, cy + 22)
                .selected(this.menu.isEnableGreeting())
                .onValueChange((cb, sel) ->
                        NetworkManager.sendToServer(new ToggleGreetingPayload(sel)))
                .build());

        // 寄语输入框
        this.greetingEditBox = addTabWidget(new EditBox(this.font,
                cx, cy + 62, cw, 20, Component.literal("")));
        this.greetingEditBox.setMaxLength(128);
        this.greetingEditBox.setValue(cachedGreetingText);

        // 音效下拉
        int initIdx = 0;
        for (int i = 0; i < SOUND_OPTIONS.length; i++) {
            if (SOUND_OPTIONS[i][1].equals(cachedSelectedSound)) { initIdx = i; break; }
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < SOUND_OPTIONS.length; i++) indices.add(i);

        addTabWidget(CycleButton.<Integer>builder(idx ->
                        Component.literal(SOUND_OPTIONS[idx][0]))
                .withValues(indices).withInitialValue(initIdx)
                .create(cx, cy + 102, cw - 10, 20, Component.literal("音效:"),
                        (btn, newIdx) -> cachedSelectedSound = SOUND_OPTIONS[newIdx][1]));

        // 保存按钮
        addTabWidget(Button.builder(Component.literal("保存寄语"), b -> {
            String text = greetingEditBox.getValue();
            if (text.isEmpty()) text = IslandInfo.DEFAULT_GREETING_TEXT;
            cachedGreetingText = text;
            NetworkManager.sendToServer(new UpdateGreetingPayload(text, cachedSelectedSound));
        }).pos(cx + cw - 80, cy + 137).size(80, 20).build());
    }

    // ==================== Tab 3: 壶中传书 ====================

    private void buildVisitTab() {
        if (visitPendingData != null) {
            this.visitableEntries = new ArrayList<>(visitPendingData);
            visitPendingData = null;
        }
        // 请求最新列表
        NetworkManager.sendToServer(new RequestVisitableIslandsPayload());
    }

    // ==================== 渲染 ====================

    /** 彻底抹除原版容器默认标题和 "物品栏" 文字 */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 故意留空：不调用 super，不绘制任何原版标签
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x0 = this.leftPos, y0 = this.topPos;
        int w = this.imageWidth, h = this.imageHeight;

        // 主背景
        g.fill(x0, y0, x0 + w, y0 + h, CLR_BG);

        // 淡青色描边（四边 1px）
        g.fill(x0, y0, x0 + w, y0 + 1, CLR_CYAN);             // top
        g.fill(x0, y0 + h - 1, x0 + w, y0 + h, CLR_CYAN);    // bottom
        g.fill(x0, y0, x0 + 1, y0 + h, CLR_CYAN);             // left
        g.fill(x0 + w - 1, y0, x0 + w, y0 + h, CLR_CYAN);    // right

        // 侧边栏底色
        g.fill(x0 + 1, y0 + 1, x0 + SIDEBAR_W, y0 + h - 1, CLR_SIDEBAR);

        // 侧边栏与内容区分隔线
        g.fill(x0 + SIDEBAR_W, y0 + 1, x0 + SIDEBAR_W + 1, y0 + h - 1, CLR_CYAN);

        // 标签页背景 + 指示条
        for (int i = 0; i < TAB_COUNT; i++) {
            int ty = y0 + 10 + i * (TAB_H + TAB_GAP);
            boolean active = (i == currentTab);

            // 标签页背景
            g.fill(x0 + 3, ty, x0 + SIDEBAR_W - 2, ty + TAB_H,
                    active ? CLR_ACTIVE : CLR_INACTIVE);

            // 激活态：左侧青色指示条
            if (active) {
                g.fill(x0 + 3, ty, x0 + 5, ty + TAB_H, 0xFFA8E3E3);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);

        // 处理 S2C 实时刷新
        if (pendingTerritoryRefresh) {
            pendingTerritoryRefresh = false;
            if (currentTab == TAB_TERRITORY) {
                rebuildTabContent();
            }
        }

        super.render(g, mx, my, pt); // 绘制所有 Widget

        // 侧边栏标签页文字（绘制在按钮背景之上）
        for (int i = 0; i < TAB_COUNT; i++) {
            int ty = this.topPos + 10 + i * (TAB_H + TAB_GAP);
            boolean active = (i == currentTab);
            int textW = this.font.width(TAB_LABELS[i]);
            int tx = this.leftPos + 3 + (SIDEBAR_W - 5 - textW) / 2;
            g.drawString(this.font, TAB_LABELS[i],
                    tx, ty + (TAB_H - 8) / 2,
                    active ? TXT_TITLE : TXT_DIM, false);
        }

        // 内容区域
        int cx = this.leftPos + SIDEBAR_W + 10;
        int cy = this.topPos;
        int cw = this.imageWidth - SIDEBAR_W - 18;

        switch (currentTab) {
            case TAB_TERRITORY -> renderTerritory(g, cx, cy, cw);
            case TAB_SETTINGS  -> renderSettings(g, cx, cy, cw);
            case TAB_VISIT     -> renderVisit(g, cx, cy, cw);
        }

        this.renderTooltip(g, mx, my);
    }

    // ---------- Tab 1 渲染：领地管理 ----------

    private void renderTerritory(GuiGraphics g, int cx, int cy, int cw) {
        g.drawString(this.font, "§l领地管理", cx, cy + 8, TXT_GOLD, false);

        g.drawString(this.font,
                "坐标: (" + this.menu.getIslandX() + ", " + this.menu.getIslandZ() + ")",
                cx, cy + 26, TXT_BODY, false);
        g.drawString(this.font, "主人: " + this.menu.getOwnerName(),
                cx, cy + 38, TXT_BODY, false);

        separator(g, cx, cy + 50, cw);
        g.drawString(this.font, "§n信任玩家:", cx, cy + 55, TXT_DIM, false);

        // 信任玩家列表（使用名字映射）
        int y = cy + 70;
        Map<UUID, String> nameMap = this.menu.getTrustedPlayerNames();
        List<UUID> players = this.menu.getMenuAllowedPlayers();
        if (players.isEmpty()) {
            g.drawString(this.font, "(暂无)", cx + 2, y, TXT_FAINT, false);
        } else {
            for (UUID uuid : players) {
                String name = nameMap.getOrDefault(uuid,
                        uuid.toString().substring(0, 8) + "...");
                g.drawString(this.font, name, cx + 2, y, TXT_BODY, false);
                y += 18;
                if (y > cy + 138) break;
            }
        }

        separator(g, cx, cy + 148, cw);
        g.drawString(this.font, "添加在线玩家:", cx, cy + 152, TXT_DIM, false);

        separator(g, cx, cy + 195, cw);
        g.drawString(this.font, "领地设置:", cx, cy + 199, TXT_DIM, false);
    }

    // ---------- Tab 2 渲染：洞天设置 ----------

    private void renderSettings(GuiGraphics g, int cx, int cy, int cw) {
        g.drawString(this.font, "§l洞天设置", cx, cy + 8, TXT_GOLD, false);
        separator(g, cx, cy + 50, cw);
        g.drawString(this.font, "§n欢迎寄语:", cx, cy + 54, TXT_DIM, false);
        g.drawString(this.font, "§n音效选择:", cx, cy + 92, TXT_DIM, false);
    }

    // ---------- Tab 3 渲染：壶中传书 ----------

    private void renderVisit(GuiGraphics g, int cx, int cy, int cw) {
        g.drawString(this.font, "§l壶中传书", cx, cy + 8, TXT_GOLD, false);
        separator(g, cx, cy + 50, cw);
        g.drawString(this.font, "§n可拜访空岛:", cx, cy + 54, TXT_DIM, false);

        // 列表背景
        int listTop = cy + 66;
        int listBottom = cy + 210;
        g.fill(cx, listTop, cx + cw, listBottom, 0x20FFFFFF);
        // 列表淡青色描边
        g.fill(cx, listTop, cx + cw, listTop + 1, CLR_CYAN);
        g.fill(cx, listBottom - 1, cx + cw, listBottom, CLR_CYAN);
        g.fill(cx, listTop, cx + 1, listBottom, CLR_CYAN);
        g.fill(cx + cw - 1, listTop, cx + cw, listBottom, CLR_CYAN);

        // 异步数据检查
        if (visitPendingData != null) {
            this.visitableEntries = new ArrayList<>(visitPendingData);
            visitPendingData = null;
        }

        // 渲染条目（裁剪区域）
        int entryH = 16;
        g.enableScissor(cx + 1, listTop + 1, cx + cw - 1, listBottom - 1);
        for (int i = visitScrollOffset; i < visitableEntries.size(); i++) {
            int ey = listTop + 2 + (i - visitScrollOffset) * entryH;
            if (ey + entryH > listBottom) break;

            SyncVisitableIslandsPayload.IslandEntry entry = visitableEntries.get(i);
            boolean sel = Objects.equals(entry.ownerUuid(), selectedVisitUuid);

            if (sel) {
                g.fill(cx + 1, ey, cx + cw - 1, ey + entryH, CLR_GOLD_BG);
                // 选中条目的左侧金色指示
                g.fill(cx + 1, ey, cx + 3, ey + entryH, 0xFFFFD700);
            }

            g.drawString(this.font,
                    entry.ownerName() + "  #" + entry.index(),
                    cx + 6, ey + 4,
                    sel ? 0xFFFFD700 : TXT_BODY, false);
        }
        g.disableScissor();

        if (visitableEntries.isEmpty()) {
            g.drawString(this.font, "暂无可拜访的空岛",
                    cx + (cw - this.font.width("暂无可拜访的空岛")) / 2,
                    cy + 132, TXT_FAINT, false);
        }

        // 底部提示
        g.drawString(this.font, "§7点击空岛名称即可传送",
                cx, cy + 218, TXT_FAINT, false);
    }

    // ==================== 工具方法 ====================

    private void separator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, CLR_CYAN);
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 优先检测侧边栏标签页点击
        for (int i = 0; i < TAB_COUNT; i++) {
            int ty = this.topPos + 10 + i * (TAB_H + TAB_GAP);
            if (mx >= this.leftPos + 2 && mx <= this.leftPos + SIDEBAR_W
                    && my >= ty && my <= ty + TAB_H) {
                switchTab(i);
                return true;
            }
        }

        if (super.mouseClicked(mx, my, btn)) return true;

        // Tab 3: 点击拜访列表条目直接传送
        if (currentTab == TAB_VISIT) {
            int cx = this.leftPos + SIDEBAR_W + 10;
            int cw = this.imageWidth - SIDEBAR_W - 18;
            int listTop = this.topPos + 66;
            int listBottom = this.topPos + 210;

            if (mx >= cx && mx <= cx + cw && my >= listTop && my <= listBottom) {
                int entryH = 16;
                int idx = visitScrollOffset + (int) ((my - listTop - 2) / entryH);
                if (idx >= 0 && idx < visitableEntries.size()) {
                    UUID target = visitableEntries.get(idx).ownerUuid();
                    selectedVisitUuid = target;
                    // 直接发送传送请求
                    NetworkManager.sendToServer(new TeleportToIslandPayload(target));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (super.mouseScrolled(mx, my, sx, sy)) return true;

        if (currentTab == TAB_VISIT) {
            int cx = this.leftPos + SIDEBAR_W + 10;
            int cw = this.imageWidth - SIDEBAR_W - 18;
            int listTop = this.topPos + 66;
            int listBottom = this.topPos + 210;

            if (mx >= cx && mx <= cx + cw && my >= listTop && my <= listBottom) {
                int entryH = 16;
                int maxVisible = (listBottom - listTop - 4) / entryH;
                int maxOffset = Math.max(0, visitableEntries.size() - maxVisible);
                visitScrollOffset = (int) Math.max(0,
                        Math.min(maxOffset, visitScrollOffset - sy));
                return true;
            }
        }
        return false;
    }

    // ==================== 键盘拦截 ====================

    /**
     * 修复在 EditBox 中输入含 E 的英文时按 E 关闭 GUI 的 Bug。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (greetingEditBox != null && greetingEditBox.isFocused()) {
            if (this.minecraft != null
                    && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
