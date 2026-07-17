package net.example.yuhutian.gui;

import net.example.yuhutian.network.AddFriendPayload;
import net.example.yuhutian.network.RemoveFriendPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 空岛管理面板的客户端渲染 Screen。
 * <p>
 * 所有 Widget 在 init() 中创建一次，避免 render() 中每帧重建导致内存泄漏。
 * </p>
 */
public class IslandManagementScreen extends AbstractContainerScreen<IslandManagementMenu> {

    private EditBox nameInput;
    private Button addButton;
    private final List<Button> removeButtons = new ArrayList<>();

    public IslandManagementScreen(IslandManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        removeButtons.clear();

        int guiLeft = this.leftPos;
        int guiTop = this.topPos;

        // 文本输入框
        this.nameInput = new EditBox(this.font, guiLeft + 10, guiTop + 120, 120, 20,
                Component.literal(""));
        this.nameInput.setMaxLength(16);
        this.addRenderableWidget(this.nameInput);

        // "添加"按钮
        this.addButton = Button.builder(Component.literal("添加"), button -> onAddClicked())
                .pos(guiLeft + 135, guiTop + 119)
                .size(70, 22)
                .build();
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
    }

    private void onAddClicked() {
        String name = this.nameInput.getValue().trim();
        if (!name.isEmpty() && this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(
                    new ServerboundCustomPayloadPacket(new AddFriendPayload(name)));
            this.nameInput.setValue("");
        }
    }

    private void onRemoveClicked(UUID playerUuid) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(
                    new ServerboundCustomPayloadPacket(new RemoveFriendPayload(playerUuid)));
        }
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

        // 输入框提示
        if (this.nameInput.getValue().isEmpty()) {
            graphics.drawString(this.font, "输入玩家名...",
                    guiLeft + 14, guiTop + 126, 0x606060, false);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
