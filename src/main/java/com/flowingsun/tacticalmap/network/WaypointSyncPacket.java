package com.flowingsun.tacticalmap.network;

import com.flowingsun.tacticalmap.TacticalMap;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WaypointSyncPacket {
    public enum Action { ADD, DELETE }
    private final Action action;
    private final String name;
    private final int x, y, z;

    public WaypointSyncPacket(Waypoint wp, Action action) {
        this.action = action;
        this.name = wp.getName();
        this.x = wp.getPos().getX();
        this.y = wp.getPos().getY();
        this.z = wp.getPos().getZ();
    }

    public WaypointSyncPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.name = buf.readUtf();
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(name);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                // 服务端逻辑：转发给队友
                TacticalMap.broadcastToTeammates(sender, this);
            } else {
                // 客户端逻辑：实时应用更改
                FTBChunksAPI.clientApi().getWaypointManager().ifPresent(manager -> {
                    // 开启同步标志，防止 Mixin 再次拦截导致无限发包
                    TacticalMap.IS_SYNCING.set(true);
                    try {
                        if (this.action == Action.ADD) {
                            manager.addWaypointAt(new BlockPos(x, y, z), name);
                        } else {
                            manager.getAllWaypoints().stream()
                                    .filter(wp -> wp.getPos().getX() == x && wp.getPos().getZ() == z)
                                    .findFirst()
                                    .ifPresent(manager::removeWaypoint);
                        }
                    } finally {
                        TacticalMap.IS_SYNCING.set(false);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getName() { return name; }
}