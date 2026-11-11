package br.unicamp.ft.si600.eventos.payment.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded replay of the small JSON contract, including requests with chunked transfer encoding. */
final class PaymentRequestBody extends HttpServletRequestWrapper {
    private final ByteArrayInputStream bytes;
    PaymentRequestBody(HttpServletRequest request, byte[] body) { super(request); bytes = new ByteArrayInputStream(body); }
    @Override public ServletInputStream getInputStream() {
        return new ServletInputStream() {
            @Override public int read() { return bytes.read(); }
            @Override public int read(byte[] target, int offset, int length) { return bytes.read(target, offset, length); }
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Somente processamento síncrono."); }
        };
    }
    @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
}
