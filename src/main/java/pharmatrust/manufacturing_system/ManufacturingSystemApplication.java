package pharmatrust.manufacturing_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ManufacturingSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManufacturingSystemApplication.class, args);
	}

}