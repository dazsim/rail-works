package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.CokeOvenBlockEntity;
import com.workshopcraft.railworks.menu.CokeOvenMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class CokeOvenScreen extends AbstractContainerScreen<CokeOvenMenu> {
    private static final Identifier TEXTURE = RailWorks.id("textures/gui/coke_oven.png");
    private static final Identifier LIT = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier BURN = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final int TANK_X = 127, TANK_Y = 18, TANK_W = 16, TANK_H = 50;

    public CokeOvenScreen(CokeOvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.isBurning()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT, 14, 14, 0, 0, x + 45, y + 54, 14, 14);
        }
        int w = Mth.ceil(menu.progress() * 24);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN, 24, 16, 0, 0, x + 66, y + 34, w, 16);
        int h = Mth.ceil((float) menu.tank() / CokeOvenBlockEntity.CAPACITY * TANK_H);
        if (h > 0) {
            int bottom = y + TANK_Y + TANK_H;
            graphics.fill(x + TANK_X, bottom - h, x + TANK_X + TANK_W, bottom, 0xFF2A1A0C);
            for (int row = bottom - h + 3; row < bottom; row += 4) {
                graphics.fill(x + TANK_X + 2 + (row % 3), row, x + TANK_X + 7 + (row % 3), row + 1, 0xFF3A2612);
            }
            graphics.fill(x + TANK_X, bottom - h, x + TANK_X + TANK_W, bottom - h + 1, 0xFF6A4A22);
        }
        for (int i = 1; i < 4; i++) {
            int ty = y + TANK_Y + TANK_H - TANK_H * i / 4;
            graphics.fill(x + TANK_X, ty, x + TANK_X + 4, ty + 1, 0xFFB0B0B0);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int rx = mouseX - leftPos;
        int ry = mouseY - topPos;
        if (rx >= TANK_X && rx < TANK_X + TANK_W && ry >= TANK_Y && ry < TANK_Y + TANK_H) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.railworks.tank", menu.tank(), CokeOvenBlockEntity.CAPACITY), mouseX, mouseY);
        }
    }
}
