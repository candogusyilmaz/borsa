package dev.canverse.stocks.service.stock;

import dev.canverse.stocks.domain.entity.instrument.StockInstrument;
import dev.canverse.stocks.service.stock.model.BistImportStockCsvRecord;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BistImportStocksJob {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final BistImportStocksDataDownloader stockDataDownloader;

    @Bean
    public Job importStocksJob(Step downloadStep, Step processStep) {
        return new JobBuilder("importStocksJob", jobRepository)
                .start(downloadStep)
                .next(processStep)
                .build();
    }

    @Bean
    public Step downloadStep() {
        return new StepBuilder("downloadStep", jobRepository)
                .tasklet(stockDataDownloader, transactionManager)
                .build();
    }

    @Bean
    public Step processStep(FlatFileItemReader<BistImportStockCsvRecord> reader, BistImportStockProcessor processor,
                            JpaItemWriter<StockInstrument> writer) {
        return new StepBuilder("processStep", jobRepository)
                .<BistImportStockCsvRecord, StockInstrument>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public FlatFileItemReader<BistImportStockCsvRecord> reader() {
        return new FlatFileItemReaderBuilder<BistImportStockCsvRecord>()
                .linesToSkip(2)
                .resource(new FileSystemResource("downloaded/stocks.csv"))
                .name("bistStockItemReader")
                .lineMapper(new DefaultLineMapper<>() {{
                    setLineTokenizer(new DelimitedLineTokenizer() {{
                        setDelimiter(";");
                    }});
                    setFieldSetMapper(s -> new BistImportStockCsvRecord(
                            s.readString(1),
                            s.readString(2),
                            s.readString(6),
                            s.readString(7),
                            s.readBigDecimal(21),
                            s.readBigDecimal(16),
                            s.readBigDecimal(23)
                    ));
                }})
                .build();
    }

    @Bean
    public JpaItemWriter<StockInstrument> writer() {
        var writer = new JpaItemWriter<StockInstrument>();
        writer.setEntityManagerFactory(entityManagerFactory);

        return writer;
    }
}
