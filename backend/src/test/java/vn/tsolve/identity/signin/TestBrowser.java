package vn.tsolve.identity.signin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-only: a headless browser. It follows no redirect by itself, keeps cookies per host name (so a cookie set by a
 * backend on one port is sent to a backend on another port, and a {@code Secure} cookie is kept on plain http, which
 * Chrome does for {@code *.localhost}), rewrites a few host names to where the container really listens, and can send
 * a request to a local backend as if it came through the proxy ({@code X-Forwarded-Host} and {@code -Port}).
 */
final class TestBrowser {

    /** One answer: the status, the headers (lower-case names), the body, and the Set-Cookie headers. */
    record Reply(int status, Map<String, List<String>> headers, String body) {

        String location() {
            return header("location");
        }

        String header(String name) {
            List<String> values = headers.get(name.toLowerCase(java.util.Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        List<String> setCookies() {
            return headers.getOrDefault("set-cookie", List.of());
        }

        /** The Set-Cookie headers for this cookie name. */
        List<String> setCookies(String name) {
            return setCookies().stream().filter(h -> h.startsWith(name + "=")).toList();
        }
    }

    private static final Pattern HREF = Pattern.compile("<a[^>]*href=\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** host name -> cookie name -> value */
    private final Map<String, Map<String, String>> jar = new LinkedHashMap<>();

    private final Map<String, String> hostRewrites;

    /** Every Set-Cookie name any answer ever carried, to show a cookie (JSESSIONID) never appeared. */
    private final List<String> seenCookieNames = new ArrayList<>();

    private final List<String> pagesShown = new ArrayList<>();

    private String lastUrl;

    /** @param hostRewrites {@code host:port} the browser is asked for -> {@code host:port} it really connects to */
    TestBrowser(Map<String, String> hostRewrites) {
        this.hostRewrites = Map.copyOf(hostRewrites);
    }

    TestBrowser() {
        this(Map.of());
    }

    // ---- requests

    /** A request to any URL (Keycloak, the fake Google); the cookies are those of the URL's host name. */
    Reply get(String url) {
        lastUrl = url;
        URI uri = URI.create(url);
        String hostPort = uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        String real = hostRewrites.get(hostPort);
        URI target = real == null ? uri : URI.create(uri.toString().replace("//" + hostPort, "//" + real));
        Reply reply = send(target, uri.getHost(), Map.of());
        if (reply.status() == 200) {
            pagesShown.add(url);
        }
        return reply;
    }

    /** The URL of every page (a 200 answer) the browser has been shown, in order. */
    List<String> pagesShown() {
        return List.copyOf(pagesShown);
    }

    /**
     * A request to a local backend on {@code backendPort} for the public URL: the backend sees the public host and
     * port through the forwarded headers, and the cookies are those of the public host name.
     */
    Reply backend(String publicUrl, int backendPort) {
        URI pub = URI.create(publicUrl);
        String pathAndQuery = pub.getRawPath() + (pub.getRawQuery() == null ? "" : "?" + pub.getRawQuery());
        URI target = URI.create("http://localhost:" + backendPort + pathAndQuery);
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("X-Forwarded-Host", pub.getHost());
        extra.put("X-Forwarded-Port", String.valueOf(pub.getPort() < 0 ? 80 : pub.getPort()));
        extra.put("X-Forwarded-Proto", pub.getScheme());
        return send(target, pub.getHost(), extra);
    }

    private Reply send(URI target, String cookieHost, Map<String, String> extraHeaders) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(60)).GET();
        extraHeaders.forEach(builder::header);
        Map<String, String> cookies = jar.get(cookieHost);
        if (cookies != null && !cookies.isEmpty()) {
            StringBuilder header = new StringBuilder();
            cookies.forEach((name, value) -> {
                if (header.length() > 0) {
                    header.append("; ");
                }
                header.append(name).append('=').append(value);
            });
            builder.header("Cookie", header.toString());
        }
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Request to " + target + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.headers()
                .map()
                .forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT), values));
        Reply reply = new Reply(response.statusCode(), headers, response.body());
        store(cookieHost, reply.setCookies());
        return reply;
    }

    private void store(String host, List<String> setCookies) {
        for (String header : setCookies) {
            String pair = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            seenCookieNames.add(name);
            boolean expired =
                    value.isEmpty() || header.toLowerCase(java.util.Locale.ROOT).contains("max-age=0");
            if (expired) {
                jar.computeIfAbsent(host, h -> new LinkedHashMap<>()).remove(name);
            } else {
                jar.computeIfAbsent(host, h -> new LinkedHashMap<>()).put(name, value);
            }
        }
    }

    /** One decoded query parameter of a URL, or null. */
    static String param(String url, String name) {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(url)
                .build()
                .getQueryParams()
                .toSingleValueMap()
                .entrySet()
                .stream()
                .filter(e -> e.getKey().equals(name))
                .map(e -> java.net.URLDecoder.decode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    // ---- the cookie jar

    String cookie(String host, String name) {
        return jar.getOrDefault(host, Map.of()).get(name);
    }

    void setCookie(String host, String name, String value) {
        jar.computeIfAbsent(host, h -> new LinkedHashMap<>()).put(name, value);
    }

    /** Every cookie the browser holds for this host name, as sent. */
    Map<String, String> cookies(String host) {
        return Map.copyOf(jar.getOrDefault(host, Map.of()));
    }

    List<String> seenCookieNames() {
        return List.copyOf(seenCookieNames);
    }

    // ---- driving a sign-in

    /** The URL of the last request made with {@link #get(String)}. */
    String lastUrl() {
        return lastUrl;
    }

    /**
     * Test-only (Task 31): follows 3xx and stops at the first answer that is not a 3xx, whatever its status (Keycloak
     * error pages are often 400), or at a 302 to {@code callbackPrefix}. A 200 page of the fake Google (a host name that
     * starts with {@code fake-google}) is passed on by clicking its first link only if {@code clickChooser}; any other
     * page is never clicked. {@link #lastUrl()} then holds the URL that gave the returned answer.
     */
    Reply followUntilSettled(Reply first, String callbackPrefix, boolean clickChooser) {
        Reply reply = first;
        for (int hop = 0; hop < 25; hop++) {
            String location = reply.location();
            if (reply.status() / 100 == 3 && location != null) {
                if (location.startsWith(callbackPrefix)) {
                    return reply;
                }
                reply = get(location);
            } else if (clickChooser
                    && reply.status() == 200
                    && lastUrl != null
                    && URI.create(lastUrl).getHost().startsWith("fake-google")) {
                Matcher link = HREF.matcher(reply.body());
                if (!link.find()) {
                    return reply;
                }
                reply = get(link.group(1).replace("&amp;", "&"));
            } else {
                return reply;
            }
        }
        throw new IllegalStateException("No settled page after 25 hops");
    }

    /**
     * Follows redirects and clicks the one link of a chooser page until Keycloak answers 302 to a URL that starts
     * with {@code callbackPrefix}. Returns that answer (the browser has not yet opened the callback).
     */
    Reply followUntilCallback(Reply first, String callbackPrefix) {
        Reply reply = first;
        for (int hop = 0; hop < 25; hop++) {
            String location = reply.location();
            if (reply.status() / 100 == 3 && location != null) {
                if (location.startsWith(callbackPrefix)) {
                    return reply;
                }
                reply = get(location);
            } else if (reply.status() == 200) {
                Matcher link = HREF.matcher(reply.body());
                if (!link.find()) {
                    throw new IllegalStateException("A page with no link and no redirect:\n" + reply.body());
                }
                reply = get(link.group(1).replace("&amp;", "&"));
            } else {
                throw new IllegalStateException("Unexpected answer " + reply.status() + ":\n" + reply.body());
            }
        }
        throw new IllegalStateException("No callback after 25 hops");
    }
}
