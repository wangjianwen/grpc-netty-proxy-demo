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

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.DefaultHttp2LocalFlowController;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class HexDumpProxyInitializer extends ChannelInitializer<SocketChannel> {

    private final String[] remoteHost;
    private final int[] remotePort;
    private final AtomicInteger robinSelector;

    public HexDumpProxyInitializer(String[] remoteHost, int[] remotePort, AtomicInteger robinSelector) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.robinSelector = robinSelector;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, Http2FrameLogger.class);
        DefaultHttp2HeadersDecoder headersDecoder = new DefaultHttp2HeadersDecoder();

        Http2InboundFrameLogger frameReader = new Http2InboundFrameLogger(new DefaultHttp2FrameReader(headersDecoder), frameLogger);
        Http2OutboundFrameLogger frameWriter = new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
        connection.local().flowController(new DefaultHttp2LocalFlowController(connection, 0.5F, true));
        DefaultHttp2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        DefaultHttp2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader);
        Http2Settings settings = new Http2Settings();

        DefaultHttp2ProxyHandler http2ConnectionHandler = new DefaultHttp2ProxyHandler(decoder, encoder,settings,
                remoteHost, remotePort, robinSelector);

        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
        pipeline.addLast(http2ConnectionHandler);

    }
}
