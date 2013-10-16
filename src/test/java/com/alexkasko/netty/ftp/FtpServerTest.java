package com.alexkasko.netty.ftp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.Test;

/**
 * User: alexkasko
 * Date: 12/28/12
 */
public class FtpServerTest {

    @Test
    public void test() throws IOException, InterruptedException {
    	EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
    	ServerBootstrap b = new ServerBootstrap();
    	b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {

					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ChannelPipeline pipe = ch.pipeline();
			            pipe.addLast("decoder", new CrlfStringDecoder());
			            pipe.addLast("handler", new FtpServerHandler(new ConsoleReceiver()));
					}
				
				});
    	b.localAddress(2121).bind();
        FTPClient client = new FTPClient();
//        https://issues.apache.org/jira/browse/NET-493
        client.setBufferSize(0);
        client.connect("127.0.0.1", 2121);
        // active
        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.printWorkingDirectory();
        client.changeWorkingDirectory("/foo");
        client.printWorkingDirectory();
        client.listFiles("/foo");
        client.storeFile("bar", new ByteArrayInputStream("content".getBytes()));
        client.rename("bar", "baz");
        client.deleteFile("baz");
        // passive
        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.enterLocalPassiveMode();
        client.printWorkingDirectory();
        client.changeWorkingDirectory("/foo");
        client.printWorkingDirectory();
        client.listFiles("/foo");
        client.storeFile("bar", new ByteArrayInputStream("content".getBytes()));
        client.rename("bar", "baz");
        client.deleteFile("baz");
    }


    private static class ConsoleReceiver implements DataReceiver {
        @Override
        public void receive(String name, InputStream data) throws IOException {
            System.out.println("receiving file: [" + name + "]");
            System.out.println("receiving data:");
            IOUtils.copy(data, System.out);
            System.out.println("");
        }
    }
}
