package ru.tinkoff.integration.openbanking.metrics;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.integration.openbanking.netty.Netty;

public class MetricsService {
    private static final Logger log = LoggerFactory.getLogger(MetricsService.class.getName());
    private final int port;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final HttpServerHandler serverHandler;

    public MetricsService(int port, PrometheusMeterRegistry registry) {
        this.port = port;
        this.bossGroup = Netty.eventLoopGroup(null, 2);
        this.workerGroup = Netty.eventLoopGroup(null, 2);
        this.serverHandler = new HttpServerHandler(registry);
    }

    public void stop() {
        try {
            workerGroup.shutdownGracefully().sync();
        } catch (InterruptedException e) {
            log.error("shutdown worker group", e);
        }
        try {
            bossGroup.shutdownGracefully().sync();
        } catch (InterruptedException e) {
            log.error("shutdown bass group", e);
        }
    }

    public void setReadiness(ReadinessStatus status) {
        this.serverHandler.setReadinessStatus(status);
    }

    public void run() throws Exception {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .channel(Netty.serverChannelType())
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpRequestDecoder());
                        p.addLast(new HttpResponseEncoder());
                        p.addLast(MetricsService.this.serverHandler);
                    }
                });

        ChannelFuture f = b.bind(port);
        var srvChannel = f.awaitUninterruptibly();

        if (f.isCancelled()) {
            // Connection attempt cancelled by user
            System.out.println("Bind canceled");
        } else if (!f.isSuccess()) {
            f.cause().printStackTrace();
        } else {
            new Thread(() -> {
                try {
                    srvChannel.channel()
                            .closeFuture()
                            .sync();
                } catch (InterruptedException e) {
                    log.error("wait channel close", e);
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }
}
