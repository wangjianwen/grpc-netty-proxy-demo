package com.netty.grpc.proxy.demo.client;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class MockGrpcClient {
    private final static String HOST = "localhost";
    private final static int PORT = 10511;

    public static void main(String[] args) {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        // Configure the client.
        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.remoteAddress(HOST, PORT);
            b.handler(new MockGrpcInitializer());

            b.connect().syncUninterruptibly().channel();
        } catch (Exception e){
            e.printStackTrace();
        }

    }
}
