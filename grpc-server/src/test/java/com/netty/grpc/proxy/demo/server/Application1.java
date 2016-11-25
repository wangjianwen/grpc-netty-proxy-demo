package com.netty.grpc.proxy.demo.server;


public class Application1 {
    private static int port = 10511;

    public static void main(String[] args) throws Exception {
        final HelloWorldServer server = new HelloWorldServer(port);
        server.start();
        server.blockUntilShutdown();

    }
}
