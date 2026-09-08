package dev.canverse.stocks.platform.web;

import org.springframework.http.HttpHeaders;

public final class CacheHeaders {

    private CacheHeaders() {}

    public static HttpHeaders noStore() {
        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        return headers;
    }
}
