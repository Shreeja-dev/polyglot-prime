package org.techbd.service.http.hub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class CustomMultiPartFileRequestWrapper extends HttpServletRequestWrapper {

    private final MultipartFile file;
    public CustomMultiPartFileRequestWrapper(HttpServletRequest request, MultipartFile file) {
        super(request);
        this.file = file;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(file.getBytes());

        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    @Override
    public java.io.BufferedReader getReader() throws IOException {
        return new java.io.BufferedReader(new java.io.InputStreamReader(this.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return (int) file.getSize();
    }

    @Override
    public long getContentLengthLong() {
        return file.getSize();
    }

    @Override
    public String getContentType() {
        return file.getContentType(); 
    }
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> params = super.getParameterMap();
        return Collections.singletonMap("file", new String[] { file.getOriginalFilename() });
    }

    @Override
    public String getParameter(String name) {
        if ("file".equals(name)) {
            return file.getOriginalFilename();
        }
        return super.getParameter(name);
    }
}

