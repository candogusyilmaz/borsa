package dev.canverse.stocks.service.stock.model;

public record BistImportStockCsvRecord(
        String islemKodu,
        String bultenAdi,
        String enstrumanGrubu,
        String enstrumanTipi
) {
    public static final String INSTRUMENT_GROUP_EQUITY = "EQT";

    public boolean isEquity() {
        return INSTRUMENT_GROUP_EQUITY.equals(enstrumanGrubu);
    }

    public String getIslemKodu() {
        if (islemKodu == null) {
            return null;
        }
        int index = islemKodu.indexOf(".");

        if (index != -1) {
            return islemKodu.substring(0, index);
        }

        return islemKodu;
    }
}