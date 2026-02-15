package com.mohsindev.candilize.market.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalClientInterceptor
@RequiredArgsConstructor
public class GrpcClientMetricsInterceptor implements ClientInterceptor, Ordered {

    private final MeterRegistry meterRegistry;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        String methodName = method.getFullMethodName();
        Timer.Sample sample = Timer.start(meterRegistry);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Listener<RespT> wrappedListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        String statusCode = status.getCode().name();
                        sample.stop(Timer.builder("grpc.client.call.duration")
                                .tag("method", methodName)
                                .tag("status", statusCode)
                                .register(meterRegistry));
                        Counter.builder("grpc.client.calls")
                                .tag("method", methodName)
                                .tag("status", statusCode)
                                .register(meterRegistry)
                                .increment();
                        super.onClose(status, trailers);
                    }
                };
                super.start(wrappedListener, headers);
            }
        };
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }
}
