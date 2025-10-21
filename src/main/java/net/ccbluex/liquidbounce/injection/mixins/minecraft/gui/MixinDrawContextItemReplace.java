/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.injection.mixins.minecraft.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleItemImageReplace;
import net.ccbluex.liquidbounce.render.RenderShortcutsKt;
import net.ccbluex.liquidbounce.render.GUIRenderEnvironment;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class MixinDrawContextItemReplace {

    @Shadow @Final private MatrixStack matrices;

    /**
     * Replace GUI item icons with custom images if configured.
     */
    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    private void liquid_bounce$replaceItemIconSeeded(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        renderOverrideIfPresent(stack, x, y, ci);
    }

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void liquid_bounce$replaceItemIcon(ItemStack stack, int x, int y, CallbackInfo ci) {
        renderOverrideIfPresent(stack, x, y, ci);
    }

    private void renderOverrideIfPresent(ItemStack stack, int x, int y, CallbackInfo ci) {
        final @Nullable Identifier texture = ModuleItemImageReplace.INSTANCE.findCustomTexture(stack);
        if (texture == null) {
            return;
        }

        // Draw a textured quad using our custom texture
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        RenderShortcutsKt.renderEnvironmentForGUI(this.matrices, (GUIRenderEnvironment env) -> {
            RenderShortcutsKt.drawCustomMesh(env, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR, ShaderProgramKeys.POSITION_TEX_COLOR, (buffer, Matrix4f matrix) -> {
                float x1 = (float) x;
                float y1 = (float) y;
                float x2 = x1 + 16.0f;
                float y2 = y1 + 16.0f;

                buffer.vertex(matrix, x1, y2, 0.0f).texture(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
                buffer.vertex(matrix, x2, y2, 0.0f).texture(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
                buffer.vertex(matrix, x2, y1, 0.0f).texture(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
                buffer.vertex(matrix, x1, y1, 0.0f).texture(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
                return null;
            });
            return null;
        });

        ci.cancel();
    }
}
