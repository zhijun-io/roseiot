package io.roseiot.iam.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class ResourceController {

    private final ResourceService resourceService;

    ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/resources")
    Page<Resource> list(@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return resourceService.list(pageable);
    }
}
