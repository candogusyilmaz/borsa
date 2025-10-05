package dev.canverse.stocks.service.stock.model;

import java.math.BigDecimal;

public record BistImportStockCsvRecord(
        String islemKodu,
        String bultenAdi,
        String enstrumanGrubu,
        String enstrumanTipi,
        BigDecimal kapanisFiyati,
        BigDecimal oncekiKapanisFiyati,
        BigDecimal degisim
) {
    public static final String INSTRUMENT_GROUP_EQT = "EQT";
    public static final String INSTRUMENT_GROUP_EMS = "EMS";

    public boolean isEquity() {
        return INSTRUMENT_GROUP_EQT.equals(enstrumanGrubu);
    }

    public boolean isETF() {
        return INSTRUMENT_GROUP_EMS.equals(enstrumanGrubu);
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