package com.netty.grpc.proxy.demo.handler.parser;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersDecoder;

import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;


public class Http2HeaderParser {

    private static Http2HeadersDecoder headersDecoder = new DefaultHttp2HeadersDecoder();

    public Http2Headers processHeaders(final ChannelHandlerContext ctx, ByteBuf in) throws Http2Exception {
        if (in.readableBytes() < FRAME_HEADER_LENGTH) {
            // Wait until the entire frame header has been read.
            return null;
        }

        // Read the header and prepare the unmarshaller to read the frame.
        int payloadLength = in.readUnsignedMedium();
        in.readByte();
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
            final int streamDependency = (int) (word1 & 0x7FFFFFFFL);
            if (streamDependency == streamId) {
                throw streamError(streamId, PROTOCOL_ERROR, "A stream cannot depend on itself.");
            }
            //final short weight = (short) (payload.readUnsignedByte() + 1);
            int lengthWithoutTrailingPadding = padding == 0 ? payload.readableBytes()
                    : payload.readableBytes() - (padding - 1);
            final ByteBuf fragment = payload.readSlice(lengthWithoutTrailingPadding);


            final Http2HeaderParser.HeadersBlockBuilder hdrBlockBuilder = new Http2HeaderParser.HeadersBlockBuilder();
            hdrBlockBuilder.addFragment(fragment, ctx.alloc(), flags.endOfHeaders());
            return hdrBlockBuilder.headers();

        } else {
            int lengthWithoutTrailingPadding = padding == 0 ? in.readableBytes()
                    : payload.readableBytes() - (padding - 1);

            // Process the initial fragment, invoking the listener's callback if end of headers.
            final ByteBuf fragment = in.readSlice(lengthWithoutTrailingPadding);
            final Http2HeaderParser.HeadersBlockBuilder hdrBlockBuilder = new Http2HeaderParser.HeadersBlockBuilder();
            hdrBlockBuilder.addFragment(fragment, ctx.alloc(), flags.endOfHeaders());
            return hdrBlockBuilder.headers();
        }

    }


    private abstract class HeadersContinuation {
        private final Http2HeaderParser.HeadersBlockBuilder builder = new Http2HeaderParser.HeadersBlockBuilder();

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

        final Http2HeaderParser.HeadersBlockBuilder headersBlockBuilder() {
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
        Http2Headers headers() throws Http2Exception {
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
