package tech.ydb.mv.integration;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.ydb.mv.AbstractIntegrationBase;
import tech.ydb.mv.MvConfig;
import tech.ydb.mv.YdbConnector;
import tech.ydb.mv.svc.MvService;

/**
 * Verifies that rows are removed from the MV when source updates change column
 * values so the row no longer satisfies the MV {@code WHERE} filter (as opposed
 * to physical DELETE on source tables).
 *
 * colima start --arch aarch64 --vm-type=vz --vz-rosetta (or) colima start
 * --arch amd64
 *
 * while mvn test -Dtest=WhereFilterObsoleteIntegrationTest; do sleep 0.5s; done
 *
 * @author zinal
 */
public class FilterObsoleteIntegrationTest extends AbstractIntegrationBase {

    public static final String CREATE_TABLES_WHERE_OBS = """
CREATE TABLE `where_obs/main` (
    id Text NOT NULL,
    c1 Timestamp,
    c2 Int64,
    filter_val Int32,
    label Text,
    PRIMARY KEY(id)
);

CREATE TABLE `where_obs/sub` (
    c1 Timestamp,
    c2 Int64,
    sub_status Text,
    PRIMARY KEY(c1, c2)
);

CREATE TABLE `where_obs/mv` (
    id Text NOT NULL,
    label Text,
    sub_status Text,
    PRIMARY KEY(id)
);

ALTER TABLE `where_obs/main` ADD CHANGEFEED `cf_main` WITH (FORMAT = 'JSON', MODE = 'KEYS_ONLY');
ALTER TABLE `where_obs/sub` ADD CHANGEFEED `cf_sub` WITH (FORMAT = 'JSON', MODE = 'NEW_AND_OLD_IMAGES');
""";

    public static final String DROP_TABLES_WHERE_OBS = """
DROP TABLE `where_obs/main`;
DROP TABLE `where_obs/sub`;
DROP TABLE `where_obs/mv`;
""";

    public static final String CDC_CONSUMERS_WHERE_OBS = """
ALTER TOPIC `where_obs/main/cf_main` ADD CONSUMER `where_obs_consumer`;
ALTER TOPIC `where_obs/sub/cf_sub` ADD CONSUMER `where_obs_consumer`;
""";

    public static final String UPSERT_CONFIG_WHERE_OBS = """
UPSERT INTO `test1/statements` (module_id, statement_no, statement_text) VALUES
  ('', 1, @@CREATE ASYNC MATERIALIZED VIEW `where_obs/mv` AS
  SELECT main.id AS id, main.label AS label, sub.sub_status AS sub_status
  FROM `where_obs/main` AS main
  LEFT JOIN `where_obs/sub` AS sub
    ON main.c1=sub.c1 AND main.c2=sub.c2
  WHERE #[ main.filter_val=7 AND (sub.sub_status IS NULL OR sub.sub_status='ok'u) ]#;@@),

  ('where_obs_handler', 1, @@CREATE ASYNC HANDLER where_obs_handler CONSUMER where_obs_consumer
  PROCESS `where_obs/mv`,
  INPUT `where_obs/main` CHANGEFEED cf_main AS STREAM,
  INPUT `where_obs/sub` CHANGEFEED cf_sub AS STREAM;@@);
""";

    public static final String SELECT_ALL_WHERE_OBS = """
SELECT main.id AS id, main.label AS label, sub.sub_status AS sub_status
FROM `where_obs/main` AS main
LEFT JOIN `where_obs/sub` AS sub
  ON main.c1=sub.c1 AND main.c2=sub.c2
WHERE main.filter_val=7 AND (sub.sub_status IS NULL OR sub.sub_status='ok'u)
""";

    private static final String TS1 = "Timestamp('2021-01-02T10:15:21Z')";
    private static final String TS2 = "Timestamp('2022-01-02T10:15:22Z')";
    private static final String TS3 = "Timestamp('2023-01-03T10:15:23Z')";
    private static final String TS4 = "Timestamp('2024-01-04T10:15:24Z')";

    /** Rows that satisfy the MV filter (filter_val=7, sub ok or absent). */
    private static final String WRITE_INITIAL = """
INSERT INTO `where_obs/main` (id,c1,c2,filter_val,label) VALUES
 ('m1'u, %s, 10001, 7, 'alpha'u)
,('m2'u, %s, 10002, 7, 'beta'u)
;
INSERT INTO `where_obs/sub` (c1,c2,sub_status) VALUES
 (%s, 10001, 'ok'u)
;
""".formatted(TS1, TS2, TS1);

