package com.netty.grpc.proxy.demo.handler;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http2.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.ByteBufUtil.hexDump;
import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static io.netty.handler.codec.http2.Http2FrameTypes.WINDOW_UPDATE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.min;

public final class GrpcPrxoyFrontendHandler extends ByteToMessageDecoder {
    public static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private final String[] remoteHost;
    private final int[] remotePort;
    private final Channel[] outboundChannel;
    private final AtomicInteger counter;
    private final Http2FrameReader reader;
    private final Http2FrameWriter writer = new DefaultHttp2FrameWriter();
    private static ByteBuf clientPrefaceString;
    private final Http2FrameListener listener = new Http2FrameListener() {
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            System.out.print("************************,Data frame received.............");
            return 0;
        }

        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {

        }

        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
            System.out.print("************************,Header frame received.............");
            Http2Settings settings = new Http2Settings();
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + settings.size() * SETTING_ENTRY_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, SETTINGS, new Http2Flags(), 0);
            for (Http2Settings.PrimitiveEntry<Long> entry : settings.entries()) {
                writeUnsignedShort(entry.key(), buf);
                writeUnsignedInt(entry.value(), buf);
            }


            ByteBuf buf2 = ctx.alloc().buffer(WINDOW_UPDATE_FRAME_LENGTH);
            writeFrameHeaderInternal(buf2, INT_FIELD_LENGTH, WINDOW_UPDATE, new Http2Flags(), streamId);
            buf2.writeInt(983041);

            //
            outboundChannel[0].write(buf);
            outboundChannel[0].write(buf2);
            outboundChannel[0].flush();


            //GrpcPrxoyFrontendHandler.this.writer.writeHeaders

        }

        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {

        }

        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {

        }

        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {

        }

        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            System.out.print("************************,Setting frame received.............");
            writeSettingsAck(ctx);
        }

        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {

        }

        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {

        }

        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {

        }

        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {

        }

        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {

        }

        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) throws Http2Exception {

        }
    };

    private void writeSettingsAck(ChannelHandlerContext ctx) {
        writer.writeSettingsAck(ctx, ctx.newPromise());
    }

    public GrpcPrxoyFrontendHandler(String[] remoteHost, int[] remotePort, AtomicInteger counter){
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.counter = counter;
        outboundChannel = new Channel[remoteHost.length];
        Http2HeadersDecoder headersDecoder = new DefaultHttp2HeadersDecoder();
        reader = new DefaultHttp2FrameReader(headersDecoder);
    }




    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        settings.maxConcurrentStreams(Integer.MAX_VALUE);
        // write setting
        writeSettings(ctx, settings, ctx.newPromise());

        // write windowUpdate
        writeWindowUpdate(ctx,0,983041, ctx.newPromise());
        ctx.flush();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
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
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if(clientPrefaceString == null){
            readClientPrefaceString(in);
        }
        reader.readFrame(ctx, in, listener);
    }


    public ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings,
                                       ChannelPromise promise) {
        try {
            checkNotNull(settings, "settings");
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH + settings.size() * SETTING_ENTRY_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, SETTINGS, new Http2Flags(), 0);
            for (Http2Settings.PrimitiveEntry<Long> entry : settings.entries()) {
                writeUnsignedShort(entry.key(), buf);
                writeUnsignedInt(entry.value(), buf);
            }
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    private static void writeFrameHeaderInternal(ByteBuf out, int payloadLength, byte type,
                                                 Http2Flags flags, int streamId) {
        out.writeMedium(payloadLength);
        out.writeByte(type);
        out.writeByte(flags.value());
        out.writeInt(streamId);
    }

    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId,
                                           int windowSizeIncrement, ChannelPromise promise) {
        try {

            ByteBuf buf = ctx.alloc().buffer(WINDOW_UPDATE_FRAME_LENGTH);
            writeFrameHeaderInternal(buf, INT_FIELD_LENGTH, WINDOW_UPDATE, new Http2Flags(), streamId);
            buf.writeInt(windowSizeIncrement);
            return ctx.write(buf, promise);
        } catch (Throwable t) {
            return promise.setFailure(t);
        }
    }

    private boolean readClientPrefaceString(ByteBuf in) throws Http2Exception {
        clientPrefaceString = Http2CodecUtil.connectionPrefaceBuf();
        int prefaceRemaining = clientPrefaceString.readableBytes();
        int bytesRead = min(in.readableBytes(), prefaceRemaining);

        // If the input so far doesn't match the preface, break the connection.
        if (bytesRead == 0 || !ByteBufUtil.equals(in, in.readerIndex(),
                clientPrefaceString, clientPrefaceString.readerIndex(), bytesRead)) {
            String receivedBytes = hexDump(in, in.readerIndex(),
                    min(in.readableBytes(), clientPrefaceString.readableBytes()));
            throw connectionError(PROTOCOL_ERROR, "HTTP/2 client preface string missing or corrupt. " +
                    "Hex dump for received bytes: %s", receivedBytes);
        }
        in.skipBytes(bytesRead);
        clientPrefaceString.skipBytes(bytesRead);

        if (!clientPrefaceString.isReadable()) {
            // Entire preface has been read.
            clientPrefaceString.release();
            clientPrefaceString = null;
            return true;
        }
        return false;
    }

}
