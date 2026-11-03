package com.holysweet.sweetbusiness.export;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class CsvWriter {

    private CsvWriter() {}

    public static void write(Writer writer, ExportData data) throws IOException {
        writer.write('\uFEFF');
        writeRow(writer, data.columns());
        for (List<?> row : data.rows()) writeRow(writer, row);
    }

    private static void writeRow(Writer writer, List<?> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) writer.write(',');
            String value = cells.get(i).toString();
            boolean quoted = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
            if (quoted) writer.write('"');
            writer.write(quoted ? value.replace("\"", "\"\"") : value);
            if (quoted) writer.write('"');
        }
        writer.write("\r\n");
    }
}
