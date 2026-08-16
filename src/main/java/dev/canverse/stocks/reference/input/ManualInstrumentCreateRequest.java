package dev.canverse.stocks.reference.input;

import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.reference.domain.InstrumentSymbol;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ManualInstrumentConstraints;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record ManualInstrumentCreateRequest(
        @NotNull UUID marketId,

        @NotBlank String symbol,

        @NotBlank String name,

        @NotNull InstrumentType instrumentType,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String quotationCurrency,
        @NotNull ValuationMethod valuationMethod,

        @Size(max = ManualInstrumentConstraints.MAX_ALIASES_PER_INSTRUMENT)
        List<@NotNull @Valid InstrumentAliasInput> aliases) {

    public ManualInstrumentCreateRequest {
        aliases = aliases == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(aliases));
    }

    public void validate() {
        validateSymbol();
        validateName();
        validateAliases();
    }

    private void validateSymbol() {
        try {
            InstrumentSymbol.of(symbol);
        } catch (IllegalArgumentException exception) {
            throw invalidSymbol();
        }
    }

    private static AppException invalidSymbol() {
        return ValidationErrors.invalidField(
                "symbol",
                "error.fields.reference.invalid_value",
                "The symbol must contain 1 to " + ManualInstrumentConstraints.MAX_SYMBOL_LENGTH + " valid characters.");
    }

    private void validateName() {
        if (!ManualInstrumentConstraints.fitsDisplayAndNormalizedBounds(
                name, ManualInstrumentConstraints.MAX_NAME_LENGTH)) {
            throw ValidationErrors.invalidField(
                    "name",
                    "error.fields.reference.invalid_value",
                    "The name must contain at most "
                            + ManualInstrumentConstraints.MAX_NAME_LENGTH
                            + " characters in both its trimmed display and normalized forms.");
        }
    }

    private void validateAliases() {
        var seen = new HashSet<String>();
        for (var alias : aliases) {
            var value = alias.value().trim();
            if (!ManualInstrumentConstraints.fitsDisplayAndNormalizedBounds(
                    value, ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH)) {
                throw ValidationErrors.invalidField(
                        "aliases",
                        "error.fields.reference.invalid_value",
                        "Each alias must contain at most "
                                + ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH
                                + " characters in both its trimmed display and normalized forms.");
            }
            var normalized = value.toUpperCase(Locale.ROOT);
            if (!seen.add(alias.type().name() + "\u0000" + normalized)) {
                throw new AppException(ReferenceErrorCode.DUPLICATE_INSTRUMENT_ALIAS);
            }
        }
    }
}
