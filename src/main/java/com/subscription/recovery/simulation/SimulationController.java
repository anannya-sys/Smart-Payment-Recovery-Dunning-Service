package com.subscription.recovery.simulation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kept in the simulation package because it is a dev/analysis tool rather than part of the core API. */
@RestController
@RequestMapping("/api/simulations")
@Tag(name = "Simulation")
public class SimulationController {

    private final SimulationService simulation;

    public SimulationController(SimulationService simulation) {
        this.simulation = simulation;
    }

    @PostMapping
    @Operation(summary = "Simulate N customers x M months of billing; compare smart vs fixed-retry recovery")
    public SimulationReport run(@Valid @RequestBody(required = false) SimulationRequest request) {
        return simulation.run(request == null ? new SimulationRequest(null, null, null, null) : request);
    }
}
