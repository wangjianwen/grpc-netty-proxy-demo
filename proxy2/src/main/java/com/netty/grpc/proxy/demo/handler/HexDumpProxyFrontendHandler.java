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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http2.*;

import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;

public class HexDumpProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private final String[] remoteHost;
    private final int[] remotePort;
    private Channel[] outboundChannel;
    private ConcurrentMap<Integer, Integer> streamIdToChannelIndexMap = new ConcurrentHashMap<Integer, Integer>();
    private final AtomicInteger counter;
    private final Http2ConnectionEncoder encoder;
    private final Http2ConnectionDecoder decoder;

    // As we use inboundChannel.eventLoop() when buildling the Bootstrap this does not need to be volatile as
    // the outboundChannel will use the same EventLoop (and therefore Thread) as the inboundChannel.

    public HexDumpProxyFrontendHandler(String[] remoteHost, int[] remotePort, AtomicInteger counter) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.counter = counter;
        outboundChannel = new Channel[remoteHost.length];

        DefaultHttp2HeadersDecoder headersDecoder = new DefaultHttp2HeadersDecoder();
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
        connection.local().flowController(new DefaultHttp2LocalFlowController(connection, 0.5F, true));
        encoder = new DefaultHttp2ConnectionEncoder(connection, new DefaultHttp2FrameWriter());
        decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, new DefaultHttp2FrameReader(headersDecoder));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new HexDumpProxyBackendHandler(inboundChannel))
                .option(ChannelOption.AUTO_READ, false);

        for(int i = 0; i < remoteHost.length; i++){
            ChannelFuture f = b.connect(remoteHost[i], remotePort[i]);
            outboundChannel[i] = f.channel();
            f.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        // connection complete start to read first data
                        inboundChannel.read();
                    } else {
                        // Close the connection if the connection attempt has failed.
                        inboundChannel.close();
                    }
                }
            });
        }
    }

//    @Override
//    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
//        List<Object> objects = new ArrayList<Object>();
//        try{
//            decoder.decodeFrame(ctx, (ByteBuf) msg, objects);
//        } catch (Http2Exception e){
//
//        }
//
//
//    }

    private int streamId(final ChannelHandlerContext ctx, ByteBuf in)  {
        // Read the header and prepare the unmarshaller to read the frame.
        int payloadLength = in.readUnsignedMedium();
        int frameType = in.readByte();
        Http2Flags flags = new Http2Flags(in.readUnsignedByte());
        return readUnsignedInt(in);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            for (int i = 0; i < outboundChannel.length; i++){
                closeOnFlush(outboundChannel[i]);
            }
        }
        streamIdToChannelIndexMap.clear();
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
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
