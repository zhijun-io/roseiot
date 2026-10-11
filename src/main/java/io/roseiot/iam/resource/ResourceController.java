package io.roseiot.iam.resource;

import io.roseiot.shared.webmvc.CursorPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
class ResourceController {

    private final ResourceService resourceService;

    ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/resources")
    CursorPage<Resource> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return resourceService.list(cursor, limit);
    }
}
