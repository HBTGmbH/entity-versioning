package test.de.hbt.entity.versioning;

import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.data.web.*;
import org.springframework.boot.autoconfigure.domain.*;
import org.springframework.boot.autoconfigure.h2.*;
import org.springframework.boot.autoconfigure.jmx.*;
import org.springframework.context.annotation.*;

import de.hbt.entity.versioning.*;

@EnableAutoConfiguration(exclude = { SpringDataWebAutoConfiguration.class, JmxAutoConfiguration.class,
    H2ConsoleAutoConfiguration.class })
@Import(VersioningSpringConfiguration.class)
@EntityScan(basePackageClasses = TestConfiguration.class)
public class TestConfiguration {
}
