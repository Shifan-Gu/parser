package tidebound.scheduling;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Timer;
import tidebound.RegisterTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ServiceRegistryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistryScheduler.class);
    private final Timer timer = new Timer("service-registry", true);

    @PostConstruct
    public void scheduleRegistration() {
        if (System.getenv().containsKey("SERVICE_REGISTRY_HOST")) {
            log.info("Scheduling service registry updates");
            timer.scheduleAtFixedRate(new RegisterTask(), 0, 5000);
        } else {
            log.info("SERVICE_REGISTRY_HOST not set. Skipping registry scheduling.");
        }
    }

    @PreDestroy
    public void stop() {
        timer.cancel();
    }
}

