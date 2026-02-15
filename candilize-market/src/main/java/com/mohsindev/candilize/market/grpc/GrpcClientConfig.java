package com.mohsindev.candilize.market.grpc;

import com.mohsindev.candilize.proto.auth.AuthServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * gRPC client configuration for candilize-market.
 * Creates blocking stubs to call candilize-auth (channel name "auth", see application.properties).
 * Used by JwtAuthenticationFilter to validate JWT via AuthGrpcClient.
 */
@Configuration
public class GrpcClientConfig {

    @Bean
    AuthServiceGrpc.AuthServiceBlockingStub authServiceBlockingStub(GrpcChannelFactory channelFactory) {
        return AuthServiceGrpc.newBlockingStub(channelFactory.createChannel("auth"));
    }
}
