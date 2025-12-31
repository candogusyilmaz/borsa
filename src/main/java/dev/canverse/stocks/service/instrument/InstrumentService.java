package dev.canverse.stocks.service.instrument;

import dev.canverse.stocks.repository.InstrumentMapper;
import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InstrumentService {
    private final InstrumentMapper instrumentMapper;

    public List<InstrumentInfo> fetchInstruments() {
        return instrumentMapper.fetchInstruments();
    }
}
