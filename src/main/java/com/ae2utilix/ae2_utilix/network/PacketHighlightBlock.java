package com.ae2utilix.ae2_utilix.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Tells the client to draw a temporary outline around a linked block. */
public class PacketHighlightBlock implements IMessage {

    private BlockPos pos;
    private int dimension;

    public PacketHighlightBlock() {
    }

    public PacketHighlightBlock(BlockPos pos, int dimension) {
        this.pos = pos;
        this.dimension = dimension;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.dimension = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeInt(this.dimension);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getDimension() {
        return this.dimension;
    }

    public static class Handler implements IMessageHandler<PacketHighlightBlock, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketHighlightBlock message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(
                    () -> com.ae2utilix.ae2_utilix.client.HighlightRenderer.addHighlight(message.getPos(),
                            message.getDimension()));
            return null;
        }
    }
}