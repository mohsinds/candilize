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
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@GlobalClientInterceptor
public class GrpcClientLoggingInterceptor implements ClientInterceptor, Ordered {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        String methodName = method.getFullMethodName();
        long startTime = System.nanoTime();

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Listener<RespT> wrappedListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                        if (status.isOk()) {
                            log.info("gRPC client | {} | OK | {}ms", methodName, durationMs);
                        } else {
                            log.warn("gRPC client | {} | {} | {} | {}ms",
                                    methodName, status.getCode(), status.getDescription(), durationMs);
                        }
                        super.onClose(status, trailers);
                    }
                };
                super.start(wrappedListener, headers);
            }
        };
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
