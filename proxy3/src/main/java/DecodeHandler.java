package io.netty.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * DecodeHandler Created by xieyz on 16-10-20.
 */
@ChannelHandler.Sharable
class DecodeHandler extends ChannelDuplexHandler {

    private static final Http2FrameLogger HTTP2_FRAME_LOGGER = new Http2FrameLogger(INFO, DecodeHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        int type = (int)byteBuf.getByte(3);
        if(type >=0 && type <= 9){

            ByteBuf copy = byteBuf.copy();
            Http2FrameReader reader = new Http2InboundFrameLogger(new DefaultHttp2FrameReader(),
                    HTTP2_FRAME_LOGGER);


            reader.readFrame(ctx, byteBuf, new Http2FrameAdapter() {
                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                        int streamDependency,
                        short weight, boolean exclusive, int padding,
                        boolean endStream) throws Http2Exception {
                    System.out.println("**************" + headers);
                }

                @Override
                public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                        boolean endOfStream) throws Http2Exception {
                    System.out.println("**************" + data);
                    int length =  super.onDataRead(ctx, streamId, data, padding, endOfStream);
                    return length;
                }

                @Override
                public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                        boolean exclusive) throws Http2Exception {
                    super.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
                }

                @Override
                public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
                    super.onRstStreamRead(ctx, streamId, errorCode);
                }

                @Override
                public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
                    super.onSettingsAckRead(ctx);
                }

                @Override
                public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
                    System.out.println("**************" + settings);
                    super.onSettingsRead(ctx, settings);
                }

                @Override
                public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                    super.onPingRead(ctx, data);
                }

                @Override
                public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                    super.onPingAckRead(ctx, data);
                }

                @Override
                public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                        Http2Headers headers,
                        int padding) throws Http2Exception {
                    super.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
                }

                @Override
                public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                        ByteBuf debugData) throws Http2Exception {
                    super.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
                }

                @Override
                public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId,
                        int windowSizeIncrement) throws Http2Exception {
                    super.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
                }

                @Override
                public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                        ByteBuf payload) {
                    super.onUnknownFrame(ctx, frameType, streamId, flags, payload);
                }
            });
            byteBuf.release();
            ctx.fireChannelRead(copy);
        } else{
            ByteBuf copy = byteBuf.copy();
            byteBuf.release();
            ctx.fireChannelRead(copy);
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

}