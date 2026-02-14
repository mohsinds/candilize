package com.mohsindev.candilize.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that provides cross-cutting logging for the application.
 * Logs method entry, arguments (sanitized to avoid logging sensitive data), execution time,
 * and exit/exception for all controller and service layer methods.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private static final String SENSITIVE_MASK = "***";
    private static final String[] SENSITIVE_PARAM_NAMES = {"password", "refreshToken", "secret", "token"};

    /**
     * Around advice for all public methods in REST controllers (api package and api.controller).
     * Logs: method name, sanitized arguments, duration, and result or exception.
     */
    @Around("execution(* com.mohsindev.candilize.api..*.*(..)) || execution(* com.mohsindev.candilize.api.controller..*.*(..))")
    public Object logControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logInvocation(joinPoint, "CONTROLLER");
    }

    /**
     * Around advice for all public methods in service layer.
     * Logs: method name, sanitized arguments, duration, and result or exception.
     */
    @Around("execution(* com.mohsindev.candilize.service..*.*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logInvocation(joinPoint, "SERVICE");
    }

    /**
     * Around advice for all public methods in security package (e.g. JwtTokenProvider, UserDetailsService).
     */
    @Around("execution(* com.mohsindev.candilize.security..*.*(..))")
    public Object logSecurityMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logInvocation(joinPoint, "SECURITY");
    }

    private Object logInvocation(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethod = className + "." + methodName;

        Object[] args = joinPoint.getArgs();
        String argsStr = sanitizeArgs(joinPoint, args);

        log.debug("[{}] Entering {} | args: {}", layer, fullMethod, argsStr);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.debug("[{}] Exited {} | duration: {} ms", layer, fullMethod, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.warn("[{}] Exception in {} after {} ms | exception: {}", layer, fullMethod, duration, ex.getMessage());
            throw ex;
        }
    }

    private String sanitizeArgs(ProceedingJoinPoint joinPoint, Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            String paramName = i < paramNames.length ? paramNames[i] : "arg" + i;
            Object value = args[i];
            if (isSensitive(paramName)) {
                sb.append(paramName).append("=").append(SENSITIVE_MASK);
            } else if (value != null && value.getClass().isArray()) {
                sb.append(paramName).append("=[...]");
            } else if (value != null && value.getClass().getName().startsWith("com.mohsindev")) {
                sb.append(paramName).append("=").append(value.getClass().getSimpleName());
            } else {
                sb.append(paramName).append("=").append(value);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private boolean isSensitive(String paramName) {
        if (paramName == null) return false;
        String lower = paramName.toLowerCase();
        for (String sensitive : SENSITIVE_PARAM_NAMES) {
            if (lower.contains(sensitive)) return true;
        }
        return false;
    }
}
