package com.netty.grpc.proxy.demo.client;



public class Application {
    private static int proxyPort = 8080;
    public static void main(String[] args) throws Exception {


//        String user = "world_" ;
//        client.greet(user);
        HelloWorldClient client = new HelloWorldClient("localhost", proxyPort);
       for (int i = 0; i < 10000; i++) {
            String user = "world_" + i;
            client.greet(user);


        }
        client.shutdown();

        //



    }
}
