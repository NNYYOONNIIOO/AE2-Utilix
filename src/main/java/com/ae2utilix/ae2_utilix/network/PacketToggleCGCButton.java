package com.ae2utilix.ae2_utilix.network;

import com.ae2utilix.ae2_utilix.block.TileCrystalGrowthChamber;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server switch for the crystal growth chamber's GUI buttons. */
public class PacketToggleCGCButton implements IMessage {

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_EJECT = 1;
    public static final int BUTTON_FACE_EJECT_START = 2;
    public static final int BUTTON_CLEAR_INPUT_FLUID = 100;
    public static final int BUTTON_CLEAR_OUTPUT_FLUID = 101;

    private BlockPos pos;
    private int buttonId;

    public PacketToggleCGCButton() {
    }

    public PacketToggleCGCButton(BlockPos pos, int buttonId) {
        this.pos = pos;
        this.buttonId = buttonId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.buttonId = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeByte(this.buttonId);
    }

    public static class Handler implements IMessageHandler<PacketToggleCGCButton, IMessage> {
        @Override
        public IMessage onMessage(PacketToggleCGCButton message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            ((WorldServer) player.world).addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (!(te instanceof TileCrystalGrowthChamber)) {
                    return;
                }
                TileCrystalGrowthChamber cgc = (TileCrystalGrowthChamber) te;
                if (message.buttonId == BUTTON_POWER) {
                    cgc.setPowered(!cgc.isPowered());
                } else if (message.buttonId == BUTTON_EJECT) {
                    cgc.setEjecting(!cgc.isEjecting());
                } else if (message.buttonId >= BUTTON_FACE_EJECT_START
                        && message.buttonId < BUTTON_FACE_EJECT_START + 6) {
                    int faceIdx = message.buttonId - BUTTON_FACE_EJECT_START;
                    cgc.setFaceEjecting(faceIdx, !cgc.isFaceEjecting(faceIdx));
                } else if (message.buttonId == BUTTON_CLEAR_INPUT_FLUID) {
                    cgc.setInputFluid(null);
                } else if (message.buttonId == BUTTON_CLEAR_OUTPUT_FLUID) {
                    cgc.setOutputFluid(null);
                }
            });
            return null;
        }
    }
}