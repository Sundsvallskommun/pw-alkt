package se.sundsvall.alkt;

import org.springframework.cloud.openfeign.EnableFeignClients;
import se.sundsvall.dept44.ServiceApplication;

import static org.springframework.boot.SpringApplication.run;

@ServiceApplication
@EnableFeignClients
public class Application {
	public static void main(String... args) {
		run(Application.class, args);
	}
}
