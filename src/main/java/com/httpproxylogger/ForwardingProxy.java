package com.httpproxylogger;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.istack.internal.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.Endpoint;
import javax.xml.ws.http.HTTPBinding;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by jarmolow on 2015-06-25.
 */
class ForwardingProxy {

    private final DateFormat dateFormat;
    private final String separtor;
    private File outputPath;
    private int port;
    private String targetHost;
    private int targetPort;
    private CloseableHttpClient httpclient;

    public ForwardingProxy(File outputPath, int port, String targetHost, int targetPort) {
        this.outputPath = new File(outputPath, port + "_" + targetHost + "_" + targetPort);
        this.port = port;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        httpclient = HttpClientBuilder.create().setMaxConnTotal(10).build();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss_SS");
        separtor = System.getProperty("line.separator");
    }

    public void bind() throws Exception {
        System.out.printf("Binding to port:%s. Forwarding to %s:%s", port, targetHost, targetPort);
        System.out.println();
        outputPath.mkdirs();
        System.out.println("Writing to outputPath = " + outputPath.getAbsolutePath());
        final Server server = new Server(port);
        server.setHandler(new MyHandler());
        server.start();
        server.join();
        Endpoint.create(HTTPBinding.HTTP_BINDING, new MyHandler()).publish("http://localhost:" + port + "/");
        Thread.sleep(Long.MAX_VALUE);
    }

    private class MyHandler extends AbstractHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int requestMode) throws IOException, ServletException {
            HttpUriRequest r = null;
            final Date time = Calendar.getInstance().getTime();
            final String uri = targetHost + ":" + targetPort + request.getRequestURI();
            final String requestBody = org.apache.commons.io.IOUtils.toString(request.getInputStream());
            String type = null;
            if (request.getMethod().equals("GET")) {
                type = "GET";
                r = new HttpGet("http://" + uri);
            } else if (request.getMethod().equals("POST")) {
                type = "POST";
                final HttpPost post = new HttpPost("http://" + uri);
                post.setEntity(new StringEntity(requestBody));
                r = post;
            }


            final Enumeration headerNames = request.getHeaderNames();
            Map<String, String> requestHeaders = Maps.newHashMap();
            while (headerNames.hasMoreElements()) {
                String name = (String) headerNames.nextElement();
                String value = request.getHeader(name);
                if (name.equalsIgnoreCase("Host")) {
                    value = targetHost;
                }
                if (name.equalsIgnoreCase("Content-Length")) {
                    continue;
                }
                r.setHeader(name, value);
                requestHeaders.put(name, value);
            }
            logRequest(time, type, uri, requestBody, requestHeaders);

            final CloseableHttpResponse myResponse = httpclient.execute(r);
            try {
                String responseText = "";
                if (myResponse.getEntity() != null) {
                    responseText = org.apache.commons.io.IOUtils.toString(myResponse.getEntity().getContent(), Charset.forName("UTF-8"));
                }
                for (Header header : myResponse.getAllHeaders()) {
                    if (header.getName().equalsIgnoreCase("Content-Length") || header.getName().equalsIgnoreCase("Transfer-Encoding")) {
                        continue;
                    }
                    response.setHeader(header.getName(), header.getValue());
                }


                logResponse(time, type, uri, myResponse.getStatusLine().getStatusCode(), responseText, myResponse.getAllHeaders());

                response.setStatus(myResponse.getStatusLine().getStatusCode());
                if (!responseText.isEmpty()) {
                    final ServletOutputStream outputStream = response.getOutputStream();

                    outputStream.write(responseText.getBytes(Charset.forName("UTF-8")));
                    outputStream.flush();
                    outputStream.close();
                }
            } finally {
                myResponse.close();
            }

        }
    }

    private void logRequest(Date time, String type, String uri, String body, Map<String, String> requestHeaders) throws IOException {
        System.out.println("Handling: " + dateFormat.format(time) + " " + type + " " + uri);
        FileUtils.write(new File(outputPath, getCommonFileNamePart(time, type, uri) + ".requestBody"), body);
        FileUtils.write(new File(outputPath, getCommonFileNamePart(time, type, uri) + ".requestHeaders"),
                "Url=http://" + uri + separtor +
                        "Headers:" + separtor +
                        joiner(requestHeaders.entrySet()));
    }

    private String joiner(Iterable<Map.Entry<String, String>> entries) {
        return Joiner.on(separtor).withKeyValueSeparator("=").join(entries);
    }

    private static String sanitizeUri(String uri) {
        return uri.replaceAll("[^A–Za-z0-9\\._\\-–]", "_");
    }

    private void logResponse(Date time, String type, String uri, long code, String body, Header[] allHeaders) throws IOException {
        FileUtils.write(new File(outputPath, getCommonFileNamePart(time, type, uri) + "--" + code + ".responseBody"), body);

        final Iterable<Map.Entry<String, String>> asMapEntries = Iterables.transform(Arrays.asList(allHeaders), new Function<Header, Map.Entry<String, String>>() {
                    @Nullable
                    @Override
                    public Map.Entry<String, String> apply(Header input) {
                        return new AbstractMap.SimpleEntry<String, String>(input.getName(), input.getValue());
                    }
                }
        );
        FileUtils.write(new File(outputPath, getCommonFileNamePart(time, type, uri) + "--" + code + ".responseHeaders"),
                "Headers:" + separtor +
                        joiner(asMapEntries));
    }

    private String getCommonFileNamePart(Date time, String type, String uri) {
        return dateFormat.format(time) + "--" + type + "--" + sanitizeUri(uri);
    }

}
