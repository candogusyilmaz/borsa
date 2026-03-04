package dev.canverse.stocks.rest.instrument;

import dev.canverse.stocks.domain.entity.instrument.CustomInstrument;
import dev.canverse.stocks.service.instrument.InstrumentService;
import dev.canverse.stocks.service.instrument.model.CreateCustomInstrumentRequest;
import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import dev.canverse.stocks.service.instrument.model.UpdateCustomInstrumentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instruments")
public class InstrumentController {
    private final InstrumentService instrumentService;

    @GetMapping
    public List<InstrumentInfo> fetchInstruments() {
        return instrumentService.fetchInstruments();
    }

    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentInfo createCustomInstrument(@Valid @RequestBody CreateCustomInstrumentRequest req) {
        var instrument = instrumentService.createCustomInstrument(req);
        return toInstrumentInfo(instrument);
    }

    @PutMapping("/custom/{instrumentId}")
    public InstrumentInfo updateCustomInstrument(@PathVariable long instrumentId,
                                                 @Valid @RequestBody UpdateCustomInstrumentRequest req) {
        var instrument = instrumentService.updateCustomInstrument(instrumentId, req);
        return toInstrumentInfo(instrument);
    }

    @DeleteMapping("/custom/{instrumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomInstrument(@PathVariable long instrumentId) {
        instrumentService.deleteCustomInstrument(instrumentId);
    }

    private static InstrumentInfo toInstrumentInfo(CustomInstrument instrument) {
        var info = new InstrumentInfo();
        info.setId(String.valueOf(instrument.getId()));
        info.setSymbol(instrument.getSymbol());
        info.setName(instrument.getName());
        info.setSupportedCurrencies(new String[]{instrument.getCurrencyCode()});
        return info;
    }
}
