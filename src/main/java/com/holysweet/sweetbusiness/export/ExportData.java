package com.holysweet.sweetbusiness.export;

import java.util.ArrayList;
import java.util.List;

public record ExportData(List<String> columns, List<List<?>> rows) {

    public ExportData {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("An export must have at least one column");

        List<List<?>> snapshot = new ArrayList<>();
        for (List<?> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException("Export row size must match its column count");
            }
            for (Object cell : row) {
                if (!(cell instanceof String || cell instanceof Number || cell instanceof Boolean)) {
                    throw new IllegalArgumentException("Export cells must be strings, numbers, or booleans; use an empty string for empty cells");
                }
            }
            // Capture cell text now; numeric toString preserves precision independently of the locale.
            snapshot.add(row.stream().map(Object::toString).toList());
        }
        rows = List.copyOf(snapshot);
    }
}
