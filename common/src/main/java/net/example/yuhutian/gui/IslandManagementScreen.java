package net.example.yuhutian.gui;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.network.AddFriendPayload;
import net.example.yuhutian.network.RemoveFriendPayload;
import net.example.yuhutian.network.RequestVisitableIslandsPayload;
import net.example.yuhutian.network.SyncVisitableIslandsPayload;
import net.example.yuhutian.network.TeleportToIslandPayload;
import net.example.yuhutian.network.ToggleBorderPayload;
import net.example.yuhutian.network.UpdateGreetingPayload;
import net.example.yuhutian.world.IslandInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 空岛管理面板的客户端渲染 Screen。
 * <p>
 * 使用 {@link CycleButton} 循环按钮替代旧版 EditBox，
 * 从服务端同步的在线玩家列表中选择目标玩家，彻底杜绝拼写错误。
 * </p>
 * <p>
 * 所有 Widget 在 init() 中创建一次，避免 render() 中每帧重建导致内存泄漏。
 * </p>
 */
public class IslandManagementScreen extends AbstractContainerScreen<IslandManagementMenu> {

    private CycleButton<UUID> playerDropdown;
    private Button addButton;
    private final List<Button> removeButtons = new ArrayList<>();

    /** 当前可被添加的在线玩家 UUID 列表（排除岛主） */
    private List<UUID> availablePlayerUuids = new ArrayList<>();
    /** 玩家 UUID → 名字的映射 */
    private Map<UUID, String> playerNameMap = Map.of();

    /** 欢迎寄语输入框 */
    private EditBox greetingEditBox;
    /** 当前选中的音效 ResourceLocation 字符串 */
    private String selectedSound;

    /** 预设音效选项：显示名 → ResourceLocation 字符串 */
    private static final String[][] SOUND_OPTIONS = {
            {"成就达成", "minecraft:entity.player.levelup"},
            {"钟声", "minecraft:block.bell.use"},
            {"竖琴", "minecraft:block.note_block.harp"},
            {"铃声", "minecraft:block.note_block.bell"},
            {"木琴", "minecraft:block.note_block.xylophone"},
            {"猫叫", "minecraft:entity.cat.purr"},
    };

    /** S2C 拜访列表缓冲区：SyncVisitableIslandsPayload 写入，init() 读取 */
    public static List<SyncVisitableIslandsPayload.IslandEntry> visitPendingData = null;

    /** 可拜访的空岛条目列表 */
    private List<SyncVisitableIslandsPayload.IslandEntry> visitableEntries = new ArrayList<>();
    /** 当前选中的拜访目标岛主 UUID */
    private UUID selectedVisitUuid = null;
    /** 拜访列表滚动偏移量（条目数） */
    private int visitScrollOffset = 0;
    /** 传送按钮引用（用于启用/禁用） */
    private Button teleportButton;

    public IslandManagementScreen(IslandManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 470;
    }

