package dev.canverse.stocks.rest.instrument;

import dev.canverse.stocks.service.instrument.InstrumentService;
import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
