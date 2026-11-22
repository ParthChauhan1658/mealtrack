package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.ParseMethod;
import com.parth.mealtrack.domain.ReportSource;
import com.parth.mealtrack.service.ReportService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    private final Clock clock;

    public ReportController(ReportService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> submit(@Valid @RequestBody ReportRequest r) {
        var result = service.submit(r.centreCode(), r.date(), r.mealsServed(), ReportSource.API, null, ParseMethod.NONE);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ReportResponse.from(result.report()));
    }

    @GetMapping
    public List<ReportResponse> history(@RequestParam String centreCode,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(30);
        return service.history(centreCode, start, end).stream().map(ReportResponse::from).toList();
    }
}
