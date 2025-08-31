package com.github.lucasskywalker64.persistence;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PersistenceUtil {

    public static <T> List<T> readCsv(
            Path filePath,
            Function<CSVRecord, T> mapper,
            String... headers
    ) throws IOException {
        if (!Files.exists(filePath)) {
            Logger.error("File not found: " + filePath);
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            CSVFormat csvFormat = CSVFormat.Builder.create()
                    .setHeader(headers)
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setDelimiter(";")
                    .get();

            List<T> result = new ArrayList<>();
            for (CSVRecord record : csvFormat.parse(reader)) {
                result.add(mapper.apply(record));
            }
            return result;
        }
    }

    public static <T> void writeCsv(
            Path filePath,
            List<T> records,
            Function<T, String[]> formatter,
            boolean append,
            String... headers) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            CSVFormat format;
            if (!append || reader.readLine() == null) {
                format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                        .setDelimiter(";")
                        .setHeader(headers)
                        .get();
            } else format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";").get();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), append))) {
                CSVPrinter csvPrinter = new CSVPrinter(writer, format);
                for (T record : records) {
                    csvPrinter.printRecord((Object[]) formatter.apply(record));
                }
            }
        }
    }

    public static String readFileAsString(Path filePath) throws IOException {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            Logger.error("Error reading file: " + filePath, e);
            throw e;
        }
    }

    public static void writeStringAsFile(Path filePath, String content) throws IOException {
        try {
            Files.writeString(filePath, content, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            Logger.error("Error writing file: " + filePath, e);
            throw e;
        }
    }
}
