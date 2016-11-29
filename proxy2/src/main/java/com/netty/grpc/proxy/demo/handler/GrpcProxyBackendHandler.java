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
import io.netty.channel.*;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameTypes;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Settings;

import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;

@ChannelHandler.Sharable
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
        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        if (first) {
            System.out.println("===============================================1");
            first = false;
            writer.writeSettingsAck(ctx, ctx.newPromise());
            ctx.flush();
        } else {
            System.out.println("===============================================2");
            inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
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


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        GrpcProxyFrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        GrpcProxyFrontendHandler.closeOnFlush(ctx.channel());
    }
}
