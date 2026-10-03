package com.knowledgeapplication.api.ask;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;

/** Bound JSON before deserialization, including chunked requests with no Content-Length. */
@Component
public class AskRequestSizeFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getServletPath().equals("/api/ask"); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","private, no-store, max-age=0");
        if (!"POST".equals(request.getMethod())) { chain.doFilter(request,response); return; }
        byte[] data=request.getInputStream().readNBytes(16385);
        if(data.length>16384) {
            response.setStatus(400); response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"MALFORMED_REQUEST\",\"message\":\"Ask request exceeds the input limit\",\"fieldErrors\":{}}"); return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var bytes=new ByteArrayInputStream(data);
                return new ServletInputStream() {
                    @Override public int read() { return bytes.read(); }
                    @Override public boolean isFinished() { return bytes.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
        },response);
    }
}
