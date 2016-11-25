package com.netty.grpc.proxy.demo.handler;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Initializer extends ChannelInitializer<SocketChannel> {

    private final String[] remoteHost;
    private final int[] remotePort;
    private final AtomicInteger counter;

    public Initializer(String[] remoteHost, int[] remotePort, AtomicInteger counter) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.counter = counter;
    }

    protected void initChannel(SocketChannel ch) throws Exception {
        int index = (counter.getAndIncrement()) % remotePort.length;
        ch.pipeline().addLast(
                new LoggingHandler(LogLevel.INFO),
                new FrontendHandler(remoteHost[index], remotePort[index]));
    }
}
