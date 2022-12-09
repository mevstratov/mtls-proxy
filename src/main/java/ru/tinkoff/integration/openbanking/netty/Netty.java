package ru.tinkoff.integration.openbanking.netty;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NettyRuntime;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;

public class Netty {
    public static EventLoopGroup eventLoopGroup(@Nullable ThreadFactory threadFactory, @Nullable Integer size) {
        if (size == null) {
            size = NettyRuntime.availableProcessors() * 2;
        }

        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(size, threadFactory);
        }

        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(size, threadFactory);
        }

        return new NioEventLoopGroup(size, threadFactory);
    }

    public static Class<? extends Channel> channelType() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }

        if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        }

        return NioSocketChannel.class;
    }

    public static Class<? extends ServerChannel> serverChannelType() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }

        if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        }

        return NioServerSocketChannel.class;
    }
}
