/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.proxy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.example.http2.Http2ExampleUtil.UPGRADE_RESPONSE_HEADER;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2FrameTypes.SETTINGS;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public final class DefaultHttp2Handler extends Http2ConnectionHandler implements Http2FrameListener {

    static final ByteBuf RESPONSE_BYTES = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));

    private final String[] remoteHost;
    private final int[] remotePort;
    private Channel[] outboundChannel;
    private final AtomicInteger counter;
    private ConcurrentMap<Integer, Integer> streamIdToChannelIndexMap = new ConcurrentHashMap<Integer, Integer>();



    DefaultHttp2Handler(Http2ConnectionDecoder decoder, DefaultHttp2ConnectionEncoder encoder,
            Http2Settings initialSettings,String remoteHost[], int[] remotePort, AtomicInteger counter) {
        super(decoder, encoder, initialSettings);
        this.decoder().frameListener(new DefaultHttp2Handler.FrameListener());
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.counter = counter;
        outboundChannel = new Channel[remoteHost.length];
    }


    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            // Write an HTTP/2 response to the upgrade request
            Http2Headers headers =
                    new DefaultHttp2Headers().status(OK.codeAsText())
                    .set(new AsciiString(UPGRADE_RESPONSE_HEADER), new AsciiString("true"));
            encoder().writeHeaders(ctx, 1, headers, 0, true, ctx.newPromise());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new HexDumpProxyBackendHandler(inboundChannel))
                .option(ChannelOption.AUTO_READ, false);

        for(int i = 0; i < remoteHost.length; i++){
            ChannelFuture f = b.connect(remoteHost[i], remotePort[i]);
            outboundChannel[i] = f.channel();
            f.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        // connection complete start to read first data
                        inboundChannel.read();
                    } else {
                        // Close the connection if the connection attempt has failed.
                        inboundChannel.close();
                    }
                }
            });
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        /*
        Http2ConnectionEncoder encoder = this.encoder();
        ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
        writeFrameHeaderInternal(buf, 0, SETTINGS, new Http2Flags().ack(true), 0);
        // Acknowledge receipt of the settings.
        encoder.writeSettingsAck(ctx, ctx.newPromise());
        outboundChannel[0].writeAndFlush(buf).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
        */

    }

    private void writeFrameHeaderInternal(ByteBuf out, int payloadLength, byte type,
            Http2Flags flags, int streamId) {
        out.writeMedium(payloadLength);
        out.writeByte(type);
        out.writeByte(flags.value());
        out.writeInt(streamId);
    }


    /**
     * Sends a "Hello World" DATA frame to the client.
     */
    private void sendResponse(ChannelHandlerContext ctx, int streamId, ByteBuf payload) {
        // Send a frame for the response status
        Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
        encoder().writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
        encoder().writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
        try {
            flush(ctx);
        } catch (Throwable cause) {
            onError(ctx, cause);
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
        int processed = data.readableBytes() + padding;
        if (endOfStream) {
            sendResponse(ctx, streamId, data.retain());

        }
        return processed;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                              Http2Headers headers, int padding, boolean endOfStream) {
        if (endOfStream) {
            ByteBuf content = ctx.alloc().buffer();
            content.writeBytes(RESPONSE_BYTES.duplicate());
            ByteBufUtil.writeAscii(content, " - via HTTP/2");
            sendResponse(ctx, streamId, content);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream) {
        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                               short weight, boolean exclusive) {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                               Http2Flags flags, ByteBuf payload) {
    }

    private class FrameListener extends Http2FrameAdapter {
        private FrameListener() {
        }

        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            DefaultHttp2Handler.this.onDataRead(ctx, streamId, data,padding, endOfStream);
            return padding;
        }

        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            DefaultHttp2Handler.this.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive,
                    padding, endStream);
        }

        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            //NettyServerHandler.this.onRstStreamRead(streamId);
        }

        
    }
}
