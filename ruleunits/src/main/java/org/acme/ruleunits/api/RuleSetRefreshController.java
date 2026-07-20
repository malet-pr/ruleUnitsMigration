package org.acme.ruleunits.api;

import org.acme.ruleunits.refresh.RuleSetRefreshResult;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative HTTP boundary for an explicit full rule-set refresh. It delegates to the
 * serialized runtime service and exposes only sanitized publication or failure information;
 * authorization remains a deployment concern.
 */
@RestController
@RequestMapping("/admin/rules")
public class RuleSetRefreshController {
    private final RuleSetRuntimeService runtime;

    public RuleSetRefreshController(RuleSetRuntimeService runtime) {
        this.runtime = runtime;
    }

    @PostMapping("/refresh")
    ResponseEntity<RuleSetRefreshResponse> refresh() {
        RuleSetRefreshResult result = runtime.refresh();
        HttpStatus status = result.published() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(RuleSetRefreshResponse.from(result));
    }
}
