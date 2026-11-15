package vn.tsolve.wiki.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.common.exception.CodedException;

class TextExtractorTest {
    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsPdfAndDocxIgnoringExtensionCase_AC_004_1() throws IOException {
        assertThat(extractor.extract(fixture("guide.pdf"), "GUIDE.PDF")).contains("Wiki guide with readable text.");
        assertThat(extractor.extract(fixture("guide.docx"), "GUIDE.DOCX")).contains("DOCX guide with readable text.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"encrypted.pdf", "image-only.pdf"})
    void returnsNoTextForEncryptedAndScannedPdf_AC_004_2(String file) throws IOException {
        assertThat(extractor.extract(fixture(file), file)).isEmpty();
    }

    @Test
    void scannedFixtureContainsAnImageAndNoText_AC_004_2_BUG_274() throws IOException {
        try (var document = Loader.loadPDF(fixture("image-only.pdf"))) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
        }
        assertThat(extractor.extract(fixture("image-only.pdf"), "image-only.pdf"))
                .isEmpty();
    }

    @Test
    void treatsParserErrorsAsUnreadable_AC_004_2() {
        assertThat(extractor.extract("%PDF-broken".getBytes(), "broken.pdf")).isEmpty();
        assertThat(extractor.extract(new byte[] {'P', 'K', 3, 4}, "broken.docx"))
                .isEmpty();
    }

    @Test
    void keepsPoiZipBombProtection_AC_004_2() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("readable ".repeat(250_000));
            document.write(bytes);
        }
        assertThatThrownBy(() -> new XWPFDocument(new java.io.ByteArrayInputStream(bytes.toByteArray())))
                .hasStackTraceContaining("Zip bomb detected");
        assertThat(extractor.extract(bytes.toByteArray(), "bomb.docx")).isEmpty();
        assertThat(org.apache.poi.openxml4j.util.ZipSecureFile.getMinInflateRatio())
                .isEqualTo(0.01);
    }

    @Test
    void treatsWhitespaceOnlyTextAsUnreadable_AC_004_2() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("   \t   ");
            document.write(bytes);
        }
        assertThat(extractor.extract(bytes.toByteArray(), "whitespace.docx")).isEmpty();
    }

    @Test
    void stripsNulThatPdfBoxEmitsForUnmappedGlyphs_BUG_292() {
        assertThat(TextExtractor.readable("Wiki\u0000 guide\u0000")).isEqualTo("Wiki guide");
        assertThat(TextExtractor.readable("\u0000 \u0000")).isEmpty();
    }

    @Test
    void rejectsFalseMagicAndDisallowedExtensions_AC_004_4() throws IOException {
        for (String name : new String[] {"fake.pdf", "guide.txt", "guide.docx", "guide"}) {
            byte[] bytes = name.equals("fake.pdf") ? fixture(name) : fixture("guide.pdf");
            assertThatThrownBy(() -> extractor.extract(bytes, name))
                    .isInstanceOfSatisfying(CodedException.class, error -> {
                        assertThat(error.getCode()).isEqualTo("MSG89");
                        assertThat(error.getParams()).containsEntry("file_name", name);
                    });
        }
    }

    private byte[] fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/wiki/" + name)) {
            return input.readAllBytes();
        }
    }
}
