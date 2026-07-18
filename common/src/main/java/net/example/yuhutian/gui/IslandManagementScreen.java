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
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 空岛管理面板 — 侧边栏标签页布局。
 * <p>
 * 左侧固定侧边栏包含三个标签页按钮，右侧为对应内容区域。
 * 通过 currentTab 状态控制当前显示的组件分组，切换标签页时
 * 清除旧组件并重建新组件，确保页面不重叠不错乱。
 * </p>
 */
public class IslandManagementScreen extends AbstractContainerScreen<IslandManagementMenu> {

    // ==================== 布局常量 ====================
    private static final int SIDEBAR_WIDTH = 42;
    private static final int TAB_BTN_H = 36;
    private static final int TAB_GAP = 3;

    // 颜色
    private static final int CLR_BG          = 0xDD16213E;
    private static final int CLR_SIDEBAR     = 0xFF0F1629;
    private static final int CLR_ACTIVE_TAB  = 0xFF1A1A2E;
    private static final int CLR_INACTIVE    = 0xFF2A2A4A;

    // 标签页索引
    private static final int TAB_TERRITORY = 0;
    private static final int TAB_SETTINGS  = 1;
    private static final int TAB_VISIT     = 2;

    private static final String[] TAB_NAMES = {"§l管理", "§l设置", "§l传书"};

    // ==================== 状态 ====================
    private int currentTab = TAB_TERRITORY;

    /** 当前标签页动态创建的组件（切换时批量移除） */
    private final List<AbstractWidget> tabWidgets = new ArrayList<>();

    // Tab 2 缓存（切换标签页时保留编辑框文本和音效选择）
    private String cachedGreetingText;
    private String cachedSelectedSound;
    private Checkbox greetingToggle;
    private EditBox greetingEditBox;

    // Tab 3 拜访列表
    public static List<SyncVisitableIslandsPayload.IslandEntry> visitPendingData = null;
    private List<SyncVisitableIslandsPayload.IslandEntry> visitableEntries = new ArrayList<>();
    private UUID selectedVisitUuid;
    private int visitScrollOffset;
    private Button teleportButton;

    /** 预设音效选项：显示名 → ResourceLocation 字符串 */
    private static final String[][] SOUND_OPTIONS = {
            {"成就达成", "minecraft:entity.player.levelup"},
            {"钟声", "minecraft:block.bell.use"},
            {"竖琴", "minecraft:block.note_block.harp"},
            {"铃声", "minecraft:block.note_block.bell"},
            {"木琴", "minecraft:block.note_block.xylophone"},
            {"猫叫", "minecraft:entity.cat.purr"},
            {"§6§l挑战达成", "minecraft:ui.toast.challenge_complete"},
    };

    // ==================== 构造 ====================

    public IslandManagementScreen(IslandManagementMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 250;
        this.imageHeight = 280;
        this.cachedGreetingText = menu.getGreetingText();
        this.cachedSelectedSound = menu.getGreetingSound();
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        tabWidgets.clear();
        visitableEntries.clear();

        // —— 侧边栏标签页按钮（常驻，不随标签切换移除） ——
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tab = i;
            Button btn = Button.builder(Component.literal(TAB_NAMES[i]), b -> switchTab(tab))
                    .pos(this.leftPos + 2, this.topPos + 6 + i * (TAB_BTN_H + TAB_GAP))
                    .size(SIDEBAR_WIDTH - 4, TAB_BTN_H)
                    .build();
            this.addRenderableWidget(btn);
        }

