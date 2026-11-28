package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.instrument.model.InstrumentInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InstrumentMapper {
    List<InstrumentInfo> fetchInstruments();
}
