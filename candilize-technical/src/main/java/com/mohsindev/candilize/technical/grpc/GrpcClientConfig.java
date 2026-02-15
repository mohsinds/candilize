package com.mohsindev.candilize.technical.grpc;

import com.mohsindev.candilize.proto.auth.AuthServiceGrpc;
import com.mohsindev.candilize.proto.market.MarketServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * gRPC client configuration for candilize-technical.
 * - auth: Calls candilize-auth for JWT validation (ValidateToken).
 * - market: Calls candilize-market for candle data (GetCandles).
 * Channel addresses from spring.grpc.client.channels.*.address in application.properties.
 */
@Configuration
public class GrpcClientConfig {

    @Bean
    AuthServiceGrpc.AuthServiceBlockingStub authServiceBlockingStub(GrpcChannelFactory channelFactory) {
        return AuthServiceGrpc.newBlockingStub(channelFactory.createChannel("auth"));
    }

    @Bean
    MarketServiceGrpc.MarketServiceBlockingStub marketServiceBlockingStub(GrpcChannelFactory channelFactory) {
        return MarketServiceGrpc.newBlockingStub(channelFactory.createChannel("market"));
    }
}
