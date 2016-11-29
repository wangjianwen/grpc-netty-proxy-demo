/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.netty.grpc.proxy.demo.handler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty.handler.codec.http2.Http2Exception.streamError;

/**
 * A simple handler that responds with the message "Hello World!".
 */
final class DefaultHttp2ProxyHandler extends Http2ConnectionHandler {
    public static final String UPGRADE_RESPONSE_HEADER = "http-to-http2-upgrade";

    private final String[] remoteHost;
    private final int[] remotePort;
    private Channel[] outboundChannel;
    private final AtomicInteger counter;
    private ConcurrentMap<Integer, Integer> streamIdToChannelIndexMap = new ConcurrentHashMap<Integer, Integer>();



    DefaultHttp2ProxyHandler(Http2ConnectionDecoder decoder, DefaultHttp2ConnectionEncoder encoder,
                           Http2Settings initialSettings,String remoteHost[], int[] remotePort, AtomicInteger counter) {
        super(decoder, encoder, initialSettings);
        this.decoder().frameListener(new DefaultHttp2ProxyHandler.FrameListener());
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.counter = counter;
        outboundChannel = new Channel[remoteHost.length];
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            // Write an HTTP/2 response to the upgrade request
            Http2Headers headers =
                    new DefaultHttp2Headers().status(OK.codeAsText())
                    .set(new AsciiString(UPGRADE_RESPONSE_HEADER), new AsciiString("true"));
            encoder().writeHeaders(ctx, 1, headers, 0, true, ctx.newPromise());
        }
        super.userEventTriggered(ctx, evt);
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new GrpcProxyBackendHandler(inboundChannel))
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

    /*

    @Override
    @SuppressWarnings("unchecked")
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf msg1 = ((ByteBuf)msg).copy();
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println("***********************" + ByteBufUtil.hexDump(byteBuf));
        int streamId = this.streamId(byteBuf);

        Integer selector = streamIdToChannelIndexMap.get(streamId);
        if(selector == null){
            selector = counter.getAndIncrement() % remoteHost.length;
            streamIdToChannelIndexMap.putIfAbsent(streamId, selector);
        }


        ByteBuf byteBuf2 = (ByteBuf) msg1;
        System.out.println("***********************" + ByteBufUtil.hexDump(byteBuf2));

        if (outboundChannel[selector].isActive()) {
            outboundChannel[selector].writeAndFlush(msg1).addListener(new ChannelFutureListener() {
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
    */

    private int streamId(ByteBuf in) throws Http2Exception {
        // Read the header and prepare the unmarshaller to read the frame.
        int payloadLength = in.readUnsignedMedium();
        int frameType = in.readByte();
        Http2Flags flags = new Http2Flags(in.readUnsignedByte());
        return readUnsignedInt(in);
    }




    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(ctx.channel());
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



    private class FrameListener extends Http2FrameAdapter {
        private FrameListener() {
        }

        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            //DefaultHttp2ProxyHandler.this.onDataRead(ctx, streamId, data,padding, endOfStream);
            return padding;
        }

        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
           //
        }

        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            //NettyServerHandler.this.onRstStreamRead(streamId);
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            Http2ConnectionEncoder encoder = DefaultHttp2ProxyHandler.this.encoder();
            encoder.remoteSettings(settings);

            // Acknowledge receipt of the settings.
            encoder.writeSettingsAck(ctx, ctx.newPromise());


            super.onSettingsRead(ctx, settings);
        }
    }



}
