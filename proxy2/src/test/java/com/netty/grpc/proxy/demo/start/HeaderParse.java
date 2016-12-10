package com.netty.grpc.proxy.demo.start;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;

/**
 * Created by Administrator on 2016/12/3.
 */
public class HeaderParse {

    public static void main(String[] args) {
//       for(int i = 64; i < 128; i++){
//           //if((i & 0x3F) == 0x3f){
//               System.out.println(i & 0x3f);
//           //}
//       }

        encodeInteger(127, 6);
    }
    public static void encodeInteger(int value, int prefix) {
        if (value >> prefix <= 0) {
            printBinary(value);
        } else {
            int number = (1 << prefix) - 1;
            printBinary(number);
            for (value -= number; value >= 128; value /= 128) {
                printBinary(value % 128 + 128);
            }
            printBinary(value);
        }
    }

    private static void printBinary(int value) {
        System.out.println(String.format("%8s", Integer.toBinaryString(value)).replace(" ", "0"));
    }
}
