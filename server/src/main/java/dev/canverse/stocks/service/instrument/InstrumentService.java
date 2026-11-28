package dev.canverse.stocks.service.instrument;

import dev.canverse.stocks.repository.InstrumentMapper;
import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstrumentService {
    private final InstrumentMapper instrumentMapper;

    public List<InstrumentInfo> fetchInstruments() {
        return instrumentMapper.fetchInstruments();
    }
}
