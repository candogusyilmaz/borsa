package dev.canverse.stocks.platform.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
class ErrorHandlingTestController {

    @GetMapping("/application")
    void applicationError() {
        throw new AppException(
                CommonErrorCode.ENTITY_NOT_FOUND,
                Map.of("entity", "Example", "id", "00000000-0000-0000-0000-000000000001"));
    }

    @GetMapping("/internal")
    void internalApplicationError() {
        throw new AppException(CommonErrorCode.INTERNAL_ERROR, Map.of("detail", "database password=secret"));
    }

    @GetMapping("/unexpected")
    void unexpectedError() {
        throw new IllegalStateException("internal secret exception message");
    }

    @PostMapping(value = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
    void validation(@Valid @RequestBody ValidationRequest request) {}

    @GetMapping("/missing")
    void missing(@RequestParam("parameter") String parameter) {}

    @PostMapping(value = "/media", consumes = MediaType.APPLICATION_JSON_VALUE)
    void supportedMedia(@RequestBody String body) {}

    @GetMapping("/conflict")
    void persistenceConflict() {
        throw new DataIntegrityViolationException("constraint=secret_database_constraint");
    }

    @GetMapping("/parameter-validation")
    void parameterValidation(@RequestParam("value") @Min(3) int value) {}

    record ValidationRequest(@NotBlank String email) {}
}
