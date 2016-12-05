package com.netty.grpc.proxy.demo.handler;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameTypes;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Settings;

import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;

public class MockClientHandler extends ChannelInboundHandlerAdapter {
    public static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private final Http2FrameWriter writer = new DefaultHttp2FrameWriter();


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 建立连接
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        settings.pushEnabled(false);
        settings.maxConcurrentStreams(0);
        ByteBuf preface = Http2CodecUtil.connectionPrefaceBuf().retainedDuplicate();
        ctx.write(preface);
        writer.writeSettings(ctx, settings, ctx.newPromise());
        writer.writeWindowUpdate(ctx, 0, 983041, ctx.newPromise());
        ctx.flush();
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf)msg;
        System.out.println("channelRead:" + ByteBufUtil.hexDump((buf)));
        while (buf.readableBytes() > 0) {

            int payload = buf.readUnsignedMedium();
            int frameType = buf.readByte();
            Http2Flags flags = new Http2Flags(buf.readUnsignedByte());
            int streamId = readUnsignedInt(buf);
            ByteBuf payloadBuf = buf.readBytes(payload);
            ByteBuf copy = ctx.alloc().buffer();
            System.out.println("frame_type:" + frameType + ","  + ByteBufUtil.hexDump((payloadBuf)));
            switch (frameType){
                case Http2FrameTypes.SETTINGS:
                    handleSettingFrame(ctx, flags);
                    break;
                case Http2FrameTypes.WINDOW_UPDATE:
                    handleWindowsUpdateFrame(ctx);
                    break;
                case Http2FrameTypes.HEADERS:

                    copy.writeMedium(payload);
                    copy.writeByte(frameType);
                    copy.writeByte(flags.value());
                    copy.writeInt(streamId);
                    copy.writeBytes(payloadBuf);
                    ctx.fireChannelRead(copy);
                    break;
                case Http2FrameTypes.DATA:
                    copy.writeMedium(payload);
                    copy.writeByte(frameType);
                    copy.writeByte(flags.value());
                    copy.writeInt(streamId);
                    copy.writeBytes(payloadBuf);
                    ctx.fireChannelRead(copy);
                    break;
                default:
                    break;

            }
        }
    }

    private void handleSettingFrame(final ChannelHandlerContext ctx, Http2Flags flags){
        ByteBufAllocator alloc = ctx.alloc();
        ByteBuf byteBuf = alloc.buffer();
        if(!flags.ack()){

            //00 00 0c 04 00 00 00 00 00 00 03 7f ff ff ff 00
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x0c);
            byteBuf.writeByte(0x04);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x03);
            byteBuf.writeByte(0x7f);
            byteBuf.writeByte(0xff);
            byteBuf.writeByte(0xff);
            byteBuf.writeByte(0xff);
            byteBuf.writeByte(0x00);
            //04 00 10 00 00
            byteBuf.writeByte(0x04);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x10);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
        } else {
//            System.out.println("********************* setting ack received ...");
            //00 00 00 04 01 00 00 00 00
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x04);
            byteBuf.writeByte(0x01);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
            byteBuf.writeByte(0x00);
        }
        ctx.writeAndFlush(byteBuf).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
//                    System.out.println(" ...operationComplete isSuccess");
                    ctx.channel().read();
                } else {
//                    System.out.println("...operationComplete failure");
                    future.channel().close();
                }
            }
        });
    }
    private void handleWindowsUpdateFrame(final ChannelHandlerContext ctx){
        ByteBufAllocator alloc = ctx.alloc();
        ByteBuf byteBuf = alloc.buffer();
        // 00 00 04 08 00 00 00 00 00 00 0f 00 01
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x04);
        byteBuf.writeByte(0x08);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x0f);
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(0x01);
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
