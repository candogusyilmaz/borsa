package dev.canverse.stocks;

import dev.canverse.stocks.domain.entity.User;
import dev.canverse.stocks.repository.UserRepository;
import dev.canverse.stocks.security.RsaKeyProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableConfigurationProperties({RsaKeyProperties.class})
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class StocksApplication {
    public static void main(String[] args) {
        SpringApplication.run(StocksApplication.class, args);
    }

    @Bean
    CommandLineRunner init(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin1").isPresent()) {
                return;
            }

            var user = new User("admin1", passwordEncoder.encode("123"), "admi1n@localhost");
            userRepository.save(user);
        };
    }
}
