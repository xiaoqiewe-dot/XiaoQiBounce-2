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
package net.ccbluex.liquidbounce.injection.mixins;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class LiquidBounceMixinPlugin implements IMixinConfigPlugin {

    private static final Map<String, String> CONDITIONAL_MIXINS = Map.of(
            "net.ccbluex.liquidbounce.injection.mixins.lithium.MixinChunkAwareBlockCollisionSweeper", "lithium",
            "net.ccbluex.liquidbounce.injection.mixins.sodium.MixinSodiumBlockOcclusionCache", "sodium",
            "net.ccbluex.liquidbounce.injection.mixins.sodium.MixinSodiumLightDataAccessMixin", "sodium"
    );

    @Override
    public void onLoad(String mixinPackage) {
        // unused
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        final String requiredMod = CONDITIONAL_MIXINS.get(mixinClassName);
        return requiredMod == null || FabricLoader.getInstance().isModLoaded(requiredMod);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // unused
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // unused
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // unused
    }
}
