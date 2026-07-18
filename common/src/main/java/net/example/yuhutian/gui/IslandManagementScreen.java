package net.example.yuhutian.gui;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.network.AddFriendPayload;
import net.example.yuhutian.network.RemoveFriendPayload;
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

    public IslandManagementScreen(IslandManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 340;
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

        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
