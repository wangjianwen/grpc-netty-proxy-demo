package com.netty.grpc.proxy.demo.start;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.internal.hpack.Decoder;

import java.io.IOException;

/**
 * Created by Administrator on 2016/12/2.
 */
public class DecoderTest {

    public static void main(String[] args) throws IOException{
        Decoder decoder = new Decoder(8192, 4096, 32);
        ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;
        ByteBuf buffer = ALLOC.buffer(103);
        // 00 00 00 0f 41 8a
        buffer.writeByte(0x00);
        buffer.writeByte(0x00);
        buffer.writeByte(0x00);
        buffer.writeByte(0x00);
        buffer.writeByte(0x41);
        buffer.writeByte(0x8a);
        // a0 e4 1d 13 9d 09 b8 f3 4d 33 44 95 62 72 d1 41
        buffer.writeByte(0xa0);
        buffer.writeByte(0xe4);
        buffer.writeByte(0x1d);
        buffer.writeByte(0x13);
        buffer.writeByte(0x9d);
        buffer.writeByte(0x09);
        buffer.writeByte(0xb8);
        buffer.writeByte(0xf3);
        buffer.writeByte(0x4d);
        buffer.writeByte(0x33);
        buffer.writeByte(0x44);
        buffer.writeByte(0x95);
        buffer.writeByte(0x62);
        buffer.writeByte(0x72);
        buffer.writeByte(0xd1);
        buffer.writeByte(0x41);
        // fc 1e ca 24 5f 15 85 2a 4b 63 1b 87 eb 19 68 a0
        buffer.writeByte(0xfc);
        buffer.writeByte(0x1e);
        buffer.writeByte(0xca);
        buffer.writeByte(0x24);
        buffer.writeByte(0x5f);
        buffer.writeByte(0x15);
        buffer.writeByte(0x85);
        buffer.writeByte(0x2a);
        buffer.writeByte(0x4b);
        buffer.writeByte(0x63);
        buffer.writeByte(0x1b);
        buffer.writeByte(0x87);
        buffer.writeByte(0xeb);
        buffer.writeByte(0x19);
        buffer.writeByte(0x68);
        buffer.writeByte(0xa0);

        // ff 83 86 5f 8b 1d 75 d0 62 0d 26 3d 4c 4d 65 64
        buffer.writeByte(0xff);
        buffer.writeByte(0x83);
        buffer.writeByte(0x86);
        buffer.writeByte(0x5f);
        buffer.writeByte(0x8b);
        buffer.writeByte(0x1d);
        buffer.writeByte(0x75);
        buffer.writeByte(0xd0);
        buffer.writeByte(0x62);
        buffer.writeByte(0x0d);
        buffer.writeByte(0x26);
        buffer.writeByte(0x3d);
        buffer.writeByte(0x4c);
        buffer.writeByte(0x4d);
        buffer.writeByte(0x65);
        buffer.writeByte(0x64);

        // 40 02 74 65 86 4d 83 35 05 b1 1f 7a 8f 9a ca c8
        buffer.writeByte(0x40);
        buffer.writeByte(0x02);
        buffer.writeByte(0x74);
        buffer.writeByte(0x65);
        buffer.writeByte(0x86);
        buffer.writeByte(0x4d);
        buffer.writeByte(0x83);
        buffer.writeByte(0x35);
        buffer.writeByte(0x05);
        buffer.writeByte(0xb1);
        buffer.writeByte(0x1f);
        buffer.writeByte(0x7a);
        buffer.writeByte(0x8f);
        buffer.writeByte(0x9a);
        buffer.writeByte(0xca);
        buffer.writeByte(0xc8);

        // b7 41 f7 1a d5 15 29 f4 c0 57 02 e0 40 8e 9a ca
        buffer.writeByte(0xb7);
        buffer.writeByte(0x41);
        buffer.writeByte(0xf7);
        buffer.writeByte(0x1a);
        buffer.writeByte(0xd5);
        buffer.writeByte(0x15);
        buffer.writeByte(0x29);
        buffer.writeByte(0xf4);
        buffer.writeByte(0xc0);
        buffer.writeByte(0x57);
        buffer.writeByte(0x02);
        buffer.writeByte(0xe0);
        buffer.writeByte(0x40);
        buffer.writeByte(0x8e);
        buffer.writeByte(0x9a);
        buffer.writeByte(0xca);

        // c8 b0 c8 42 d6 95 8b 51 0f 21 aa 9b 83 9b d9 ab
        buffer.writeByte(0xca);
        buffer.writeByte(0xb0);
        buffer.writeByte(0xc8);
        buffer.writeByte(0x42);
        buffer.writeByte(0xd6);
        buffer.writeByte(0x95);
        buffer.writeByte(0x8b);
        buffer.writeByte(0x51);
        buffer.writeByte(0x0f);
        buffer.writeByte(0x21);
        buffer.writeByte(0xaa);
        buffer.writeByte(0x9b);
        buffer.writeByte(0x83);
        buffer.writeByte(0x9b);
        buffer.writeByte(0xd9);
        buffer.writeByte(0xab);

        Http2Headers headers = new DefaultHttp2Headers();
        decoder.decode(buffer, headers);
        System.out.println(headers);
    }
}
