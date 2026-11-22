package com.parth.mealtrack.web;

import com.parth.mealtrack.service.ParseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/parse")
public class ParseController {
    private final ParseService service;

    public ParseController(ParseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ParseResponse> parse(@Valid @RequestBody ParseRequest r) {
        var outcome = service.parseAndSave(r.centreCode(), r.message());
        var p = outcome.parsed();
        var saved = outcome.saved();
        var body = new ParseResponse(r.centreCode(), p.date(), p.mealsServed(), p.method(), p.confidence(),
                saved.created(), ReportResponse.from(saved.report()));
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }
}
