package com.spendwise.product.service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ShoppingIntentService {

    private static final Pattern PRICE_LIMIT = Pattern.compile(
            "(?i)(?:under|below|less than|up to|max(?:imum)?(?:\\s+price)?|budget(?:\\s+of)?)\\s*(\\$|₹|€|£)?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)");

    public ParsedIntent parse(String message) {
        String normalized = message.strip().replaceAll("\\s+", " ");
        Matcher matcher = PRICE_LIMIT.matcher(normalized);
        boolean hasPrice = matcher.find();
        BigDecimal requestedMaxPrice = hasPrice
                ? new BigDecimal(matcher.group(2).replace(",", ""))
                : null;
        String requestedCurrency = hasPrice ? currency(matcher.group(1)) : null;
        String keywords = requestedMaxPrice == null
                ? normalized
                : normalized.substring(0, matcher.start()).strip()
                        + " " + normalized.substring(matcher.end()).strip();
        return new ParsedIntent(normalized, keywords.strip(), requestedMaxPrice, requestedCurrency);
    }

    private String currency(String symbol) {
        if (symbol == null) {
            return null;
        }
        return switch (symbol) {
            case "$" -> "USD";
            case "₹" -> "INR";
            case "€" -> "EUR";
            case "£" -> "GBP";
            default -> null;
        };
    }

    public record ParsedIntent(
            String normalizedQuery, String keywords, BigDecimal requestedMaxPrice, String requestedCurrency) {
    }
}
