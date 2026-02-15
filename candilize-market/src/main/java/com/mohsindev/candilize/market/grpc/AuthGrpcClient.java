package com.mohsindev.candilize.market.grpc;

import com.mohsindev.candilize.proto.auth.AuthServiceGrpc;
import com.mohsindev.candilize.proto.auth.ValidateTokenRequest;
import com.mohsindev.candilize.proto.auth.ValidateTokenResponse;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * gRPC client to candilize-auth for JWT validation.
 * Replaces local JwtValidator - all auth is delegated to auth service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGrpcClient {

    private final AuthServiceGrpc.AuthServiceBlockingStub authStub;

    public ValidateResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            return ValidateResult.invalid("Token is empty");
        }
        try {
            ValidateTokenResponse response = authStub.validateToken(
                    ValidateTokenRequest.newBuilder().setToken(token).build());
            if (response.getValid()) {
                return ValidateResult.valid(response.getUsername(), response.getRolesList());
            }
            return ValidateResult.invalid(response.getErrorMessage());
        } catch (StatusRuntimeException e) {
            log.debug("Auth gRPC call failed: {}", e.getStatus());
            return ValidateResult.invalid(e.getStatus().getDescription());
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return ValidateResult.invalid(e.getMessage());
        }
    }

    public record ValidateResult(boolean valid, String username, List<String> roles, String errorMessage) {
        public static ValidateResult valid(String username, List<String> roles) {
            return new ValidateResult(true, username, roles, null);
        }
        public static ValidateResult invalid(String error) {
            return new ValidateResult(false, null, List.of(), error);
        }
    }
}