        // —— 构建当前标签页内容 ——
        rebuildTabContent();
    }

    // ==================== 标签页切换 ====================

    private void switchTab(int newTab) {
        if (newTab == currentTab) return;

        // 保存 Tab 2 编辑状态
        if (currentTab == TAB_SETTINGS) {
            if (greetingEditBox != null) cachedGreetingText = greetingEditBox.getValue();
        }

        currentTab = newTab;
        rebuildTabContent();
    }

    /** 清除标签页组件，然后重建当前标签页 */
    private void rebuildTabContent() {
        // 移除旧标签页组件
        for (AbstractWidget w : tabWidgets) removeWidget(w);
        tabWidgets.clear();

        switch (currentTab) {
            case TAB_TERRITORY -> buildTerritoryTab();
            case TAB_SETTINGS  -> buildSettingsTab();
            case TAB_VISIT     -> buildVisitTab();
        }
    }

    /** 向当前标签页添加组件的便捷方法 */
    private <T extends AbstractWidget> T addTabWidget(T widget) {
        tabWidgets.add(widget);
        this.addRenderableWidget(widget);
        return widget;
    }

    // ==================== Tab 1: 领地管理 ====================

    private void buildTerritoryTab() {
        int cx = this.leftPos + SIDEBAR_WIDTH + 8;   // content x
        int cy = this.topPos;                          // base y
        int cw = this.imageWidth - SIDEBAR_WIDTH - 16; // content width

        // —— 信任玩家删除按钮 ——
        int y = cy + 62;
        for (UUID uuid : this.menu.getMenuAllowedPlayers()) {
            addTabWidget(Button.builder(Component.literal("×"), b -> {
                NetworkManager.sendToServer(new RemoveFriendPayload(uuid));
            }).pos(cx + cw - 18, y - 2).size(18, 16).build());
            y += 18;
            if (y > cy + 130) break;
        }

        // —— 在线玩家下拉选择 ——
        Map<UUID, String> onlinePlayers = this.menu.getOnlinePlayers();
        List<UUID> uuids = new ArrayList<>(onlinePlayers.keySet());

        CycleButton<UUID> dropdown;
        if (!uuids.isEmpty()) {
            dropdown = CycleButton.<UUID>builder(uuid ->
                            Component.literal(onlinePlayers.getOrDefault(uuid, "???")))
                    .withValues(uuids)
                    .withInitialValue(uuids.get(0))
                    .create(cx, cy + 164, cw - 55, 20, Component.empty());
        } else {
            UUID placeholder = UUID.randomUUID();
            dropdown = CycleButton.<UUID>builder(uuid -> Component.literal("无在线玩家"))
                    .withValues(placeholder)
                    .withInitialValue(placeholder)
                    .create(cx, cy + 164, cw - 55, 20, Component.empty());
            dropdown.active = false;
        }
        addTabWidget(dropdown);

        // —— 添加权限按钮 ——
        final CycleButton<UUID> dd = dropdown;
        addTabWidget(Button.builder(Component.literal("添加"), b -> {
            if (!uuids.isEmpty() && dd.getValue() != null) {
                NetworkManager.sendToServer(new AddFriendPayload(dd.getValue()));
            }
        }).pos(cx + cw - 50, cy + 163).size(50, 22).build());

        // —— 领地边界开关 ——
        addTabWidget(CycleButton.<Boolean>builder(state ->
                        Component.literal(state ? "§a边界: ON" : "§c边界: OFF"))
                .withValues(true, false)
                .withInitialValue(this.menu.isShowBorder())
                .withTooltip(state -> Tooltip.create(
                        Component.literal("开启后靠近领地边界时显示粒子墙")))
                .create(cx, cy + 218, cw, 20, Component.empty(),
                        (btn, newState) -> NetworkManager.sendToServer(
                                new ToggleBorderPayload(newState))));
    }

    // ==================== Tab 2: 洞天设置 ====================

    private void buildSettingsTab() {
        int cx = this.leftPos + SIDEBAR_WIDTH + 8;
        int cy = this.topPos;
        int cw = this.imageWidth - SIDEBAR_WIDTH - 16;

        // —— 欢迎仪式总开关 ——
        this.greetingToggle = addTabWidget(Checkbox.builder(
                        Component.literal("启用入场欢迎仪式"), this.font)
                .pos(cx, cy + 20)
                .selected(this.menu.isEnableGreeting())
                .onValueChange((cb, selected) ->
                        NetworkManager.sendToServer(new ToggleGreetingPayload(selected)))
                .build());

        // —— 寄语输入框 ——
        this.greetingEditBox = addTabWidget(new EditBox(this.font,
                cx, cy + 60, cw, 20, Component.literal("")));
        this.greetingEditBox.setMaxLength(128);
        this.greetingEditBox.setValue(cachedGreetingText);

        // —— 音效选择下拉 ——
        int initialIdx = 0;
        for (int i = 0; i < SOUND_OPTIONS.length; i++) {
            if (SOUND_OPTIONS[i][1].equals(cachedSelectedSound)) {
                initialIdx = i;
                break;
            }
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < SOUND_OPTIONS.length; i++) indices.add(i);

        addTabWidget(CycleButton.<Integer>builder(idx ->
                        Component.literal(SOUND_OPTIONS[idx][0]))
                .withValues(indices)
                .withInitialValue(initialIdx)
                .create(cx, cy + 100, cw - 10, 20, Component.literal("音效:"),
                        (btn, newIdx) -> cachedSelectedSound = SOUND_OPTIONS[newIdx][1]));

        // —— 保存按钮 ——
        addTabWidget(Button.builder(Component.literal("保存寄语"), b -> {
            String text = greetingEditBox.getValue();
            if (text.isEmpty()) text = IslandInfo.DEFAULT_GREETING_TEXT;
            cachedGreetingText = text;
            NetworkManager.sendToServer(new UpdateGreetingPayload(text, cachedSelectedSound));
        }).pos(cx + cw - 80, cy + 135).size(80, 20).build());
    }

    // ==================== Tab 3: 壶中传书 ====================

    private void buildVisitTab() {
        int cx = this.leftPos + SIDEBAR_WIDTH + 8;
        int cy = this.topPos;

        // 从 S2C 缓冲区加载
        if (visitPendingData != null) {
            this.visitableEntries = new ArrayList<>(visitPendingData);
            visitPendingData = null;
        }

        // 传送按钮
        this.teleportButton = addTabWidget(Button.builder(
                Component.literal("传送"), b -> {
                    if (selectedVisitUuid != null) {
                        NetworkManager.sendToServer(
                                new TeleportToIslandPayload(selectedVisitUuid));
                    }
                }).pos(cx + 50, cy + 218).size(100, 20).build());
        this.teleportButton.active = selectedVisitUuid != null;

        // 请求最新列表
        NetworkManager.sendToServer(new RequestVisitableIslandsPayload());
    }

    // ==================== 渲染 ====================

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        // 主背景
        g.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, CLR_BG);
        // 侧边栏
        g.fill(this.leftPos, this.topPos,
                this.leftPos + SIDEBAR_WIDTH, this.topPos + this.imageHeight, CLR_SIDEBAR);

        // 标签页按钮着色（模拟激活态）
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int bx = this.leftPos + 2;
            int by = this.topPos + 6 + i * (TAB_BTN_H + TAB_GAP);
            int color = (i == currentTab) ? CLR_ACTIVE_TAB : CLR_INACTIVE;
            g.fill(bx, by, bx + SIDEBAR_WIDTH - 4, by + TAB_BTN_H, color);
        }

        // 内容区域顶部装饰线
        int cx = this.leftPos + SIDEBAR_WIDTH;
        g.fill(cx, this.topPos, cx + 2, this.topPos + this.imageHeight, 0x40FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);

        int cx = this.leftPos + SIDEBAR_WIDTH + 8;
        int cy = this.topPos;
        int cw = this.imageWidth - SIDEBAR_WIDTH - 16;

        switch (currentTab) {
            case TAB_TERRITORY -> renderTerritoryContent(g, cx, cy, cw);
            case TAB_SETTINGS  -> renderSettingsContent(g, cx, cy, cw);
            case TAB_VISIT     -> renderVisitContent(g, cx, cy, cw);
        }

        this.renderTooltip(g, mx, my);
    }

    // ---------- Tab 1 渲染 ----------

    private void renderTerritoryContent(GuiGraphics g, int cx, int cy, int cw) {
        // 标题
        g.drawString(this.font, "§6§l领地管理", cx, cy + 8, 0xFFD700, false);

        // 坐标 & 主人
        g.drawString(this.font,
                "坐标: (" + this.menu.getIslandX() + ", " + this.menu.getIslandZ() + ")",
                cx, cy + 24, 0xFFFFFF, false);
        g.drawString(this.font, "主人: " + this.menu.getOwnerName(),
                cx, cy + 36, 0xFFFFFF, false);

        // 分隔线
        g.fill(cx, cy + 47, cx + cw, cy + 48, 0x80FFFFFF);

        // 信任玩家标题
        g.drawString(this.font, "§n信任玩家:", cx, cy + 52, 0xAAAAAA, false);

        // 信任玩家列表
        int y = cy + 66;
        List<UUID> players = this.menu.getMenuAllowedPlayers();
        if (players.isEmpty()) {
            g.drawString(this.font, "(暂无)", cx + 2, y, 0x808080, false);
        } else {
            for (UUID uuid : players) {
                g.drawString(this.font,
                        uuid.toString().substring(0, 8) + "...",
                        cx + 2, y, 0xFFFFFF, false);
                y += 18;
                if (y > cy + 130) break;
            }
        }

        // 添加在线玩家标签
        g.fill(cx, cy + 148, cx + cw, cy + 149, 0x80FFFFFF);
        g.drawString(this.font, "添加在线玩家:", cx, cy + 152, 0xAAAAAA, false);

        // 边界标签
        g.fill(cx, cy + 200, cx + cw, cy + 201, 0x80FFFFFF);
        g.drawString(this.font, "领地设置:", cx, cy + 205, 0xAAAAAA, false);
    }

    // ---------- Tab 2 渲染 ----------

    private void renderSettingsContent(GuiGraphics g, int cx, int cy, int cw) {
        g.drawString(this.font, "§6§l洞天设置", cx, cy + 8, 0xFFD700, false);
        g.fill(cx, cy + 47, cx + cw, cy + 48, 0x80FFFFFF);

        g.drawString(this.font, "§n欢迎寄语:", cx, cy + 52, 0xAAAAAA, false);

        // 音效标签
        g.drawString(this.font, "§n音效选择:", cx, cy + 90, 0xAAAAAA, false);
    }

    // ---------- Tab 3 渲染 ----------

    private void renderVisitContent(GuiGraphics g, int cx, int cy, int cw) {
        g.drawString(this.font, "§6§l壶中传书", cx, cy + 8, 0xFFD700, false);
        g.fill(cx, cy + 47, cx + cw, cy + 48, 0x80FFFFFF);

        g.drawString(this.font, "§n可拜访空岛:", cx, cy + 52, 0xAAAAAA, false);

        // 列表背景
        int listTop = cy + 64;
        int listBottom = cy + 210;
        g.fill(cx, listTop, cx + cw, listBottom, 0x30FFFFFF);

        // 异步数据检查
        if (visitPendingData != null) {
            this.visitableEntries = new ArrayList<>(visitPendingData);
            visitPendingData = null;
            if (teleportButton != null) {
                teleportButton.active = selectedVisitUuid != null;
            }
        }

        // 渲染条目（带裁剪）
        int entryH = 14;
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        for (int i = visitScrollOffset; i < visitableEntries.size(); i++) {
            int ey = listTop + (i - visitScrollOffset) * entryH;
            if (ey + entryH > listBottom) break;

            SyncVisitableIslandsPayload.IslandEntry entry = visitableEntries.get(i);
            boolean sel = Objects.equals(entry.ownerUuid(), selectedVisitUuid);

            if (sel) g.fill(cx, ey, cx + cw, ey + entryH, 0x60FFD700);

            g.drawString(this.font,
                    entry.ownerName() + " #" + entry.index(),
                    cx + 4, ey + 3, sel ? 0xFFFF00 : 0xFFFFFF, false);
        }
        g.disableScissor();

        if (visitableEntries.isEmpty()) {
            g.drawString(this.font, "加载中...",
                    cx + cw / 2 - 16, cy + 132, 0x808080, false);
        }

        // 底部提示
        g.drawString(this.font, "§7选择空岛后点击传送",
                cx, cy + 248, 0x888888, false);
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        if (currentTab == TAB_VISIT) {
            int cx = this.leftPos + SIDEBAR_WIDTH + 8;
            int cw = this.imageWidth - SIDEBAR_WIDTH - 16;
            int listTop = this.topPos + 64;
            int listBottom = this.topPos + 210;

            if (mx >= cx && mx <= cx + cw && my >= listTop && my <= listBottom) {
                int entryH = 14;
                int idx = visitScrollOffset + (int) ((my - listTop) / entryH);
                if (idx >= 0 && idx < visitableEntries.size()) {
                    selectedVisitUuid = visitableEntries.get(idx).ownerUuid();
                    if (teleportButton != null) teleportButton.active = true;
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
            int cx = this.leftPos + SIDEBAR_WIDTH + 8;
            int cw = this.imageWidth - SIDEBAR_WIDTH - 16;
            int listTop = this.topPos + 64;
            int listBottom = this.topPos + 210;

            if (mx >= cx && mx <= cx + cw && my >= listTop && my <= listBottom) {
                int entryH = 14;
                int maxVisible = (listBottom - listTop) / entryH;
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
     * 修复"在 EditBox 中输入含 E 的英文单词时按 E 会关闭 GUI"的问题。
     * <p>
     * 当欢迎语输入框处于聚焦状态时，拦截 Inventory 快捷键（默认 E），
     * 阻止其传递给父类触发关闭逻辑。
     * </p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (greetingEditBox != null && greetingEditBox.isFocused()) {
            // 拦截 Inventory 键（默认 E）
            if (this.minecraft != null
                    && this.minecraft.options.keyInventory
                    .matches(keyCode, scanCode)) {
                return true; // 事件已消费，不传播
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
