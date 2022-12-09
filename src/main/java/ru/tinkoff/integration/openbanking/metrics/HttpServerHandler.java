package ru.tinkoff.integration.openbanking.metrics;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@ChannelHandler.Sharable
public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(HttpServerHandler.class.getName());
    private static final String DEFAULT_READINESS_URI = "/system/readiness";
    private static final String DEFAULT_LIVENESS_URI = "/system/liveness";
    private static final String DEFAULT_METRICS_URI = "/metrics";

    private String readinessUri;
    private String livenessUri;
    private String metricsUri;
    private volatile ReadinessStatus readinessStatus;
    private volatile HealthStatus healthStatus;
    private final PrometheusMeterRegistry registry;

    public HttpServerHandler(PrometheusMeterRegistry registry) {
        this.livenessUri = DEFAULT_LIVENESS_URI;
        this.readinessUri = DEFAULT_READINESS_URI;
        this.metricsUri = DEFAULT_METRICS_URI;
        this.readinessStatus = ReadinessStatus.INITIALIZING;
        this.healthStatus = HealthStatus.HEALTHY;
        this.registry = registry;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    public void setReadinessUri(String uri) {
        if (uri != null) {
            this.readinessUri = uri;
        }
    }

    public void setLivenessUri(String uri) {
        if (uri != null) {
            this.livenessUri = uri;
        }
    }

    public void setMetricsUri(String uri) {
        if (uri != null) {
            this.metricsUri = uri;
        }
    }

    public void setReadinessStatus(ReadinessStatus status) {
        this.readinessStatus = status;
    }

    public void setHealthStatus(HealthStatus status) {
        this.healthStatus = status;
    }

    private void processLiveness(ChannelHandlerContext ctx, HttpRequest request) {
        var buff = switch (this.healthStatus) {
            case HEALTHY -> Unpooled.copiedBuffer("{\"result\":\"OK\"}", CharsetUtil.UTF_8);
            case UNHEALTHY -> Unpooled.copiedBuffer("{\"result\":\"Unhealthy\"}", CharsetUtil.UTF_8);
        };

        var resultCode = switch (this.healthStatus) {
            case HEALTHY -> OK;
            case UNHEALTHY -> SERVICE_UNAVAILABLE;
        };

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, resultCode, buff);
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        writeResponse(ctx, request, httpResponse);
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, FullHttpResponse response) {
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        ctx.write(response);

        if (!keepAlive) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void processReadiness(ChannelHandlerContext ctx, HttpRequest request) {
        var buff = switch (this.readinessStatus) {
            case INITIALIZING -> Unpooled.copiedBuffer("{\"result\":\"Initializing\"}", CharsetUtil.UTF_8);
            case LIVE -> Unpooled.copiedBuffer("{\"result\":\"OK\"}", CharsetUtil.UTF_8);
            case SHUTDOWN -> Unpooled.copiedBuffer("{\"result\":\"Shutdown\"}", CharsetUtil.UTF_8);
        };

        var resultCode = this.readinessStatus == ReadinessStatus.LIVE ? OK : SERVICE_UNAVAILABLE;

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, resultCode, buff);
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        writeResponse(ctx, request, httpResponse);
    }

    private void processMetrics(ChannelHandlerContext ctx, HttpRequest request) {
        var buff = Unpooled.copiedBuffer(registry.scrape(), CharsetUtil.UTF_8);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buff);
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8");

        writeResponse(ctx, request, httpResponse);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest request) {
            if (HttpMethod.GET != request.method()) {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED,
                        Unpooled.EMPTY_BUFFER);
                httpResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);

                ctx.write(httpResponse);

                return;
            }

            var uri = request.uri();

            if (livenessUri.equals(uri)) {
                processLiveness(ctx, request);
            } else if (readinessUri.equals(uri)) {
                processReadiness(ctx, request);
            } else if (metricsUri.equals(uri)) {
                processMetrics(ctx, request);
            } else {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND,
                        Unpooled.copiedBuffer("NOT FOUND", CharsetUtil.UTF_8));
                httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                writeResponse(ctx, request, httpResponse);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("http handler error", cause);

        ctx.close();
    }
}