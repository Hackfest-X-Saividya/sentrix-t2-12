package ai.zerotrace.zeroremediate.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.*;

@Component
public class PayloadLimitFilter implements Filter {
    private static final int MAX=16*1024;
    @Override public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException{
        HttpServletRequest request=(HttpServletRequest)req; HttpServletResponse response=(HttpServletResponse)res;
        if("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/")) {
            if(request.getContentLengthLong()>MAX){reject(response);return;}
            if(request.getContentLengthLong()<0 && request.getRequestURI().equals("/api/v1/findings")){response.setStatus(411);response.setContentType("application/json");response.getWriter().write("{\"error\":\"content_length_required\",\"message\":\"Finding submissions require a bounded Content-Length\"}");return;}
        }
        chain.doFilter(req,res);
    }
    private void reject(HttpServletResponse response)throws IOException{response.setStatus(413);response.setContentType("application/json");response.getWriter().write("{\"error\":\"payload_too_large\",\"message\":\"Request body exceeds 16384 bytes\"}");}
}
