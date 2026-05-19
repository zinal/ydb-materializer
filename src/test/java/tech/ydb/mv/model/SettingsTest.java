package tech.ydb.mv.model;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.ydb.mv.MvConfig;

/**
 *
 * @author zinal
 */
public class SettingsTest {

    @Test
    public void checkDictionarySettings() {
        var src = new MvDictionarySettings();

        Assertions.assertEquals(10000, src.getRowsPerSecondLimit());
        Assertions.assertEquals(500, src.getUpsertBatchSize());
        Assertions.assertEquals(4, src.getCdcReaderThreads());
        Assertions.assertEquals(100000, src.getMaxChangeRowsScanned());

        src.setRowsPerSecondLimit(600);
        src.setCdcReaderThreads(51);
        src.setUpsertBatchSize(123);
        src.setMaxChangeRowsScanned(999);

        String temp = MvConfig.GSON.toJson(src);

        var dst = MvConfig.GSON.fromJson(temp, MvDictionarySettings.class);

        Assertions.assertEquals(src, dst);
        Assertions.assertEquals(600, dst.getRowsPerSecondLimit());
        Assertions.assertEquals(51, dst.getCdcReaderThreads());
        Assertions.assertEquals(123, dst.getUpsertBatchSize());
        Assertions.assertEquals(999, dst.getMaxChangeRowsScanned());

        var copy = new MvDictionarySettings(src);
        Assertions.assertEquals(src, copy);
    }

    @Test
    public void checkHandlerSettings() {
        var src = new MvHandlerSettings();

        Assertions.assertEquals(4, src.getCdcReaderThreads());
        Assertions.assertEquals(4, src.getApplyThreads());
        Assertions.assertEquals(10000, src.getApplyQueueSize());
        Assertions.assertEquals(1000, src.getSelectBatchSize());
        Assertions.assertEquals(500, src.getUpsertBatchSize());
        Assertions.assertEquals(28800, src.getDictionaryScanSeconds());
        Assertions.assertEquals(30, src.getQueryTimeoutSeconds());

        src.setApplyQueueSize(123);
        src.setApplyThreads(456);
        src.setCdcReaderThreads(789);
        src.setDictionaryScanSeconds(512);
        src.setSelectBatchSize(789);
        src.setUpsertBatchSize(333);
        src.setQueryTimeoutSeconds(44);

        String temp = MvConfig.GSON.toJson(src);

        var dst = MvConfig.GSON.fromJson(temp, MvHandlerSettings.class);

        Assertions.assertEquals(src, dst);
        Assertions.assertEquals(44, dst.getQueryTimeoutSeconds());

        var copy = new MvHandlerSettings(src);
        Assertions.assertEquals(src, copy);
    }

    @Test
    public void checkScanSettings() {
        var src = new MvScanSettings();

        Assertions.assertEquals(10000, src.getRowsPerSecondLimit());

        src.setRowsPerSecondLimit(500);

        String temp = MvConfig.GSON.toJson(src);

        var dst = MvConfig.GSON.fromJson(temp, MvScanSettings.class);

        Assertions.assertEquals(src, dst);
    }

    @Test
    public void checkSettingsFromProperties() {
        Properties props = new Properties();
        props.setProperty(MvConfig.CONF_SCAN_RATE, "101");
        props.setProperty(MvConfig.CONF_CDC_THREADS, "102");
        props.setProperty(MvConfig.CONF_BATCH_UPSERT, "103");
        props.setProperty(MvConfig.CONF_MAX_ROW_CHANGES, "104");
        props.setProperty(MvConfig.CONF_APPLY_THREADS, "105");
        props.setProperty(MvConfig.CONF_APPLY_QUEUE, "106");
        props.setProperty(MvConfig.CONF_BATCH_SELECT, "107");
        props.setProperty(MvConfig.CONF_DICT_SCAN_SECONDS, "108");
        props.setProperty(MvConfig.CONF_QUERY_TIMEOUT, "109");

        var dict = new MvDictionarySettings(props);
        Assertions.assertEquals(101, dict.getRowsPerSecondLimit());
        Assertions.assertEquals(102, dict.getCdcReaderThreads());
        Assertions.assertEquals(103, dict.getUpsertBatchSize());
        Assertions.assertEquals(104, dict.getMaxChangeRowsScanned());

        var handler = new MvHandlerSettings(props);
        Assertions.assertEquals(102, handler.getCdcReaderThreads());
        Assertions.assertEquals(105, handler.getApplyThreads());
        Assertions.assertEquals(106, handler.getApplyQueueSize());
        Assertions.assertEquals(107, handler.getSelectBatchSize());
        Assertions.assertEquals(103, handler.getUpsertBatchSize());
        Assertions.assertEquals(108, handler.getDictionaryScanSeconds());
        Assertions.assertEquals(109, handler.getQueryTimeoutSeconds());

        var scan = new MvScanSettings(props);
        Assertions.assertEquals(101, scan.getRowsPerSecondLimit());
    }

}
