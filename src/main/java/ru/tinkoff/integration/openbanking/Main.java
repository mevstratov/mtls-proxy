package ru.tinkoff.integration.openbanking;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.integration.openbanking.grpc.GrpcServer;
import ru.tinkoff.integration.openbanking.metrics.MetricsService;
import ru.tinkoff.integration.openbanking.metrics.ReadinessStatus;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting module");

        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.globalRegistry.add(registry);

        try (var jvmGcMetrics = new JvmGcMetrics()) {
            jvmGcMetrics.bindTo(registry);
            new ClassLoaderMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new ProcessorMetrics().bindTo(registry);
            new JvmThreadMetrics().bindTo(registry);
            new UptimeMetrics().bindTo(registry);
            new FileDescriptorMetrics().bindTo(registry);

            var metrics = new MetricsService(18081, registry);

            metrics.run();

            var server = new GrpcServer(8080);

            metrics.setReadiness(ReadinessStatus.LIVE);

            log.info("Module started");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping module");
                metrics.setReadiness(ReadinessStatus.SHUTDOWN);
                server.shutdown();
                metrics.stop();
            }));

            server.await();
        } catch (Throwable e) {
            log.error("run application", e);
        }
    }
}