package vn.tsolve.ticket.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.exception.CsvFileRejectedException;

/**
 * Reads an uploaded ticket CSV (UC-21): strict UTF-8, RFC 4180, header in the first record. Only file-level problems
 * are rejected here; row content is checked by {@link CsvRowValidator}.
 */
@Component
public class CsvTicketParser {

    private static final CSVFormat FORMAT =
            CSVFormat.RFC4180.builder().setIgnoreEmptyLines(true).build();

    public ParsedCsv parse(InputStream in) {
        // REPORT instead of the default REPLACE: an ANSI export must fail, not turn into '?' characters
        CharsetDecoder utf8 = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(in, utf8);
                CSVParser csv = FORMAT.parse(reader)) {
            Iterator<CSVRecord> records = csv.iterator();
            if (!records.hasNext()) {
                throw new CsvFileRejectedException("MSG27");
            }
            Map<String, Integer> columns = headerIndex(records.next());
            List<ParsedCsv.Row> rows = new ArrayList<>();
            while (records.hasNext()) {
                CSVRecord record = records.next();
                rows.add(new ParsedCsv.Row((int) record.getRecordNumber() - 1, values(record, columns)));
            }
            if (rows.isEmpty()) {
                throw new CsvFileRejectedException("MSG04");
            }
            return new ParsedCsv(rows);
        } catch (IOException | UncheckedIOException | IllegalStateException e) {
            // bad encoding or broken quoting
            throw new CsvFileRejectedException("MSG27");
        }
    }

    private static Map<String, Integer> headerIndex(CSVRecord header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i);
            if (i == 0 && name.startsWith("﻿")) {
                name = name.substring(1);
            }
            name = name.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && index.putIfAbsent(name, i) != null) {
                throw new CsvFileRejectedException("MSG27");
            }
        }
        List<String> missing =
                CsvColumns.REQUIRED.stream().filter(c -> !index.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new CsvFileRejectedException("MSG29", Map.of("column_names", String.join(", ", missing)));
        }
        return index;
    }

    private static ImportPreviewRow.Values values(CSVRecord record, Map<String, Integer> columns) {
        return new ImportPreviewRow.Values(
                cell(record, columns, CsvColumns.TICKET_ID),
                cell(record, columns, CsvColumns.TITLE),
                cell(record, columns, CsvColumns.DESCRIPTION),
                cell(record, columns, CsvColumns.AUTHOR),
                cell(record, columns, CsvColumns.RESOLVED_DATE),
                cell(record, columns, CsvColumns.SYMPTOMS),
                cell(record, columns, CsvColumns.ROOT_CAUSE),
                cell(record, columns, CsvColumns.RESOLUTION_STEPS),
                cell(record, columns, CsvColumns.WORKAROUND),
                cell(record, columns, CsvColumns.SCOPE));
    }

    /** null when the column is absent or the row is shorter than the header. */
    private static String cell(CSVRecord record, Map<String, Integer> columns, String column) {
        Integer i = columns.get(column);
        return i == null || i >= record.size() ? null : record.get(i);
    }
}
