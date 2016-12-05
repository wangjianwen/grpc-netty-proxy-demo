/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.netty.grpc.proxy.demo.handler;

import com.netty.grpc.proxy.demo.handler.parser.Http2HeaderParser;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameTypes;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Settings;

import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;

@ChannelHandler.Sharable
class GrpcProxyBackendHandler extends ChannelInboundHandlerAdapter {
    public static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private final Channel inboundChannel;
    private final Http2FrameWriter writer = new DefaultHttp2FrameWriter();
    private final Http2FrameReader reader = new DefaultHttp2FrameReader();
    private boolean first = true;
    private int streamId;
    private DefaultHttp2HeadersEncoder encoder = new DefaultHttp2HeadersEncoder();
    private Http2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder();
    Http2HeaderParser parser = new Http2HeaderParser();

    public GrpcProxyBackendHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

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
        ByteBuf byteBuf = buf.copy();
        //System.out.println("channelRead:" + ByteBufUtil.hexDump((buf)));
        while (buf.readableBytes() > 0) {

            int payload = buf.readUnsignedMedium();
            int frameType = buf.readByte();
            Http2Flags flags = new Http2Flags(buf.readUnsignedByte());
            int streamId = readUnsignedInt(buf);
            ByteBuf payloadBuf = buf.readBytes(payload);
            ByteBuf copy = ctx.alloc().buffer();
            //System.out.println("frame_type:" + frameType + ","  + ByteBufUtil.hexDump((payloadBuf)));
            switch (frameType){
                case Http2FrameTypes.SETTINGS:
                    handleSettingFrame(ctx, flags);
                    break;
                case Http2FrameTypes.WINDOW_UPDATE:
                    handleWindowsUpdateFrame(ctx);
                    break;
                case Http2FrameTypes.HEADERS:
                    try {
                        reader.readFrame(ctx, byteBuf, new Http2FrameAdapter(){
                            @Override
                            public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                    int padding, boolean endStream) throws Http2Exception {
                                System.out.println("................." + headers);
                            }
                        });
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                    copy.writeMedium(payload);
                    copy.writeByte(frameType);
                    copy.writeByte(flags.value());
                    copy.writeInt(this.streamId);
                    copy.writeBytes(payloadBuf);
                    System.out.println("+++++++++++++++++++++++++headers:" + ByteBufUtil.hexDump(copy));
                    forward(ctx, copy);
                    break;
                case Http2FrameTypes.DATA:
                    copy.writeMedium(payload);
                    copy.writeByte(frameType);
                    copy.writeByte(flags.value());
                    copy.writeInt(this.streamId);
                    copy.writeBytes(payloadBuf);
                    forward(ctx, copy);
                    break;
                default:
                    break;

            }
        }

    }

    private void forward(final ChannelHandlerContext ctx, ByteBuf byteBuf){
        if(inboundChannel.isActive()){
            inboundChannel.writeAndFlush(byteBuf).addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        } else {
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


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        GrpcProxyFrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        GrpcProxyFrontendHandler.closeOnFlush(ctx.channel());
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }
}
