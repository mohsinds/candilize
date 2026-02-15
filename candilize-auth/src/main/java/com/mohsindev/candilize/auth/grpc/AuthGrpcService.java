package com.mohsindev.candilize.auth.grpc;

import com.mohsindev.candilize.auth.security.JwtTokenProvider;
import com.mohsindev.candilize.proto.auth.AuthServiceGrpc;
import com.mohsindev.candilize.proto.auth.GetUserByUsernameRequest;
import com.mohsindev.candilize.proto.auth.GetUserByUsernameResponse;
import com.mohsindev.candilize.proto.auth.ValidateTokenRequest;
import com.mohsindev.candilize.proto.auth.ValidateTokenResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.stream.Collectors;

/**
 * gRPC server for token validation and user lookup (service-to-service).
 * Called by candilize-market and candilize-technical to validate JWT.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final JwtTokenProvider jwtTokenProvider;
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Override
    public void validateToken(ValidateTokenRequest request, StreamObserver<ValidateTokenResponse> responseObserver) {
        String token = request.getToken();
        try {
            if (token == null || token.isBlank()) {
                responseObserver.onNext(ValidateTokenResponse.newBuilder()
                        .setValid(false)
                        .setErrorMessage("Token is empty")
                        .build());
                responseObserver.onCompleted();
                return;
            }
            if (!jwtTokenProvider.validateToken(token)) {
                responseObserver.onNext(ValidateTokenResponse.newBuilder()
                        .setValid(false)
                        .setErrorMessage("Invalid or expired token")
                        .build());
                responseObserver.onCompleted();
                return;
            }
            String username = jwtTokenProvider.getUsernameFromToken(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            List<String> roles = user.getAuthorities().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            responseObserver.onNext(ValidateTokenResponse.newBuilder()
                    .setValid(true)
                    .setUsername(username)
                    .addAllRoles(roles)
                    .build());
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            responseObserver.onNext(ValidateTokenResponse.newBuilder()
                    .setValid(false)
                    .setErrorMessage(e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<GetUserByUsernameResponse> responseObserver) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
            List<String> roles = user.getAuthorities().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            responseObserver.onNext(GetUserByUsernameResponse.newBuilder()
                    .setFound(true)
                    .setUsername(user.getUsername())
                    .addAllRoles(roles)
                    .build());
        } catch (Exception e) {
            responseObserver.onNext(GetUserByUsernameResponse.newBuilder()
                    .setFound(false)
                    .build());
        }
        responseObserver.onCompleted();
    }
}
