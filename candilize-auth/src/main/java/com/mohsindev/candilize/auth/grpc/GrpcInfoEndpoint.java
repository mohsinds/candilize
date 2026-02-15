package com.mohsindev.candilize.auth.grpc;

import io.grpc.ServerServiceDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.grpc.server.service.GrpcServiceDiscoverer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "grpc")
@RequiredArgsConstructor
public class GrpcInfoEndpoint {

    private final GrpcServiceDiscoverer serviceDiscoverer;

    @ReadOperation
    public Map<String, Object> grpcInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("type", "grpc-server");
        info.put("registeredServices", serviceDiscoverer.listServiceNames());

        List<Map<String, Object>> serviceDetails = serviceDiscoverer.findServices().stream()
                .map(spec -> {
                    ServerServiceDefinition definition = spec.service().bindService();
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("serviceName", definition.getServiceDescriptor().getName());
                    detail.put("methods", definition.getServiceDescriptor().getMethods().stream()
                            .map(m -> Map.of(
                                    "fullName", m.getFullMethodName(),
                                    "type", m.getType().name()
                            )).toList());
                    return detail;
                }).toList();
        info.put("serviceDetails", serviceDetails);

        return info;
    }
}
