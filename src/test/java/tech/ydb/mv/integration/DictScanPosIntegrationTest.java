package tech.ydb.mv.integration;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.ydb.mv.AbstractIntegrationBase;
import tech.ydb.mv.MvConfig;
import tech.ydb.mv.YdbConnector;
import tech.ydb.mv.data.YdbStruct;
import tech.ydb.mv.model.MvViewExpr;
import tech.ydb.mv.parser.MvSqlGen;
import tech.ydb.mv.svc.MvService;

/**
 * Verifies that dictionary refresh commits scan positions to the control table
 * and that subsequent dictionary scans resume after the committed position.
 */
public class DictScanPosIntegrationTest extends AbstractIntegrationBase {

    private static final String HANDLER = "handler1";
    private static final String DICT_TABLE = "test1/sub_table4";
    private static final String DICT_HIST_TABLE = "test1/dict_hist";

    private static final String WRITE_DICT_ROUND1
            = """
UPSERT INTO `test1/sub_table4` (c15,c16) VALUES
 (102, 'sub_table4 Zwei Updated 1'u)
,(104, 'sub_table4 Vier Updated 1'u);
""";

    private static final String WRITE_DICT_ROUND2
            = """
UPSERT INTO `test1/sub_table4` (c15,c16) VALUES
 (101, 'sub_table4 Eins Updated 2'u)
,(103, 'sub_table4 Drei Updated 2'u);
""";

    @BeforeEach
    public void init() {
        prepareDb();
    }

    @AfterEach
    public void cleanup() {
        clearDb();
    }

    @Test
    public void dictionaryScanPositionCommittedAndAdvanced() {
        System.err.println("[AAA] Starting dictionary scan position test...");
        var cfg = MvConfig.fromBytes(getConfigBytes());
        try (YdbConnector conn = new YdbConnector(cfg, true)) {
            MvService wc = new MvService(conn);
            try {
                wc.applyDefaults(conn.getConfig().getProperties());
                Assertions.assertTrue(wc.getMetadata().isValid());

                MvViewExpr mainTarget = wc.getMetadata().getHandlers()
                        .get(HANDLER).getView("test1/mv1").getParts()
                        .values().iterator().next();
                String sqlQuery;
                try (MvSqlGen sg = new MvSqlGen(mainTarget)) {
                    sqlQuery = sg.makeSelectAll();
                }

                wc.startDefaultHandlers();
                wc.startDictionaryHandler();

                runDml(conn, WRITE_INITIAL_DATA);
                standardPause();
                clearMV(conn);
                refreshMV(wc);
                standardPause();
                standardPause();
                dictionaryScanPause();

                Optional<YdbStruct> baseline = readDictionaryScanPosition(conn, HANDLER, DICT_TABLE);
                System.err.println("[AAA] Baseline dictionary scan position: " + baseline);

                System.err.println("[AAA] First dictionary update...");
                runDml(conn, WRITE_DICT_ROUND1);
                dictionaryScanPause();

                Optional<YdbStruct> pos1 = readDictionaryScanPosition(conn, HANDLER, DICT_TABLE);
                Assertions.assertTrue(pos1.isPresent(),
                        "dictionary scan position must be committed after first refresh");
                if (baseline.isPresent()) {
                    Assertions.assertTrue(compareDictHistPositions(pos1.get(), baseline.get()) > 0,
                            "scan position must advance after first dictionary refresh");
                }
                Assertions.assertEquals(0L,
                        countDictHistRowsAfter(conn, DICT_HIST_TABLE, DICT_TABLE, pos1),
                        "first refresh must consume all pending dict_hist rows");

                System.err.println("[AAA] Second dictionary update...");
                runDml(conn, WRITE_DICT_ROUND2);
                long pendingBeforeSecond = waitForDictHistRowsAfter(
                        conn, DICT_HIST_TABLE, DICT_TABLE, pos1, 15_000L);
                Assertions.assertTrue(pendingBeforeSecond > 0L,
                        "new dict_hist rows expected after committed position");

                dictionaryScanPause();

                Optional<YdbStruct> pos2 = readDictionaryScanPosition(conn, HANDLER, DICT_TABLE);
                Assertions.assertTrue(pos2.isPresent(),
                        "dictionary scan position must remain committed after second refresh");
                Assertions.assertTrue(compareDictHistPositions(pos2.get(), pos1.get()) > 0,
                        "scan position must advance after second dictionary refresh");
                Assertions.assertEquals(0L,
                        countDictHistRowsAfter(conn, DICT_HIST_TABLE, DICT_TABLE, pos2),
                        "second refresh must consume only rows after the previous position");

                int diffCount = checkViewOutput(conn, "test1/mv1", sqlQuery, false, "id");
                Assertions.assertEquals(0, diffCount,
                        "view output mismatch after dictionary refreshes");
            } finally {
                wc.shutdown();
            }
        }
    }

    private void clearMV(YdbConnector conn) {
        conn.sqlWrite("DELETE FROM `test1/mv1`;",
                tech.ydb.table.query.Params.empty());
    }

    private void refreshMV(MvService wc) {
        wc.startScan(HANDLER, "test1/mv1");
    }

}
