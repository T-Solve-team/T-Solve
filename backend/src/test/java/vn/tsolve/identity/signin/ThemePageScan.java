package vn.tsolve.identity.signin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-only: reads what a Keycloak page or the theme stylesheet loads or links to, with regular expressions (no HTML
 * parser is needed for these checks).
 */
final class ThemePageScan {

    /** One {@code <a>}: its href as written (entities decoded) and its text. */
    record Link(String href, String text) {}

    private static final Pattern SCRIPT = Pattern.compile("<script\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLER = Pattern.compile("<[^>]*\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_URL = Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC = Pattern.compile("\\ssrc\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRCSET = Pattern.compile("\\ssrcset\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_ATTR = Pattern.compile("\\shref\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR =
            Pattern.compile("<a\\b([^>]*)>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FORM_ACTION =
            Pattern.compile("<form\\b[^>]*\\saction\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)");
    private static final Pattern CSS_IMPORT = Pattern.compile("@import\\s+(?:url\\()?\\s*['\"]?([^'\")\\s;]+)");

    private ThemePageScan() {}

    static boolean hasScript(String html) {
        return SCRIPT.matcher(html).find();
    }

    static boolean hasEventHandlerAttribute(String html) {
        return HANDLER.matcher(html).find();
    }

    static boolean hasJavascriptUrl(String html) {
        return JS_URL.matcher(html).find();
    }

    /** Every {@code src}, {@code srcset}, {@code <link href>}, {@code url(...)} and {@code @import} of an HTML page. */
    static List<String> resourceUrls(String html) {
        List<String> urls = new ArrayList<>();
        collect(SRC, html, urls);
        Matcher set = SRCSET.matcher(html);
        while (set.find()) {
            for (String candidate : set.group(1).split(",")) {
                String trimmed = candidate.trim();
                if (!trimmed.isEmpty()) {
                    urls.add(decode(trimmed.split("\\s+")[0]));
                }
            }
        }
        Matcher tag = LINK_TAG.matcher(html);
        while (tag.find()) {
            collect(HREF_ATTR, tag.group(), urls);
        }
        urls.addAll(cssUrls(html));
        return urls;
    }

    /** Every {@code url(...)} and {@code @import} of a stylesheet (or of a {@code <style>} block). */
    static List<String> cssUrls(String css) {
        List<String> urls = new ArrayList<>();
        collect(CSS_URL, css, urls);
        collect(CSS_IMPORT, css, urls);
        return urls;
    }

    static List<Link> links(String html) {
        List<Link> links = new ArrayList<>();
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find()) {
            Matcher href = HREF_ATTR.matcher(anchor.group(1));
            if (href.find()) {
                links.add(new Link(decode(href.group(1)), visibleText(anchor.group(2))));
            }
        }
        return links;
    }

    static List<String> formActions(String html) {
        List<String> actions = new ArrayList<>();
        collect(FORM_ACTION, html, actions);
        return actions;
    }

    /** The first stylesheet link that points at the theme's {@code tsolve.css}, or null. */
    static String themeStylesheet(String html) {
        Matcher tag = LINK_TAG.matcher(html);
        while (tag.find()) {
            Matcher href = HREF_ATTR.matcher(tag.group());
            if (href.find() && href.group(1).contains("/login/tsolve/css/tsolve.css")) {
                return decode(href.group(1));
            }
        }
        return null;
    }

    static boolean hasInlineLogo(String html) {
        return Pattern.compile("<svg\\b", Pattern.CASE_INSENSITIVE)
                .matcher(html)
                .find();
    }

    /** The text a person reads: script and style removed, tags removed, entities decoded, spaces collapsed. */
    static String visibleText(String html) {
        String text = html.replaceAll("(?is)<(script|style)\\b.*?</\\1>", " ")
                .replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?s)<[^>]*>", " ");
        return decode(text).replaceAll("\\s+", " ").trim();
    }

    static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    /** True if a URL is relative or on this origin (or a data URI, which names no host). */
    static boolean isOwnOrigin(String url, String origin) {
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("data:") || lower.startsWith("#")) {
            return true;
        }
        if (lower.startsWith("//")) {
            return false;
        }
        if (lower.startsWith("/")) {
            return true;
        }
        String own = origin.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith(own)) {
            // the whole origin: the next character must end the authority (not "localhost:49153.evil.test")
            return lower.length() == own.length() || "/?#".indexOf(lower.charAt(own.length())) >= 0;
        }
        return !lower.matches("^[a-z][a-z0-9+.-]*:.*");
    }

    private static void collect(Pattern pattern, String text, List<String> into) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            into.add(decode(matcher.group(1)));
        }
    }

    private static String decode(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&#x2F;", "/")
                .replace("&amp;", "&");
    }
}
