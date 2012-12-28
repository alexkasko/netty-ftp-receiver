package com.alexkasko.netty.ftp;

import org.apache.commons.io.IOUtils;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.junit.Test;

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
    public void test() throws InterruptedException {
        ChannelFactory factory = new NioServerSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool());
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setPipelineFactory(new PipelineFactory());
        bootstrap.bind(new InetSocketAddress(2121));
//        Thread.sleep(100000);
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
            System.out.println(name);
            IOUtils.copy(data, System.out);
        }
    }
}
