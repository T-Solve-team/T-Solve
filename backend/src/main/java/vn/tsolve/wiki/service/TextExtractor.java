package vn.tsolve.wiki.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.tsolve.common.exception.FieldMessageCodeException;

@Component
public class TextExtractor {
    public String extract(byte[] bytes, String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        boolean pdf = name.endsWith(".pdf") && startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
        boolean docx = name.endsWith(".docx") && startsWith(bytes, new byte[] {'P', 'K', 3, 4});
        if (!pdf && !docx) {
            throw new FieldMessageCodeException(HttpStatus.BAD_REQUEST, "MSG89", Map.of("file_name", fileName), null);
        }
        try {
            if (pdf) {
                // ponytail: no page cap yet; add one if large PDFs time out in the request thread.
                try (var document = Loader.loadPDF(bytes)) {
                    if (document.isEncrypted()) return "";
                    return readable(new PDFTextStripper().getText(document));
                }
            }
            // Keep POI's ZipSecureFile defaults: malformed archives and zip bombs are unreadable.
            try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
                    var extractor = new XWPFWordExtractor(document)) {
                return readable(extractor.getText());
            }
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    // PDFBox emits NUL for glyphs without a Unicode mapping; PostgreSQL text rejects it (BUG-293).
    static String readable(String text) {
        String clean = text.replace("\0", "");
        return clean.isBlank() ? "" : clean;
    }
}
