package com.alexkasko.netty.ftp;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * User: alexkasko
 * Date: 12/28/12
 */
public class FtpServerTest {

    @Test
    public void test() throws IOException {
        ChannelFactory factory = new NioServerSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool());
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setPipelineFactory(new PipelineFactory());
        bootstrap.bind(new InetSocketAddress(2121));
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

    // testonly, use proper instantiation in production
    private static class PipelineFactory implements ChannelPipelineFactory {
        @Override
        public ChannelPipeline getPipeline() throws Exception {
            ChannelPipeline pipe = Channels.pipeline();
            pipe.addLast("decoder", new CrlfStringDecoder());
            pipe.addLast("executor", new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(1, 1048576, 1048576)));
            pipe.addLast("handler", new FtpServerHandler(new ConsoleReceiver()));
            return pipe;
        }
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
