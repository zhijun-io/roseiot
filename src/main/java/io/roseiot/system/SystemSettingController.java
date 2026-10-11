package io.roseiot.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/system-settings")
@PreAuthorize("hasAuthority('system.setting.manage')")
class SystemSettingController {

    private final SystemSettingService systemSettingService;

    SystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    List<SystemSetting> list() {
        return systemSettingService.list();
    }

    @GetMapping("/{id}")
    SystemSetting get(@PathVariable Long id) {
        return systemSettingService.get(id);
    }

    @PostMapping
    SystemSetting create(@Valid @RequestBody CreateSystemSettingRequest request) {
        return systemSettingService.create(request.type(), request.setting());
    }

    @PutMapping("/{id}")
    SystemSetting update(@PathVariable Long id, @RequestBody JsonNode setting) {
        return systemSettingService.update(id, setting);
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable Long id) {
        systemSettingService.delete(id);
    }

    record CreateSystemSettingRequest(@NotNull SystemSettingType type, @NotNull JsonNode setting) {}
}
