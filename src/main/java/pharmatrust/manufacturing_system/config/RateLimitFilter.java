package pharmatrust.manufacturing_system.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Bucket4j (token bucket algorithm).
 *
 * Rules:
 *  - /api/v1/auth/**   → 10 requests / minute  (brute-force protection)
 *  - /api/v1/scan/**   → 60 requests / minute  (QR scan endpoints)
 *  - All other APIs    → 100 requests / minute (general API)
 *
 * Buckets are keyed by client IP address.
 * Returns HTTP 429 Too Many Requests when limit exceeded.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // IP → Bucket maps per endpoint category
    private final Map<String, Bucket> authBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> scanBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    @Value("${rate-limit.auth.requests-per-minute:10}")
    private int authLimit;

    @Value("${rate-limit.scan.requests-per-minute:60}")
    private int scanLimit;

    @Value("${rate-limit.general.requests-per-minute:100}")
    private int generalLimit;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String path = request.getRequestURI();

        Bucket bucket = resolveBucket(ip, path);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded — IP: {}, path: {}", ip, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Please slow down.\",\"status\":429}"
            );
        }
    }

    private Bucket resolveBucket(String ip, String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return authBuckets.computeIfAbsent(ip, k -> buildBucket(authLimit));
        } else if (path.startsWith("/api/v1/scan/")) {
            return scanBuckets.computeIfAbsent(ip, k -> buildBucket(scanLimit));
        } else {
            return generalBuckets.computeIfAbsent(ip, k -> buildBucket(generalLimit));
        }
    }

    private Bucket buildBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
