package ai.zerotrace.zerowatch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PayloadLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BODY_BYTES = 16 * 1024;
    private final ObjectMapper mapper;
    private final Clock clock;
    public PayloadLimitFilter(ObjectMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, jakarta.servlet.ServletException {
        if (!"POST".equals(request.getMethod()) || !request.getRequestURI().equals("/api/v1/zerowatch/events")) {
            chain.doFilter(request, response); return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) { tooLarge(response); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) { tooLarge(response); return; }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json"); response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), Map.of("error", "payload_too_large", "message", "Event payload exceeds 16384 bytes.", "timestamp", clock.instant().toString()));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    try { if (input.available() == 0) listener.onAllDataRead(); else listener.onDataAvailable(); }
                    catch (IOException e) { listener.onError(e); }
                }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding() == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(getCharacterEncoding()))); }
    }
}
