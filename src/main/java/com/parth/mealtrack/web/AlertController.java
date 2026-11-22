package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.AlertStatus;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.service.AlertService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertResponse> list(@RequestParam(required = false) AlertStatus status,
                                    @RequestParam(required = false) AlertType type,
                                    @RequestParam(required = false) String district,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.search(status, type, district, date).stream().map(AlertResponse::from).toList();
    }

    @PatchMapping("/{id}/resolve")
    public AlertResponse resolve(@PathVariable Long id) {
        return AlertResponse.from(service.resolve(id));
    }
}