    @Override
    protected void init() {
        super.init();
        removeButtons.clear();

        int guiLeft = this.leftPos;
        int guiTop = this.topPos;

        // 从 Menu 获取服务端同步的在线玩家数据（已排除岛主）
        this.playerNameMap = this.menu.getOnlinePlayers();
        this.availablePlayerUuids = new ArrayList<>(playerNameMap.keySet());

        // 在线玩家下拉选择按钮
        if (!availablePlayerUuids.isEmpty()) {
            this.playerDropdown = CycleButton.<UUID>builder(uuid ->
                            Component.literal(playerNameMap.getOrDefault(uuid, "???")))
                    .withValues(availablePlayerUuids)
                    .withInitialValue(availablePlayerUuids.get(0))
                    .create(guiLeft + 10, guiTop + 118, 120, 20, Component.empty());
            this.addRenderableWidget(this.playerDropdown);
        } else {
            // 没有其他在线玩家时显示禁用按钮
            UUID placeholder = UUID.randomUUID();
            this.playerDropdown = CycleButton.<UUID>builder(uuid -> Component.empty())
                    .withValues(placeholder)
                    .withInitialValue(placeholder)
                    .create(guiLeft + 10, guiTop + 118, 120, 20, Component.empty());
            this.playerDropdown.active = false;
            this.playerDropdown.setTooltip(
                    Tooltip.create(Component.literal("当前无其他在线玩家")));
            this.addRenderableWidget(this.playerDropdown);
        }

        // "添加权限"按钮
        this.addButton = Button.builder(Component.literal("添加"), button -> onAddClicked())
                .pos(guiLeft + 135, guiTop + 117)
                .size(70, 22)
                .build();
        this.addButton.active = !availablePlayerUuids.isEmpty();
        this.addRenderableWidget(this.addButton);

        // 为每个信任玩家创建"删除"按钮（仅在 init 时创建一次）
        int y = guiTop + 82;
        for (UUID uuid : this.menu.getMenuAllowedPlayers()) {
            Button removeBtn = Button.builder(Component.literal("×"),
                    button -> onRemoveClicked(uuid))
                    .pos(guiLeft + 185, y - 2)
                    .size(20, 16)
                    .build();
            this.addRenderableWidget(removeBtn);
            removeButtons.add(removeBtn);
            y += 20;
            if (y > guiTop + 180) break;
        }

        // 领地边界显示切换按钮
        CycleButton<Boolean> borderToggle = CycleButton.<Boolean>builder(state ->
                        Component.literal(state ? "§a显示边界: ON" : "§c显示边界: OFF"))
                .withValues(true, false)
                .withInitialValue(this.menu.isShowBorder())
                .withTooltip(state -> Tooltip.create(Component.literal("开启后靠近领地边界时显示粒子墙")))
                .create(guiLeft + 10, guiTop + 198, 200, 20, Component.empty(),
                        (button, newState) -> {
                            NetworkManager.sendToServer(new ToggleBorderPayload(newState));
                        });
        this.addRenderableWidget(borderToggle);

        // ===== 欢迎寄语编辑区 =====
        this.selectedSound = this.menu.getGreetingSound();

        // 寄语输入框
        this.greetingEditBox = new EditBox(this.font, guiLeft + 10, guiTop + 240, 200, 20,
                Component.literal(""));
        this.greetingEditBox.setMaxLength(128);
        this.greetingEditBox.setValue(this.menu.getGreetingText());
        this.addRenderableWidget(this.greetingEditBox);

        // 音效选择下拉按钮（用 Integer 索引映射到 SOUND_OPTIONS）
        int initialSoundIndex = 0;
        for (int i = 0; i < SOUND_OPTIONS.length; i++) {
            if (SOUND_OPTIONS[i][1].equals(this.selectedSound)) {
                initialSoundIndex = i;
                break;
            }
        }
        List<Integer> soundIndices = new ArrayList<>();
        for (int i = 0; i < SOUND_OPTIONS.length; i++) soundIndices.add(i);

        CycleButton<Integer> soundDropdown = CycleButton.<Integer>builder(idx ->
                        Component.literal(SOUND_OPTIONS[idx][0]))
                .withValues(soundIndices)
                .withInitialValue(initialSoundIndex)
                .create(guiLeft + 10, guiTop + 274, 120, 20, Component.literal("音效:"),
                        (button, newIdx) -> {
                            selectedSound = SOUND_OPTIONS[newIdx][1];
                        });
        this.addRenderableWidget(soundDropdown);

        // 保存按钮
        Button saveGreetingBtn = Button.builder(Component.literal("保存寄语"), button -> {
            String text = greetingEditBox.getValue();
            if (text.isEmpty()) text = IslandInfo.DEFAULT_GREETING_TEXT;
            NetworkManager.sendToServer(new UpdateGreetingPayload(text, selectedSound));
        }).pos(guiLeft + 135, guiTop + 273).size(75, 22).build();
        this.addRenderableWidget(saveGreetingBtn);

        // ===== 拜访他人空岛区域 =====
        // 从 S2C 缓冲区加载可拜访列表（如果有待读取数据）
        if (visitPendingData != null) {
            this.visitableEntries = new ArrayList<>(visitPendingData);
            visitPendingData = null;
        }

        // 传送按钮
        this.teleportButton = Button.builder(Component.literal("传送"), button -> {
            if (selectedVisitUuid != null) {
                NetworkManager.sendToServer(new TeleportToIslandPayload(selectedVisitUuid));
            }
        }).pos(guiLeft + 60, guiTop + 426).size(100, 20).build();
        this.teleportButton.active = selectedVisitUuid != null;
        this.addRenderableWidget(this.teleportButton);

        // 向服务端请求最新的可拜访空岛列表
        NetworkManager.sendToServer(new RequestVisitableIslandsPayload());
    }

    private void onAddClicked() {
        if (availablePlayerUuids.isEmpty()) return;
        if (playerDropdown == null) return;
        UUID selectedUuid = playerDropdown.getValue();
        NetworkManager.sendToServer(new AddFriendPayload(selectedUuid));
    }

    private void onRemoveClicked(UUID playerUuid) {
        NetworkManager.sendToServer(new RemoveFriendPayload(playerUuid));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 半透明深色背景
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight,
                0xCC1A1A2E);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int guiLeft = this.leftPos;
        int guiTop = this.topPos;

        // 标题
        graphics.drawString(this.font, "§l空岛管理面板",
                guiLeft + 10, guiTop + 10, 0xFFD700, false);

        // 空岛坐标
        graphics.drawString(this.font,
                "坐标: (" + this.menu.getIslandX() + ", " + this.menu.getIslandZ() + ")",
                guiLeft + 10, guiTop + 28, 0xFFFFFF, false);

        // 主人名称
        graphics.drawString(this.font,
                "主人: " + this.menu.getOwnerName(),
                guiLeft + 10, guiTop + 42, 0xFFFFFF, false);

