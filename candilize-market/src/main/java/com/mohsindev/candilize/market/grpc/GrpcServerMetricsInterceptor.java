package com.mohsindev.candilize.market.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
@RequiredArgsConstructor
public class GrpcServerMetricsInterceptor implements ServerInterceptor, Ordered {

    private final MeterRegistry meterRegistry;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        Timer.Sample sample = Timer.start(meterRegistry);

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                String statusCode = status.getCode().name();
                sample.stop(Timer.builder("grpc.server.call.duration")
                        .tag("method", methodName)
                        .tag("status", statusCode)
                        .register(meterRegistry));
                Counter.builder("grpc.server.calls")
                        .tag("method", methodName)
                        .tag("status", statusCode)
                        .register(meterRegistry)
                        .increment();
                super.close(status, trailers);
            }
        };

        return next.startCall(wrappedCall, headers);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }
}
