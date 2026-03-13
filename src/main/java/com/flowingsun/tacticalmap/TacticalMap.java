package com.flowingsun.tacticalmap;

import com.flowingsun.tacticalmap.network.WaypointSyncPacket;
import com.flowingsun.tacticalmap.util.TeamUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

@Mod(TacticalMap.MODID)
public class TacticalMap {
    public static final String MODID = "tacticalmap";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "1";

    /**
     * 网络通道
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * 防止同步死循环的标志位
     */
    public static final ThreadLocal<Boolean> IS_SYNCING = ThreadLocal.withInitial(() -> false);

    public TacticalMap() {
        // 注册网络数据包
        int id = 0;
        CHANNEL.registerMessage(id++,
                WaypointSyncPacket.class,
                WaypointSyncPacket::encode,
                WaypointSyncPacket::new,
                WaypointSyncPacket::handle);

        // 注册到 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("TacticalMap: 实时战术同步系统已启动");
    }

    /**
     * 核心实时同步逻辑
     */
    public static void broadcastToTeammates(ServerPlayer sender, WaypointSyncPacket packet) {
        if (sender.getServer() == null) return;

        sender.getServer().getPlayerList().getPlayers().forEach(target -> {
            // 排除发送者本人
            if (!target.getUUID().equals(sender.getUUID())) {
                // 实时判定队伍关系
                if (TeamUtils.areOnSameTeam(sender, target)) {
                    LOGGER.info("正在同步路径点 [{}] 从 {} 到队友 {}",
                            packet.getName(), sender.getName().getString(), target.getName().getString());

                    CHANNEL.sendTo(packet, target.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                }
            }
        });
    }
}