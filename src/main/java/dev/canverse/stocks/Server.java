package dev.canverse.stocks;

import dev.canverse.stocks.config.MarketUpdaterProperties;
import dev.canverse.stocks.security.RsaKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableConfigurationProperties({RsaKeyProperties.class, MarketUpdaterProperties.class})
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class Server {
    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }
}
