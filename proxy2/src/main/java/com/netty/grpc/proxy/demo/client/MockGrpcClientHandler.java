package com.netty.grpc.proxy.demo.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;


/**
 * Created by Administrator on 2016/11/27.
 */
public class MockGrpcClientHandler extends ChannelInboundHandlerAdapter {
    public static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private final Http2FrameWriter writer = new DefaultHttp2FrameWriter();
    private boolean fisrt = true;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        settings.pushEnabled(false);
        settings.maxConcurrentStreams(0);
        ByteBuf preface = Http2CodecUtil.connectionPrefaceBuf().retainedDuplicate();
        ctx.write(preface);
        writer.writeSettings(ctx, settings, ctx.newPromise());
        writer.writeWindowUpdate(ctx, 0, 983041, ctx.newPromise());
        ctx.flush();

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.print(ByteBufUtil.hexDump((ByteBuf) msg));
        //super.channelRead(ctx, msg);
        if (fisrt) {
            writer.writeSettingsAck(ctx, ctx.newPromise());
            fisrt = false;
        }

        readFrame(ctx, (ByteBuf) msg);

    }

    private void readFrame(final ChannelHandlerContext ctx, ByteBuf buf) {
        while (buf.readableBytes() > 0) {
            int payload = buf.readUnsignedMedium();
            int frameType = buf.readByte();
            Http2Flags flags = new Http2Flags(buf.readUnsignedByte());
            int streamId = readUnsignedInt(buf);
            buf.readBytes(payload);
            if (frameType == Http2FrameTypes.SETTINGS && flags.ack()) {
                System.out.print("********************* setting ack received ....");



                ByteBufAllocator alloc = ctx.alloc();
                ByteBuf byteBuf = alloc.buffer();
                //00 00 67 01 24 00 00 00 03 00 00 00 00 0f 41 8a
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x67);
                byteBuf.writeByte(0x01);
                byteBuf.writeByte(0x24);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x03);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x0f);
                byteBuf.writeByte(0x41);
                byteBuf.writeByte(0x8a);
                // a0 e4 1d 13 9d 09 b8 f3 4d 33 44 95 62 72 d1 41
                byteBuf.writeByte(0xa0);
                byteBuf.writeByte(0xe4);
                byteBuf.writeByte(0x1d);
                byteBuf.writeByte(0x13);
                byteBuf.writeByte(0x9d);
                byteBuf.writeByte(0x09);
                byteBuf.writeByte(0xb8);
                byteBuf.writeByte(0xf3);
                byteBuf.writeByte(0x4d);
                byteBuf.writeByte(0x33);
                byteBuf.writeByte(0x44);
                byteBuf.writeByte(0x95);
                byteBuf.writeByte(0x62);
                byteBuf.writeByte(0x72);
                byteBuf.writeByte(0xd1);
                byteBuf.writeByte(0x41);
                // fc 1e ca 24 5f 15 85 2a 4b 63 1b 87 eb 19 68 a0
                byteBuf.writeByte(0xfc);
                byteBuf.writeByte(0x1e);
                byteBuf.writeByte(0xca);
                byteBuf.writeByte(0x24);
                byteBuf.writeByte(0x5f);
                byteBuf.writeByte(0x15);
                byteBuf.writeByte(0x85);
                byteBuf.writeByte(0x2a);
                byteBuf.writeByte(0x4b);
                byteBuf.writeByte(0x63);
                byteBuf.writeByte(0x1b);
                byteBuf.writeByte(0x87);
                byteBuf.writeByte(0xeb);
                byteBuf.writeByte(0x19);
                byteBuf.writeByte(0x68);
                byteBuf.writeByte(0xa0);
                // ff 83 86 5f 8b 1d 75 d0 62 0d 26 3d 4c 4d 65 64
                byteBuf.writeByte(0xff);
                byteBuf.writeByte(0x83);
                byteBuf.writeByte(0x86);
                byteBuf.writeByte(0x5f);
                byteBuf.writeByte(0x8b);
                byteBuf.writeByte(0x1d);
                byteBuf.writeByte(0x75);
                byteBuf.writeByte(0xd0);
                byteBuf.writeByte(0x62);
                byteBuf.writeByte(0x0d);
                byteBuf.writeByte(0x26);
                byteBuf.writeByte(0x3d);
                byteBuf.writeByte(0x4c);
                byteBuf.writeByte(0x4d);
                byteBuf.writeByte(0x65);
                byteBuf.writeByte(0x64);
                // 40 02 74 65 86 4d 83 35 05 b1 1f 7a 8f 9a ca c8
                byteBuf.writeByte(0x40);
                byteBuf.writeByte(0x02);
                byteBuf.writeByte(0x74);
                byteBuf.writeByte(0x65);
                byteBuf.writeByte(0x86);
                byteBuf.writeByte(0x4d);
                byteBuf.writeByte(0x83);
                byteBuf.writeByte(0x35);
                byteBuf.writeByte(0x05);
                byteBuf.writeByte(0xb1);
                byteBuf.writeByte(0x1f);
                byteBuf.writeByte(0x7a);
                byteBuf.writeByte(0x8f);
                byteBuf.writeByte(0x9a);
                byteBuf.writeByte(0xca);
                byteBuf.writeByte(0xc8);
                // b7 41 f7 1a d5 15 29 f4 c0 57 02 e0 40 8e 9a ca
                byteBuf.writeByte(0xb7);
                byteBuf.writeByte(0x41);
                byteBuf.writeByte(0xf7);
                byteBuf.writeByte(0x1a);
                byteBuf.writeByte(0xd5);
                byteBuf.writeByte(0x15);
                byteBuf.writeByte(0x29);
                byteBuf.writeByte(0xf4);
                byteBuf.writeByte(0xc0);
                byteBuf.writeByte(0x57);
                byteBuf.writeByte(0x02);
                byteBuf.writeByte(0xe0);
                byteBuf.writeByte(0x40);
                byteBuf.writeByte(0x8e);
                byteBuf.writeByte(0x9a);
                byteBuf.writeByte(0xca);
                //c8 b0 c8 42 d6 95 8b 51 0f 21 aa 9b 83 9b d9 ab
                byteBuf.writeByte(0xc8);
                byteBuf.writeByte(0xb0);
                byteBuf.writeByte(0xc8);
                byteBuf.writeByte(0x42);
                byteBuf.writeByte(0xd6);
                byteBuf.writeByte(0x95);
                byteBuf.writeByte(0x8b);
                byteBuf.writeByte(0x51);
                byteBuf.writeByte(0x0f);
                byteBuf.writeByte(0x21);
                byteBuf.writeByte(0xaa);
                byteBuf.writeByte(0x9b);
                byteBuf.writeByte(0x83);
                byteBuf.writeByte(0x9b);
                byteBuf.writeByte(0xd9);
                byteBuf.writeByte(0xab);

               //data
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x0e);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x01);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x03);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x00);
                byteBuf.writeByte(0x09);
                byteBuf.writeByte(0x0a);
                byteBuf.writeByte(0x07);
                byteBuf.writeByte(0x77);
                byteBuf.writeByte(0x6f);
                byteBuf.writeByte(0x72);
                byteBuf.writeByte(0x6c);
                byteBuf.writeByte(0x64);
                byteBuf.writeByte(0x5f);
                byteBuf.writeByte(0x30);
                ctx.writeAndFlush(byteBuf).addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) {
                        if (future.isSuccess()) {
                            ctx.channel().read();
                        } else {
                            future.channel().close();
                        }
                    }
                });



            }
        }
    }

    private boolean isSettingAck(ByteBuf buf) {

        int payload = buf.readUnsignedMedium();
        int type = buf.readByte();
        return buf.readBoolean();
    }

    private static void writeFrameHeaderInternal(ByteBuf out, int payloadLength, byte type,
            Http2Flags flags, int streamId) {
        out.writeMedium(payloadLength);
        out.writeByte(type);
        out.writeByte(flags.value());
        out.writeInt(streamId);
    }

}
