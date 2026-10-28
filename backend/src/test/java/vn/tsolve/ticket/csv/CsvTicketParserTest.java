package vn.tsolve.ticket.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.exception.CsvFileRejectedException;

class CsvTicketParserTest {

    private static final String HEADER = "ticket_id,title,symptoms,root_cause,resolution_steps,scope";

    private final CsvTicketParser parser = new CsvTicketParser();

    private ParsedCsv parse(String text) {
        return parser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static CsvFileRejectedException rejected(Runnable call) {
        try {
            call.run();
        } catch (CsvFileRejectedException e) {
            return e;
        }
        throw new AssertionError("expected CsvFileRejectedException");
    }

    @Test
    void readsRowsByHeaderName() {
        ParsedCsv csv = parse(HEADER + ",workaround\nT-1,Login fails,Error 500,Bad token,Restart,All users,None\n");

        assertThat(csv.rows()).hasSize(1);
        ParsedCsv.Row row = csv.rows().get(0);
        assertThat(row.rowNo()).isEqualTo(1);
        ImportPreviewRow.Values v = row.values();
        assertThat(v.ticketId()).isEqualTo("T-1");
        assertThat(v.title()).isEqualTo("Login fails");
        assertThat(v.resolutionSteps()).isEqualTo("Restart");
        assertThat(v.workaround()).isEqualTo("None");
        assertThat(v.description()).isNull();
    }

    @Test
    void rejectsNonUtf8File_AC_001_3() {
        byte[] windows1258 = (HEADER + "\nT-1,Lỗi đăng nhập,a,b,c,d\n").getBytes(Charset.forName("windows-1258"));

        CsvFileRejectedException e = rejected(() -> parser.parse(new ByteArrayInputStream(windows1258)));

        assertThat(e.getCode()).isEqualTo("MSG27");
    }

    @Test
    void rejectsUnclosedQuote_AC_001_3() {
        assertThat(rejected(() -> parse(HEADER + "\nT-1,\"never closed,a,b,c,d\n"))
                        .getCode())
                .isEqualTo("MSG27");
    }

    @Test
    void rejectsEmptyFile_AC_001_3() {
        assertThat(rejected(() -> parse("")).getCode()).isEqualTo("MSG27");
    }

    @Test
    void rejectsDuplicateHeaderName_AC_001_3() {
        assertThat(rejected(() -> parse(HEADER + ",Title\nT-1,a,b,c,d,e,f\n")).getCode())
                .isEqualTo("MSG27");
    }

    @Test
    void listsEveryMissingRequiredColumn_AC_001_5() {
        CsvFileRejectedException e = rejected(() -> parse("ticket_id,title,symptoms\nT-1,a,b\n"));

        assertThat(e.getCode()).isEqualTo("MSG29");
        assertThat(e.getParams()).isEqualTo(Map.of("column_names", "root_cause, resolution_steps, scope"));
    }

    @Test
    void semicolonFileFailsTheHeaderCheck_AC_001_5() {
        String semicolons = HEADER.replace(',', ';') + "\nT-1;a;b;c;d;e\n";

        assertThat(rejected(() -> parse(semicolons)).getCode()).isEqualTo("MSG29");
    }

    @Test
    void rejectsHeaderWithoutDataRows_AC_001_6() {
        assertThat(rejected(() -> parse(HEADER + "\n")).getCode()).isEqualTo("MSG04");
    }

    @Test
    void ignoresByteOrderMark_AC_001_7() {
        ParsedCsv csv = parse("﻿" + HEADER + "\nT-1,a,b,c,d,e\n");

        assertThat(csv.rows().get(0).values().ticketId()).isEqualTo("T-1");
    }

    @Test
    void ignoresColumnsNotInTheTemplate_AC_001_8() {
        ParsedCsv csv = parse(HEADER + ",priority\nT-1,a,b,c,d,e,high\n");

        assertThat(csv.rows()).hasSize(1);
        assertThat(csv.rows().get(0).values().scope()).isEqualTo("e");
    }

    @Test
    void matchesHeaderNamesIgnoringCaseAndSpaces() {
        ParsedCsv csv = parse(" Ticket_ID ,TITLE,Symptoms,root_cause,resolution_steps,scope\nT-1,a,b,c,d,e\n");

        assertThat(csv.rows().get(0).values().ticketId()).isEqualTo("T-1");
    }

    @Test
    void readsCommasQuotesAndLineBreaksInsideQuotedValues_AC_001_12() {
        String text = HEADER + "\r\n"
                + "T-1,\"Login, then fail\",\"He said \"\"no\"\"\",\"line one\r\nline two\",\"a\nb\",e\r\n";

        ParsedCsv.Row row = parse(text).rows().get(0);

        assertThat(row.values().title()).isEqualTo("Login, then fail");
        assertThat(row.values().symptoms()).isEqualTo("He said \"no\"");
        assertThat(row.values().rootCause()).isEqualTo("line one\r\nline two");
        assertThat(row.values().resolutionSteps()).isEqualTo("a\nb");
    }

    @Test
    void numbersRowsByRecordSoMultiLineValuesCountOnce() {
        ParsedCsv csv = parse(HEADER + "\nT-1,a,\"x\ny\",c,d,e\nT-2,a,b,c,d,e\n");

        assertThat(csv.rows()).extracting(ParsedCsv.Row::rowNo).containsExactly(1, 2);
    }

    @Test
    void skipsBlankLines() {
        ParsedCsv csv = parse(HEADER + "\nT-1,a,b,c,d,e\n\n\nT-2,a,b,c,d,e\n\n");

        assertThat(csv.rows()).extracting(r -> r.values().ticketId()).containsExactly("T-1", "T-2");
    }

    @Test
    void readsMissingTrailingCellsAsNull() {
        ParsedCsv csv = parse(HEADER + "\nT-1,a,b\n");

        ImportPreviewRow.Values v = csv.rows().get(0).values();
        assertThat(v.symptoms()).isEqualTo("b");
        assertThat(v.rootCause()).isNull();
        assertThat(v.scope()).isNull();
    }
}
