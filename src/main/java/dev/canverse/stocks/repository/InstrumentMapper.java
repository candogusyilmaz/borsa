package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InstrumentMapper {
    List<InstrumentInfo> fetchInstruments();
}
