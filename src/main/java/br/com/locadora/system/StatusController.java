package br.com.locadora.system;

import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

    private final Clock clock;

    public StatusController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    public StatusResponse status() {
        return new StatusResponse("locadora-livros", "UP", clock.getZone().getId());
    }

    public record StatusResponse(String application, String status, String timeZone) {}
}
