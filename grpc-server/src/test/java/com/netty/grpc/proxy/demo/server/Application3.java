package com.netty.grpc.proxy.demo.handler.server;


public class Application3 {
    private static int port = 10513;

    public static void main(String[] args) throws Exception {
        final HelloWorldServer server = new HelloWorldServer(port);
        server.start();
        server.blockUntilShutdown();

    }
}
