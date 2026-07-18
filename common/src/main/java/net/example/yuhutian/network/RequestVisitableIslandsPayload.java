package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 网络包：请求可拜访的空岛列表。
 * <p>
 * 客户端在打开管理面板的"拜访"区域时发送此空包，
 * 服务端筛选出请求者拥有或被信任的空岛后，
 * 通过 {@link SyncVisitableIslandsPayload} 将列表回传。
 * </p>
 */
public record RequestVisitableIslandsPayload() implements CustomPacketPayload {

    public static final Type<RequestVisitableIslandsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "request_visitable_islands"));

    /**
     * 空包编解码器：编码时不写入任何数据，解码时创建空实例。
     * 注意：不能使用 StreamCodec.unit()，因为它在编码时通过 equals() 比较实例，
     * 发送 new 实例时必然不等导致 IllegalStateException。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestVisitableIslandsPayload> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new RequestVisitableIslandsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
