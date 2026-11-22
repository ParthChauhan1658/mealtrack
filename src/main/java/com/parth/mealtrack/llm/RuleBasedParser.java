package com.parth.mealtrack.llm;

import com.parth.mealtrack.domain.ParseMethod;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback parser for English/Hinglish messages. Works clause by clause: a number counts as the
 * answer only when a "served"-type word is within two words of it, and is ignored near absent/enrolled/total.
 * HIGH confidence needs exactly one distinct such number; anything else is LOW so a human checks it.
 */
@Component
public class RuleBasedParser {
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DMY_DATE = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4}|\\d{2})\\b");
    private static final Pattern DM_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b");
    private static final Pattern TIME = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");
    private static final Pattern GROUPED = Pattern.compile("\\b\\d{1,3}(?:,\\d{2,3})+\\b");
    private static final Pattern CLAUSE_SPLIT = Pattern.compile("[,;.!?\\n|]+");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private static final List<String> POSITIVE_PREFIXES = List.of(
            "serv", "meal", "khana", "khaana", "khaya", "khilaya", "bach", "bacc", "kid", "child", "student",
            "beneficiar", "thali", "plate", "lunch", "fed", "diya", "खाना", "बच्च");
    private static final Set<String> STRONG_NEGATIVE = Set.of(
            "absent", "absents", "enrolled", "enrolment", "enrollment", "registered", "strength", "leave",
            "anupasthit", "gair", "अनुपस्थित");
    private static final Set<String> WEAK_NEGATIVE = Set.of("total", "kul");
    private static final List<String> NO_MEAL_PHRASES = List.of(
            "nahi bana", "nahin bana", "nahi bani", "khana nahi", "no meal", "not served", "did not serve",
            "didn't serve", "didnt serve", "not cooked", "closed", "holiday", "chutti", "band hai");
    private static final int WINDOW = 2;

    public ParsedReport parse(String message, LocalDate today) {
        String text = message.toLowerCase(Locale.ROOT);
        LocalDate date = extractDate(text, today);
        text = ISO_DATE.matcher(text).replaceAll(" ");
        text = DMY_DATE.matcher(text).replaceAll(" ");
        text = DM_DATE.matcher(text).replaceAll(" ");
        text = TIME.matcher(text).replaceAll(" ");
        text = removeGrouping(text);

        Set<Integer> answers = new LinkedHashSet<>();
        List<Integer> neutral = new ArrayList<>();
        for (String clause : CLAUSE_SPLIT.split(text)) {
            classifyNumbers(tokens(clause), answers, neutral);
        }

        boolean saysNoMeals = NO_MEAL_PHRASES.stream().anyMatch(text::contains);
        if (answers.size() == 1 && !saysNoMeals) {
            return new ParsedReport(answers.iterator().next(), date, Confidence.HIGH, ParseMethod.RULES);
        }
        if (!answers.isEmpty()) {
            return new ParsedReport(answers.iterator().next(), date, Confidence.LOW, ParseMethod.RULES);
        }
        if (saysNoMeals) {
            return new ParsedReport(0, date, Confidence.LOW, ParseMethod.RULES);
        }
        Integer guess = neutral.size() == 1 ? neutral.get(0) : null;
        return new ParsedReport(guess, date, Confidence.LOW, ParseMethod.RULES);
    }

    private static void classifyNumbers(List<String> tokens, Set<Integer> answers, List<Integer> neutral) {
        for (int i = 0; i < tokens.size(); i++) {
            Integer value = toInt(tokens.get(i));
            if (value == null) continue;
            List<String> near = new ArrayList<>();
            for (int j = Math.max(0, i - WINDOW); j <= Math.min(tokens.size() - 1, i + WINDOW); j++) {
                if (j != i) near.add(tokens.get(j));
            }
            if (near.stream().anyMatch(STRONG_NEGATIVE::contains)) continue;
            if (near.stream().anyMatch(RuleBasedParser::isPositive)) {
                answers.add(value);
            } else if (near.stream().noneMatch(WEAK_NEGATIVE::contains)) {
                neutral.add(value);
            }
        }
    }

    private static boolean isPositive(String word) {
        return POSITIVE_PREFIXES.stream().anyMatch(word::startsWith);
    }

    private static List<String> tokens(String clause) {
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(clause);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static Integer toInt(String token) {
        if (!token.chars().allMatch(Character::isDigit) || token.length() > 9) return null;
        return Integer.parseInt(token);
    }

    /** "1,250" and Indian "1,25,000" become plain digits; "95 kids,12 absent" is left alone. */
    private static String removeGrouping(String text) {
        Matcher m = GROUPED.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, m.group().replace(",", ""));
        m.appendTail(sb);
        return sb.toString();
    }

    private static LocalDate extractDate(String text, LocalDate today) {
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            LocalDate d = safeDate(iso.group(1), iso.group(2), iso.group(3));
            if (d != null) return d;
        }
        Matcher dmy = DMY_DATE.matcher(text);
        if (dmy.find()) {
            String year = dmy.group(3).length() == 2 ? "20" + dmy.group(3) : dmy.group(3);
            LocalDate d = safeDate(year, dmy.group(2), dmy.group(1));
            if (d != null) return d;
        }
        Matcher dm = DM_DATE.matcher(text);
        if (dm.find()) {
            LocalDate d = safeDate(String.valueOf(today.getYear()), dm.group(2), dm.group(1));
            if (d != null) return d;
        }
        // "kal" is ambiguous in Hindi (yesterday or tomorrow); reports describe past meals, so read it as yesterday.
        for (String t : tokens(text)) {
            if (t.equals("yesterday") || t.equals("kal")) return today.minusDays(1);
        }
        return today;
    }

    private static LocalDate safeDate(String y, String m, String d) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }
}