        // 分隔线
        graphics.fill(guiLeft + 10, guiTop + 56, guiLeft + 210, guiTop + 57, 0x80FFFFFF);

        // 信任玩家列表标题
        graphics.drawString(this.font, "§n信任玩家:",
                guiLeft + 10, guiTop + 62, 0xAAAAAA, false);

        // 渲染信任玩家 UUID 文本（按钮已在 init 中创建）
        int y = guiTop + 82;
        List<UUID> players = this.menu.getMenuAllowedPlayers();
        if (players.isEmpty()) {
            graphics.drawString(this.font, "(暂无)", guiLeft + 12, y, 0x808080, false);
        } else {
            for (UUID uuid : players) {
                String display = uuid.toString().substring(0, 8) + "...";
                graphics.drawString(this.font, display, guiLeft + 12, y, 0xFFFFFF, false);
                y += 20;
                if (y > guiTop + 180) break;
            }
        }

        // 下拉按钮上方标签
        graphics.drawString(this.font, "添加在线玩家:",
                guiLeft + 10, guiTop + 106, 0xAAAAAA, false);

        // ===== 欢迎寄语区标签 =====
        // 分隔线
        graphics.fill(guiLeft + 10, guiTop + 222, guiLeft + 210, guiTop + 223, 0x80FFFFFF);

        graphics.drawString(this.font, "§n欢迎寄语:",
                guiLeft + 10, guiTop + 228, 0xAAAAAA, false);

        // ===== 拜访他人空岛区域渲染 =====
        // 分隔线
        graphics.fill(guiLeft + 10, guiTop + 302, guiLeft + 210, guiTop + 303, 0x80FFFFFF);

        // 区域标题
        graphics.drawString(this.font, "§n拜访他人空岛:",
                guiLeft + 10, guiTop + 308, 0xAAAAAA, false);

        // 列表背景
        graphics.fill(guiLeft + 10, guiTop + 322, guiLeft + 210, guiTop + 422, 0x30FFFFFF);

        // 检查 S2C 异步返回的数据
        if (visitPendingData != null) {
            this.visitableEntries = new ArrayList<>(visitPendingData);
            visitPendingData = null;
            // 重新启用传送按钮
            if (teleportButton != null) {
                teleportButton.active = selectedVisitUuid != null;
            }
        }

        // 渲染可见条目（使用裁剪防止溢出列表区域）
        int listTop = guiTop + 322;
        int listBottom = guiTop + 422;
        int entryHeight = 14;
        graphics.enableScissor(guiLeft + 10, listTop, guiLeft + 210, listBottom);
        for (int i = visitScrollOffset; i < visitableEntries.size(); i++) {
            int entryY = listTop + (i - visitScrollOffset) * entryHeight;
            if (entryY + entryHeight > listBottom) break;

            SyncVisitableIslandsPayload.IslandEntry entry = visitableEntries.get(i);
            boolean isSelected = Objects.equals(entry.ownerUuid(), selectedVisitUuid);

            // 选中高亮
            if (isSelected) {
                graphics.fill(guiLeft + 10, entryY, guiLeft + 210, entryY + entryHeight, 0x60FFD700);
            }

            // 条目文本：岛主名 + 空岛编号
            graphics.drawString(this.font,
                    entry.ownerName() + " #" + entry.index(),
                    guiLeft + 14, entryY + 3,
                    isSelected ? 0xFFFF00 : 0xFFFFFF, false);
        }
        graphics.disableScissor();

        // 空列表提示
        if (visitableEntries.isEmpty()) {
            graphics.drawString(this.font, "加载中...",
                    guiLeft + 80, guiTop + 368, 0x808080, false);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // 拜访列表点击检测
        int listLeft = this.leftPos + 10;
        int listRight = this.leftPos + 210;
        int listTop = this.topPos + 322;
        int listBottom = this.topPos + 422;

        if (mouseX >= listLeft && mouseX <= listRight
                && mouseY >= listTop && mouseY <= listBottom) {
            int entryHeight = 14;
            int idx = visitScrollOffset + (int) ((mouseY - listTop) / entryHeight);
            if (idx >= 0 && idx < visitableEntries.size()) {
                selectedVisitUuid = visitableEntries.get(idx).ownerUuid();
                if (teleportButton != null) {
                    teleportButton.active = true;
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        // 拜访列表滚动处理
        int listLeft = this.leftPos + 10;
        int listRight = this.leftPos + 210;
        int listTop = this.topPos + 322;
        int listBottom = this.topPos + 422;

        if (mouseX >= listLeft && mouseX <= listRight
                && mouseY >= listTop && mouseY <= listBottom) {
            int entryHeight = 14;
            int maxVisible = (listBottom - listTop) / entryHeight;
            int maxOffset = Math.max(0, visitableEntries.size() - maxVisible);
            visitScrollOffset = (int) Math.max(0, Math.min(maxOffset, visitScrollOffset - scrollY));
            return true;
        }

        return false;
    }
}
