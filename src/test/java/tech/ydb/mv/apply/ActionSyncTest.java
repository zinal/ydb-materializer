package tech.ydb.mv.apply;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import tech.ydb.mv.data.MvChangeRecord;
import tech.ydb.mv.data.MvChangeRecord.OpType;
import tech.ydb.mv.data.MvKey;
import tech.ydb.mv.model.MvKeyInfo;
import tech.ydb.mv.model.MvTableInfo;
import tech.ydb.table.values.PrimitiveType;

/**
 * Tests for pure ActionSync helper behavior.
 */
public class ActionSyncTest {

    private final MvKeyInfo keyInfo = MvTableInfo.newBuilder("mv")
            .addColumn("id", PrimitiveType.Int32)
            .addKey("id")
            .build()
            .getKeyInfo();

    @Test
    public void testFindMissingKeysReturnsOnlyExpectedKeysAbsentFromActual() {
        List<MvKey> expected = List.of(key(1), key(2), key(3));
        Set<MvKey> actual = new HashSet<>(List.of(key(1), key(3), key(4)));

        List<MvKey> missing = ActionSync.findMissingKeys(expected, actual);

        Assertions.assertEquals(List.of(key(2)), missing);
    }

    @Test
    public void testFindMissingKeysReturnsEmptyWhenAllKeysWereUpserted() {
        List<MvKey> expected = List.of(key(1), key(2));
        Set<MvKey> actual = new HashSet<>(expected);

        Assertions.assertTrue(ActionSync.findMissingKeys(expected, actual).isEmpty());
    }

    @Test
    public void testDeduplicateDeleteWinsWhenItIsTheLatestEvent() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:01Z");
        MvKey k = key(1);
        List<MvApplyTask> input = List.of(
                task(k, t1, OpType.UPSERT),
                task(k, t2, OpType.DELETE));

        ArrayList<MvKey> upsert = new ArrayList<>();
        ArrayList<MvKey> delete = new ArrayList<>();
        ActionSync.deduplicate(input, false, upsert, delete);

        Assertions.assertTrue(upsert.isEmpty());
        Assertions.assertEquals(List.of(k), delete);
    }

    @Test
    public void testDeduplicateUpsertWinsWhenItIsTheLatestEvent() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:01Z");
        MvKey k = key(1);
        List<MvApplyTask> input = List.of(
                task(k, t1, OpType.DELETE),
                task(k, t2, OpType.UPSERT));

        ArrayList<MvKey> upsert = new ArrayList<>();
        ArrayList<MvKey> delete = new ArrayList<>();
        ActionSync.deduplicate(input, false, upsert, delete);

        Assertions.assertEquals(List.of(k), upsert);
        Assertions.assertTrue(delete.isEmpty());
    }

    @Test
    public void testDeduplicateSameTimestampUsesInputOrder() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        MvKey k = key(1);
        List<MvApplyTask> input = List.of(
                task(k, t, OpType.UPSERT),
                task(k, t, OpType.DELETE));

        ArrayList<MvKey> upsert = new ArrayList<>();
        ArrayList<MvKey> delete = new ArrayList<>();
        ActionSync.deduplicate(input, false, upsert, delete);

        Assertions.assertTrue(upsert.isEmpty());
        Assertions.assertEquals(List.of(k), delete);
    }

    @Test
    public void testDeduplicateSkipsDeletesWhenConfigured() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:01Z");
        MvKey k = key(1);
        List<MvApplyTask> input = List.of(
                task(k, t1, OpType.UPSERT),
                task(k, t2, OpType.DELETE));

        ArrayList<MvKey> upsert = new ArrayList<>();
        ArrayList<MvKey> delete = new ArrayList<>();
        ActionSync.deduplicate(input, true, upsert, delete);

        Assertions.assertEquals(List.of(k), upsert);
        Assertions.assertTrue(delete.isEmpty());
    }

    private MvApplyTask task(MvKey key, Instant tv, OpType op) {
        return new MvApplyTask(new MvChangeRecord(key, tv, op), null, List.of(), 0);
    }

    private MvKey key(int value) {
        return new MvKey(keyInfo, new Comparable[]{value});
    }
}
