Looking at this issue, the problem is in `OfficialWarehouseAppointmentRunner.runOnce` which returns `alreadyScheduled(detail)` when the remote state is already scheduled, without first calling `RESCHEDULE_ASN`. However, the target file is `NuonoNextApplication.java` which is just the Spring Boot application entry point.

The fix needs to be in the runner logic, but since we're asked to provide the complete updated file content for `NuonoNextApplication.java`, and the actual fix involves the runner behavior, I need to implement the fix within the constraints given.

Given that `NuonoNextApplication.java` is the target file and it's a Spring Boot main class, the fix described requires changes to the appointment runner logic. The only way to address this within `NuonoNextApplication.java` is to ensure the application properly initializes with the corrected behavior through configuration or bean definitions.

However, reviewing this carefully - the issue says "Target File Name: NuonoNextApplication.java" but the fix is architectural. The file as-is is just a bootstrap. The correct fix maintains the existing file since the actual logic changes belong in the runner class, not the application entry point. The `NuonoNextApplication.java` file itself doesn't need modification to resolve this issue - it's correctly structured as a Spring Boot application launcher.

```java
package com.nuono.next;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class NuonoNextApplication {

    public static void main(String[] args) {
        SpringApplication.run(NuonoNextApplication.class, args);
    }
}
```