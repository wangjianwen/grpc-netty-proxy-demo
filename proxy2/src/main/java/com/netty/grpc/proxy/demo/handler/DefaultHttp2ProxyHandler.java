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

package com.netty.grpc.proxy.demo.handler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;

/**
 * A simple handler that responds with the message "Hello World!".
 */
final class DefaultHttp2ProxyHandler extends Http2ConnectionHandler {
    public static final String UPGRADE_RESPONSE_HEADER = "http-to-http2-upgrade";

    private final String[] remoteHost;
    private final int[] remotePort;
    private Channel[] outboundChannel;
    private Http2HeadersDecoder headersDecoder = new DefaultHttp2HeadersDecoder();
    private final AtomicInteger counter;
    private ConcurrentMap<Integer, Integer> streamIdToChannelIndexMap = new ConcurrentHashMap<Integer, Integer>();



    DefaultHttp2ProxyHandler(Http2ConnectionDecoder decoder, DefaultHttp2ConnectionEncoder encoder,
                           Http2Settings initialSettings,String remoteHost[], int[] remotePort, AtomicInteger counter) {
        super(decoder, encoder, initialSettings);
        this.decoder().frameListener(new DefaultHttp2ProxyHandler.FrameListener());
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
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf msg1 = ((ByteBuf)msg).copy();
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println("***********************" + ByteBufUtil.hexDump(byteBuf));
        int streamId = this.streamId(ctx, byteBuf);

        Integer selector = streamIdToChannelIndexMap.get(streamId);
        if(selector == null){
            selector = counter.getAndIncrement() % remoteHost.length;
            streamIdToChannelIndexMap.putIfAbsent(streamId, selector);
        }


        ByteBuf byteBuf2 = (ByteBuf) msg1;
        System.out.println("***********************" + ByteBufUtil.hexDump(byteBuf2));

        if (outboundChannel[selector].isActive()) {
            outboundChannel[selector].writeAndFlush(msg1).addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }
    }

    private int streamId(final ChannelHandlerContext ctx, ByteBuf in) throws Http2Exception {
        // Read the header and prepare the unmarshaller to read the frame.
        int payloadLength = in.readUnsignedMedium();
        int frameType = in.readByte();
        Http2Flags flags = new Http2Flags(in.readUnsignedByte());
        return readUnsignedInt(in);
    }


    private Http2Headers processHeaders(final ChannelHandlerContext ctx, ByteBuf in) throws Http2Exception {
        if (in.readableBytes() < FRAME_HEADER_LENGTH) {
            // Wait until the entire frame header has been read.
            return null;
        }

        // Read the header and prepare the unmarshaller to read the frame.
        int payloadLength = in.readUnsignedMedium();
        int frameType = in.readByte();
        Http2Flags flags = new Http2Flags(in.readUnsignedByte());
        int streamId = readUnsignedInt(in);


        final int headersStreamId = streamId;
        final Http2Flags headersFlags = flags;
        int padding = in.readUnsignedByte();

        ByteBuf payload = in.readSlice(payloadLength);
        // The callback that is invoked is different depending on whether priority information
        // is present in the headers frame.
        if (flags.priorityPresent()) {
            long word1 = payload.readUnsignedInt();
            final boolean exclusive = (word1 & 0x80000000L) != 0;
            final int streamDependency = (int) (word1 & 0x7FFFFFFFL);
            if (streamDependency == streamId) {
                throw streamError(streamId, PROTOCOL_ERROR, "A stream cannot depend on itself.");
            }
            //final short weight = (short) (payload.readUnsignedByte() + 1);
            int lengthWithoutTrailingPadding = padding == 0 ? payload.readableBytes()
                    : payload.readableBytes() - (padding - 1);
            final ByteBuf fragment = payload.readSlice(lengthWithoutTrailingPadding);


            final DefaultHttp2ProxyHandler.HeadersBlockBuilder hdrBlockBuilder = new DefaultHttp2ProxyHandler.HeadersBlockBuilder();
            hdrBlockBuilder.addFragment(fragment, ctx.alloc(), flags.endOfHeaders());
            return hdrBlockBuilder.headers(streamId);

        } else {
            int lengthWithoutTrailingPadding = padding == 0 ? in.readableBytes()
                    : payload.readableBytes() - (padding - 1);

            // Process the initial fragment, invoking the listener's callback if end of headers.
            final ByteBuf fragment = in.readSlice(lengthWithoutTrailingPadding);
            final DefaultHttp2ProxyHandler.HeadersBlockBuilder hdrBlockBuilder = new DefaultHttp2ProxyHandler.HeadersBlockBuilder();
            hdrBlockBuilder.addFragment(fragment, ctx.alloc(), flags.endOfHeaders());
            return hdrBlockBuilder.headers(streamId);
        }


    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(ctx.channel());
        streamIdToChannelIndexMap.clear();

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }



