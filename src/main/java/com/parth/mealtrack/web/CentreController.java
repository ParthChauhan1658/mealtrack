package com.parth.mealtrack.web;

import com.parth.mealtrack.service.CentreService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/centres")
public class CentreController {
    private final CentreService service;

    public CentreController(CentreService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CentreResponse> list(@RequestParam(required = false) String district,
                                     @RequestParam(required = false) Boolean active,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("code"));
        return service.search(district, active, pageable).map(CentreResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CentreResponse create(@Valid @RequestBody CentreRequest request) {
        return CentreResponse.from(service.create(request));
    }

    @GetMapping("/{id}")
    public CentreResponse get(@PathVariable Long id) {
        return CentreResponse.from(service.get(id));
    }
}
