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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;

import static io.netty.buffer.ByteBufUtil.hexDump;
import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static java.lang.Math.min;

public class GrpcProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private final String[] remoteHosts;
    private final int[] remotePorts;
    private final Channel[] outboundChannels;
    private boolean first = true;

    public GrpcProxyFrontendHandler(String[] remoteHosts, int[] remotePorts) {
        this.remoteHosts = remoteHosts;
        this.remotePorts = remotePorts;
        this.outboundChannels = new Channel[remoteHosts.length];
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();

        // 建立与远程服务器的联系
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new GrpcProxyBackendHandler(inboundChannel))
                .option(ChannelOption.AUTO_READ, false);

        for(int i = 0; i < remoteHosts.length; i++){
            final ChannelFuture f = b.connect(remoteHosts[i], remotePorts[i]);
            outboundChannels[i] = f.channel();
            f.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        // connection complete start to read first data
                        inboundChannel.read();
                        System.out.println(f.channel().remoteAddress() + ", " + f.channel().localAddress());
                    } else {
                        // Close the connection if the connection attempt has failed.
                        System.out.println("channelActive close" + inboundChannel.remoteAddress() + ", " + inboundChannel.localAddress());
                        inboundChannel.close();
                    }
                }
            });
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        readFrame(ctx, (ByteBuf) msg);
    }

    private void readFrame(final ChannelHandlerContext ctx, ByteBuf buf) {
        if(first){
            try {
                readClientPrefaceString(buf);
            } catch (Http2Exception e){
                e.printStackTrace();
            }
            first = false;
        }

        ByteBuf copy = buf.copy();
        while (buf.readableBytes() > 0) {

            int payload = buf.readUnsignedMedium();
            int frameType = buf.readByte();
            Http2Flags flags = new Http2Flags(buf.readUnsignedByte());
            int streamId = readUnsignedInt(buf);
            buf.readBytes(payload);
            switch (frameType){
                case Http2FrameTypes.SETTINGS:
                    handleSettingFrame(ctx, flags);
                    break;
                case Http2FrameTypes.WINDOW_UPDATE:
                    handleWindowsUpdateFrame(ctx);
                    break;
                case Http2FrameTypes.HEADERS:
                    handleHeaderFrame(ctx, copy, streamId);
                    break;
                case Http2FrameTypes.DATA:
                    handleDataFrame(ctx, copy, streamId);
                    break;
                default:
                    break;

            }
        }
    }

    private boolean readClientPrefaceString(ByteBuf in) throws Http2Exception {
        ByteBuf clientPrefaceString = Http2CodecUtil.connectionPrefaceBuf();
        int prefaceRemaining = clientPrefaceString.readableBytes();
        int bytesRead = min(in.readableBytes(), prefaceRemaining);

        // If the input so far doesn't match the preface, break the connection.
        if (bytesRead == 0 || !ByteBufUtil.equals(in, in.readerIndex(),
                clientPrefaceString, clientPrefaceString.readerIndex(), bytesRead)) {
            String receivedBytes = hexDump(in, in.readerIndex(),
                    min(in.readableBytes(), clientPrefaceString.readableBytes()));
            throw connectionError(PROTOCOL_ERROR, "HTTP/2 client preface string missing or corrupt. " +
                    "Hex dump for received bytes: %s", receivedBytes);
        }
        in.skipBytes(bytesRead);
        clientPrefaceString.skipBytes(bytesRead);

        if (!clientPrefaceString.isReadable()) {
            // Entire preface has been read.
            clientPrefaceString.release();
            return true;
        }
        return false;
    }

    private void handleSettingFrame(final ChannelHandlerContext ctx, Http2Flags flags){
        ByteBufAllocator alloc = ctx.alloc();
        ByteBuf byteBuf = alloc.buffer();
        if(!flags.ack()){
            System.out.println("********************* setting received ...");

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
            System.out.println("********************* setting ack received ...");
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
                    System.out.println(" ...operationComplete isSuccess");
                    ctx.channel().read();
                } else {
                    System.out.println("...operationComplete failure");
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

    private void handleHeaderFrame(final ChannelHandlerContext ctx, final ByteBuf copy, final int streamId){
        System.out.print("******************************** headers received");
        forwardThisFrame(ctx, copy, streamId, 1);
    }


    private void handleDataFrame(final ChannelHandlerContext ctx, final ByteBuf copy, int streamId){
        System.out.print("******************************** data received");
        forwardThisFrame(ctx, copy, streamId, 2);
    }

    private void forwardThisFrame(final ChannelHandlerContext ctx, final ByteBuf copy, int streamId, final int type){
        //int select = (streamId - 2) % remoteHosts.length ;
        int select = 0;
        System.out.println("---------------------------------select:" + select + "," + ByteBufUtil.hexDump(copy));
        final Channel inboundChannel = ctx.channel();
        outboundChannels[select].writeAndFlush(copy).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    System.out.println("forward success ------------------------------------------type=" + type);
                    inboundChannel.read();
                } else {
                    System.out.println("forward failure------------------------------------------");
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannels != null) {

            for (int i = 0; i < outboundChannels.length; i++){
                if(outboundChannels[i].isActive()){
                    closeOnFlush(outboundChannels[i]);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            System.out.println("----------------------------------------------");
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
