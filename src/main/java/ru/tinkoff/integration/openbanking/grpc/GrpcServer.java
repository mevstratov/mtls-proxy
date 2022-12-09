package ru.tinkoff.integration.openbanking.grpc;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import ru.tinkoff.integration.openbanking.netty.Netty;
import ru.tinkoff.integration.openbanking.service.RandomService;

import java.io.IOException;

public class GrpcServer {
    private final int port;
    private Server server;

    public GrpcServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = NettyServerBuilder.forPort(this.port)
                .bossEventLoopGroup(Netty.eventLoopGroup(null, 2))
                .workerEventLoopGroup(Netty.eventLoopGroup(null, 2))
                .addService(new RandomService())
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
    }

    public void shutdown() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void await() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
