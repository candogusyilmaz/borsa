package dev.canverse.stocks.service.instrument;

import dev.canverse.stocks.domain.entity.instrument.CustomInstrument;
import dev.canverse.stocks.domain.exception.BadRequestException;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.CustomInstrumentRepository;
import dev.canverse.stocks.repository.InstrumentMapper;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.instrument.model.CreateCustomInstrumentRequest;
import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import dev.canverse.stocks.service.instrument.model.UpdateCustomInstrumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InstrumentService {
    private final InstrumentMapper instrumentMapper;
    private final CustomInstrumentRepository customInstrumentRepository;

    public List<InstrumentInfo> fetchInstruments() {
        var userId = AuthenticationProvider.getUser().getId();
        return instrumentMapper.fetchInstruments(userId);
    }

    @Transactional
    public CustomInstrument createCustomInstrument(CreateCustomInstrumentRequest req) {
        var user = AuthenticationProvider.getUser();

        if (customInstrumentRepository.existsBySymbolAndUserId(req.symbol(), user.getId())) {
            throw new BadRequestException(
                    String.format("You already have an instrument with symbol '%s'.", req.symbol())
            );
        }

        var instrument = new CustomInstrument(req.name(), req.symbol(), req.type(), req.currencyCode(), user);
        return customInstrumentRepository.save(instrument);
    }

    @Transactional
    public CustomInstrument updateCustomInstrument(long instrumentId, UpdateCustomInstrumentRequest req) {
        var user = AuthenticationProvider.getUser();

        var instrument = customInstrumentRepository.findByIdAndUserId(instrumentId, user.getId())
                .orElseThrow(() -> new NotFoundException("Custom instrument not found."));

        if (!instrument.getSymbol().equals(req.symbol())
                && customInstrumentRepository.existsBySymbolAndUserId(req.symbol(), user.getId())) {
            throw new BadRequestException(
                    String.format("You already have an instrument with symbol '%s'.", req.symbol())
            );
        }

        instrument.setName(req.name());
        instrument.setSymbol(req.symbol());
        instrument.setCurrencyCode(req.currencyCode());

        return customInstrumentRepository.save(instrument);
    }

    @Transactional
    public void deleteCustomInstrument(long instrumentId) {
        var user = AuthenticationProvider.getUser();

        var instrument = customInstrumentRepository.findByIdAndUserId(instrumentId, user.getId())
                .orElseThrow(() -> new NotFoundException("Custom instrument not found."));

        instrument.setActive(false);
        customInstrumentRepository.save(instrument);
    }
}
