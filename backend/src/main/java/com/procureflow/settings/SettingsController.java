package com.procureflow.settings;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public List<SettingsService.SettingDto> all() {
        // Read access is open to any signed-in user (for example to show the company name); writes are admin-only.
        return service.all();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<SettingsService.SettingDto> update(@RequestBody Map<String, String> values) {
        return service.update(values);
    }
}
