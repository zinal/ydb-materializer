package tech.ydb.mv.apply;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

    private MvKey key(int value) {
        return new MvKey(keyInfo, new Comparable[]{value});
    }
}
