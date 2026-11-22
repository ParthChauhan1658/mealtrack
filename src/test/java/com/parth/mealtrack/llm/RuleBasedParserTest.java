package com.parth.mealtrack.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.parth.mealtrack.domain.ParseMethod;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleBasedParserTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    final RuleBasedParser parser = new RuleBasedParser();

    /** message | expected meals (empty = none found) | expected confidence | expected date */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            // Hinglish
            "aaj 180 bachchon ko khana diya                         | 180   | HIGH | 2026-11-22",
            "aaj 180 bachchon ko khana diya, 20 absent the          | 180   | HIGH | 2026-11-22",
            "Kal 120 bachho ne khana khaya                          | 120   | HIGH | 2026-11-21",
            "khana nahi bana aaj, gas khatam                        | 0     | LOW  | 2026-11-22",
            // English, with distractors
            "Meals served today: 1,250                              | 1250  | HIGH | 2026-11-22",
            "served 95 kids, 12 absent, total enrolled 107          | 95    | HIGH | 2026-11-22",
            "Served 95 kids,12 absent                               | 95    | HIGH | 2026-11-22",
            "served 95 kids out of 107 enrolled                     | 95    | HIGH | 2026-11-22",
            "Mid day meal: 210 students present, 15 absent          | 210   | HIGH | 2026-11-22",
            "Total 150 meals served                                 | 150   | HIGH | 2026-11-22",
            "SERVED 45 KIDS                                         | 45    | HIGH | 2026-11-22",
            "Students fed: 1250. Absent: 80                         | 1250  | HIGH | 2026-11-22",
            "Meals served: 0                                        | 0     | HIGH | 2026-11-22",
            // number formats
            "Today we served 1,25,000 meals                         | 125000| HIGH | 2026-11-22",
            "Served 60 kids at 12:30                                | 60    | HIGH | 2026-11-22",
            // dates
            "22/11/2026 served 88                                   | 88    | HIGH | 2026-11-22",
            "2026-11-20: 140 meals                                  | 140   | HIGH | 2026-11-20",
            "served 70 meals on 19/11                               | 70    | HIGH | 2026-11-19",
            "served 70 meals yesterday                              | 70    | HIGH | 2026-11-21",
            // not confident: human must check
            "no meals served today                                  |       | LOW  | 2026-11-22",
            "hello                                                  |       | LOW  | 2026-11-22",
            "served 90 and 95 meals                                 | 90    | LOW  | 2026-11-22",
            "150                                                    | 150   | LOW  | 2026-11-22",
            "total enrolled 107, absent 12                          |       | LOW  | 2026-11-22",
    })
    void parsesMessages(String message, Integer meals, Confidence confidence, LocalDate date) {
        ParsedReport r = parser.parse(message, TODAY);
        if ("no meals served today".equals(message.trim())) {
            assertEquals(0, r.mealsServed()); // explicit "no meals" is zero, but never trusted automatically
        } else if (meals == null) {
            assertNull(r.mealsServed());
        } else {
            assertEquals(meals, r.mealsServed());
        }
        assertEquals(confidence, r.confidence());
        assertEquals(date, r.date());
        assertEquals(ParseMethod.RULES, r.method());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"served 5 meals on 31/02/2026 | 2026-11-22", "served 5 meals 2026-13-40 | 2026-11-22"})
    void impossibleDatesFallBackToToday(String message, LocalDate expected) {
        assertEquals(expected, parser.parse(message, TODAY).date());
    }
}
