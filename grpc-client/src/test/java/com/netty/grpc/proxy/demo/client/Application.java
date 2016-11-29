package com.netty.grpc.proxy.demo.client;



public class Application {
    private static int proxyPort = 8443;
    public static void main(String[] args) throws Exception {
        HelloWorldClient client = new HelloWorldClient("localhost", proxyPort);
        for (int i = 0; i < 2; i++) {

            String user = "world_" + i;
            client.greet(user);


        }
        //
        client.shutdown();


    }
}