    /** Additional rows added after the initial sync. */
    private static final String WRITE_MORE = """
INSERT INTO `where_obs/main` (id,c1,c2,filter_val,label) VALUES
 ('m3'u, %s, 10003, 7, 'gamma'u)
,('m4'u, %s, 10004, 7, 'delta'u)
;
INSERT INTO `where_obs/sub` (c1,c2,sub_status) VALUES
 (%s, 10003, 'ok'u)
,(%s, 10004, 'ok'u)
;
""".formatted(TS3, TS4, TS3, TS4);

    /**
     * m1 from the initial set: filter_val no longer matches WHERE.
     * m3 from the later batch: sub_status no longer matches WHERE.
     */
    private static final String WRITE_MAKE_OBSOLETE = """
UPSERT INTO `where_obs/main` (id,filter_val) VALUES
 ('m1'u, 8)
;
UPSERT INTO `where_obs/sub` (c1,c2,sub_status) VALUES
 (%s, 10003, 'blocked'u)
;
""".formatted(TS3);

    @Override
    protected Properties getConfigProps() {
        var props = super.getConfigProps();
        props.setProperty(MvConfig.CONF_HANDLERS, "where_obs_handler");
        return props;
    }

    @BeforeEach
    public void init() {
        pause(5000L);
        System.err.println("[WHEREOBS] Database setup...");
        var cfg = MvConfig.fromBytes(getConfigBytes());
        try (YdbConnector conn = new YdbConnector(cfg)) {
            runDdl(conn, CREATE_TABLES_BASE);
            runDdl(conn, CREATE_TABLES_WHERE_OBS);
            runDdl(conn, CDC_CONSUMERS_WHERE_OBS);
            runDdl(conn, UPSERT_CONFIG_WHERE_OBS);
        }
    }

    @AfterEach
    public void cleanup() {
        System.err.println("[WHEREOBS] Database cleanup...");
        var cfg = MvConfig.fromBytes(getConfigBytes());
        try (YdbConnector conn = new YdbConnector(cfg)) {
            runDdl(conn, DROP_TABLES_BASE);
            runDdl(conn, DROP_TABLES_WHERE_OBS);
        }
    }

    @Test
    public void whereFilterObsoleteIntegrationTest() {
        System.err.println("[WHEREOBS] Starting up...");
        var cfg = MvConfig.fromBytes(getConfigBytes());
        try (YdbConnector conn = new YdbConnector(cfg, true)) {
            try (MvService svc = new MvService(conn)) {
                svc.applyDefaults(conn.getConfig().getProperties());

                System.err.println("[WHEREOBS] Checking context...");
                svc.printIssues(System.out);
                Assertions.assertTrue(svc.getMetadata().isValid());

                System.err.println("[WHEREOBS] (1) Writing initial source rows...");
                runDml(conn, WRITE_INITIAL);

                System.err.println("[WHEREOBS] (2) Starting handlers for initial sync...");
                svc.startDefaultHandlers();
                standardPause();
                System.err.println("[WHEREOBS] Checking MV after initial sync...");
                Assertions.assertEquals(0,
                        checkViewOutput(conn, "where_obs/mv", SELECT_ALL_WHERE_OBS, false, "id"),
                        "MV must match filtered join after initial sync");

                System.err.println("[WHEREOBS] (3) Adding more source rows...");
                runDml(conn, WRITE_MORE);
                standardPause();
                System.err.println("[WHEREOBS] Checking MV after additional inserts...");
                Assertions.assertEquals(0,
                        checkViewOutput(conn, "where_obs/mv", SELECT_ALL_WHERE_OBS, false, "id"),
                        "MV must include newly inserted matching rows");

                System.err.println("[WHEREOBS] (4) Updating rows to fall out of WHERE filter...");
                runDml(conn, WRITE_MAKE_OBSOLETE);
                standardPause();
                System.err.println("[WHEREOBS] Checking MV after filter-breaking updates...");
                Assertions.assertEquals(0,
                        checkViewOutput(conn, "where_obs/mv", SELECT_ALL_WHERE_OBS, false, "id"),
                        "MV must drop rows that no longer satisfy the WHERE filter");
            }
        }
    }

}
