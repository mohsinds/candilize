package com.mohsindev.candilize.technical.grpc;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "grpc")
public class GrpcInfoEndpoint {

    @ReadOperation
    public Map<String, Object> grpcInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("type", "grpc-client-only");
        info.put("clientChannels", List.of(
                Map.of("name", "auth", "target", "static://localhost:9090"),
                Map.of("name", "market", "target", "static://localhost:9091")
        ));
        return info;
    }
}