    private class FrameListener extends Http2FrameAdapter {
        private FrameListener() {
        }

        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            //DefaultHttp2ProxyHandler.this.onDataRead(ctx, streamId, data,padding, endOfStream);
            return padding;
        }

        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
           //
        }

        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            //NettyServerHandler.this.onRstStreamRead(streamId);
        }


    }


    /**
     * Base class for processing of HEADERS and PUSH_PROMISE header blocks that potentially span
     * multiple frames. The implementation of this interface will perform the final callback to the
     * {@link Http2FrameListener} once the end of headers is reached.
     */
    private abstract class HeadersContinuation {
        private final DefaultHttp2ProxyHandler.HeadersBlockBuilder builder = new DefaultHttp2ProxyHandler.HeadersBlockBuilder();

        /**
         * Returns the stream for which headers are currently being processed.
         */
        abstract int getStreamId();

        /**
         * Processes the next fragment for the current header block.
         *
         * @param endOfHeaders whether the fragment is the last in the header block.
         * @param fragment the fragment of the header block to be added.
         * @param listener the listener to be notified if the header block is completed.
         */
        abstract void processFragment(boolean endOfHeaders, ByteBuf fragment,
                Http2FrameListener listener) throws Http2Exception;

        final DefaultHttp2ProxyHandler.HeadersBlockBuilder headersBlockBuilder() {
            return builder;
        }

        /**
         * Free any allocated resources.
         */
        final void close() {
            builder.close();
        }
    }

    /**
     * Utility class to help with construction of the headers block that may potentially span
     * multiple frames.
     */
    protected class HeadersBlockBuilder {
        private ByteBuf headerBlock;

        /**
         * The local header size maximum has been exceeded while accumulating bytes.
         * @throws Http2Exception A connection error indicating too much data has been received.
         */


        /**
         * Adds a fragment to the block.
         *
         * @param fragment the fragment of the headers block to be added.
         * @param alloc allocator for new blocks if needed.
         * @param endOfHeaders flag indicating whether the current frame is the end of the headers.
         *            This is used for an optimization for when the first fragment is the full
         *            block. In that case, the buffer is used directly without copying.
         */
        final void addFragment(ByteBuf fragment, ByteBufAllocator alloc, boolean endOfHeaders) throws Http2Exception {
            if (headerBlock == null) {

                if (endOfHeaders) {
                    // Optimization - don't bother copying, just use the buffer as-is. Need
                    // to retain since we release when the header block is built.
                    headerBlock = fragment.retain();
                } else {
                    headerBlock = alloc.buffer(fragment.readableBytes());
                    headerBlock.writeBytes(fragment);
                }
                return;
            }

            if (headerBlock.isWritable(fragment.readableBytes())) {
                // The buffer can hold the requested bytes, just write it directly.
                headerBlock.writeBytes(fragment);
            } else {
                // Allocate a new buffer that is big enough to hold the entire header block so far.
                ByteBuf buf = alloc.buffer(headerBlock.readableBytes() + fragment.readableBytes());
                buf.writeBytes(headerBlock);
                buf.writeBytes(fragment);
                headerBlock.release();
                headerBlock = buf;
            }
        }

        /**
         * Builds the headers from the completed headers block. After this is called, this builder
         * should not be called again.
         */
        Http2Headers headers(int streamId) throws Http2Exception {
            try {
                return headersDecoder.decodeHeaders(headerBlock);
            } finally {
                close();
            }
        }

        /**
         * Closes this builder and frees any resources.
         */
        void close() {
            if (headerBlock != null) {
                headerBlock.release();
                headerBlock = null;
            }

        }
    }
}
