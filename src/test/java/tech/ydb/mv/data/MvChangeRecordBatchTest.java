package tech.ydb.mv.data;

import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import tech.ydb.mv.model.MvKeyInfo;
import tech.ydb.mv.model.MvTableInfo;
import tech.ydb.table.values.PrimitiveType;

/**
 * Tests for the batch marker on change records.
 *
 * @author zinal
 */
public class MvChangeRecordBatchTest {

    private final MvKeyInfo keyInfo = MvTableInfo.newBuilder("t")
            .addColumn("id", PrimitiveType.Int32)
            .addKey("id")
            .build()
            .getKeyInfo();

    @Test
    public void defaultsToInteractiveAndCopiesWithBatch() {
        MvKey key = new MvKey(keyInfo, new Comparable<?>[]{1});

        MvChangeRecord interactive = new MvChangeRecord(key, Instant.now());
        Assertions.assertFalse(interactive.isBatch());

        MvChangeRecord batch = interactive.withBatch(true);
        Assertions.assertTrue(batch.isBatch());
        Assertions.assertFalse(interactive.isBatch());
        Assertions.assertSame(batch, batch.withBatch(true));
        Assertions.assertSame(interactive, interactive.withBatch(false));
    }

}
