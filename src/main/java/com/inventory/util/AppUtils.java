package com.inventory.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility helpers shared across the application.
 */
public final class AppUtils {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Separator line for console display */
    public static final String SEP =
        "─────────────────────────────────────────────────────────────────────────";

    private AppUtils() {}   // no instances

    /**
     * Parses a date string of format yyyy-MM-dd.
     * Throws DateTimeParseException on bad input (caller must catch).
     */
    public static LocalDate parseDate(String raw) throws DateTimeParseException {
        return LocalDate.parse(raw.trim(), DATE_FMT);
    }

    /**
     * Parses an integer safely.
     * Throws NumberFormatException on bad input (caller must catch).
     */
    public static int parseInt(String raw) throws NumberFormatException {
        return Integer.parseInt(raw.trim());
    }

    /**
     * Parses a double safely.
     * Throws NumberFormatException on bad input (caller must catch).
     */
    public static double parseDouble(String raw) throws NumberFormatException {
        return Double.parseDouble(raw.trim());
    }

    /** Prints a blank line then the separator */
    public static void printSep() {
        System.out.println(SEP);
    }

    /** Prints a formatted section header */
    public static void printHeader(String title) {
        System.out.println("\n" + SEP);
        System.out.printf("  %s%n", title.toUpperCase());
        System.out.println(SEP);
    }

    /** Prints an error message in a consistent format */
    public static void printError(String msg) {
        System.out.println("  [ERROR] " + msg);
    }

    /** Prints a success message in a consistent format */
    public static void printSuccess(String msg) {
        System.out.println("  [OK]    " + msg);
    }
}
