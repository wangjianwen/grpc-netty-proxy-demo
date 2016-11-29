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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameTypes;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Settings;

import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;

class GrpcProxyBackendHandler extends ChannelInboundHandlerAdapter {
    public static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private final Channel inboundChannel;
    private final Http2FrameWriter writer = new DefaultHttp2FrameWriter();
    private boolean first = true;

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
        //ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        if (first) {
            writer.writeSettingsAck(ctx, ctx.newPromise());
            first = false;
            ctx.flush();
        } else {
            ByteBuf copy = ((ByteBuf)msg).copy();
            readFrame(ctx, (ByteBuf) msg, copy);

        }
    }

    private void readFrame(final ChannelHandlerContext ctx, ByteBuf buf, ByteBuf copy) {
        inboundChannel.writeAndFlush(copy).addListener(new ChannelFutureListener() {
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
        DefaultHttp2ProxyHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        DefaultHttp2ProxyHandler.closeOnFlush(ctx.channel());
    }
}
