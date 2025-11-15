package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.portfolio.model.TransactionInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TransactionMapper {
    List<TransactionInfo> fetchTransactions(Long userId);
}
