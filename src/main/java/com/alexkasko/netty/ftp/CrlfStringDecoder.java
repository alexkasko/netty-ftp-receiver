package com.alexkasko.netty.ftp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.nio.charset.Charset;

/**
 * User: alexkasko
 * Date: 12/28/12
 */
public class CrlfStringDecoder extends FrameDecoder {
    private static final byte CR = 13;
    private static final byte LF = 10;

    private final int maxRequestLengthBytes;
    private final Charset encoding;

    public CrlfStringDecoder() {
        this(1<< 8, "UTF-8");
    }

    public CrlfStringDecoder(int maxRequestLengthBytes, String encoding) {
        if(maxRequestLengthBytes <= 0) throw new IllegalArgumentException(
                "Provided maxRequestLengthBytes: [" + maxRequestLengthBytes +"] must be positive");
        this.maxRequestLengthBytes = maxRequestLengthBytes;
        this.encoding = Charset.forName(encoding);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer cb) throws Exception {
        byte[] data = new byte[maxRequestLengthBytes];
        int lineLength = 0;
        while (true) {
            if (!cb.readable()) {
                cb.resetReaderIndex();
                return null;
            }
            byte nextByte = cb.readByte();
            if (nextByte == CR) {
                nextByte = cb.readByte();
                if (nextByte == LF) {
                    return new String(data, encoding);
                }
            } else if (nextByte == LF) {
                return new String(data, encoding);
            } else {
                if (lineLength >= maxRequestLengthBytes) throw new IllegalArgumentException(
                        "Request size threshold exceeded: [" + maxRequestLengthBytes + "]");
                data[lineLength] = nextByte;
                lineLength += 1;
            }
        }
    }
}
