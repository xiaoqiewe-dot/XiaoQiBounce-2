// src/main/java/net/ccbluex/liquidbounce/mixin/accessor/IMixinEntityVelocityUpdateS2CPacket.java
package net.ccbluex.liquidbounce.mixin.accessor;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityVelocityUpdateS2CPacket.class)
public interface IMixinEntityVelocityUpdateS2CPacket {
    @Accessor("velocityX")
    int getVelocityX();

    @Accessor("velocityX")
    void setVelocityX(int x);

    @Accessor("velocityY")
    int getVelocityY();

    @Accessor("velocityY")
    void setVelocityY(int y);

    @Accessor("velocityZ")
    int getVelocityZ();

    @Accessor("velocityZ")
    void setVelocityZ(int z);
}
