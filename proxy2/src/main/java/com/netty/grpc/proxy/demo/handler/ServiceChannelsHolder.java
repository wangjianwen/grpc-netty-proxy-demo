package com.netty.grpc.proxy.demo.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Created by wangjw on 2016/12/1.
 */
public class ServiceChannelsHolder {

    private static ServiceChannelsHolder instance;
    private final List<ChannelFuture> serviceChannels;
    private final String[] remoteHosts = new String[]{"localhost", "localhost", "localhost"};
    private final int[] remotePorts = new int[]{10511, 10512, 10513};


    public synchronized static ServiceChannelsHolder getInstance() {
        if(instance == null){
            return new ServiceChannelsHolder();
        }
        return instance;
    }

    private ServiceChannelsHolder(){
        serviceChannels = new ArrayList<ChannelFuture>();
        init();
    }

    private void init(){

        for (int i = 0; i < remoteHosts.length; i++){
            // 建立与远程服务器的联系
            Bootstrap b = new Bootstrap();
            EventLoopGroup eventGroup = new NioEventLoopGroup();
            b.group(eventGroup).channel(NioSocketChannel.class);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new MockClientHandler());
                }
            }).option(ChannelOption.AUTO_READ, false);
            ChannelFuture channelFuture = b.connect(remoteHosts[i], remotePorts[i]);
            serviceChannels.add(channelFuture);
        }

    }

    public List<ChannelFuture> getServiceChannels() {
        return serviceChannels;
    }

}
