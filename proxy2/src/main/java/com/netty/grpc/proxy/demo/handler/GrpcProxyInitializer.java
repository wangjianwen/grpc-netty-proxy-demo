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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.util.concurrent.atomic.AtomicInteger;

public class GrpcProxyInitializer extends ChannelInitializer<SocketChannel> {

    private final String[] remoteHosts;
    private final int[] remotePorts;
    private final AtomicInteger counter;

    public GrpcProxyInitializer(String[] remoteHosts, int[] remotePorts, AtomicInteger counter) {
        this.remoteHosts = remoteHosts;
        this.remotePorts = remotePorts;
        this.counter = counter;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        System.out.println("---------------------------------MockGrpcServerInitializer-----------------");
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
        pipeline.addLast(new GrpcProxyFrontendHandler(remoteHosts, remotePorts, counter));
    }
}
