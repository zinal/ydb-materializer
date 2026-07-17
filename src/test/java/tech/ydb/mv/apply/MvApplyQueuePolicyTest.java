package tech.ydb.mv.apply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for apply-queue admission control.
 *
 * @author zinal
 */
public class MvApplyQueuePolicyTest {

    @Test
    public void reservesInteractiveShareForBatchCap() {
        MvApplyQueuePolicy policy = new MvApplyQueuePolicy(1000, 40);

        Assertions.assertEquals(1000, policy.getQueueLimit());
        Assertions.assertEquals(40, policy.getApplyQueuePercent());
        Assertions.assertEquals(600, policy.getMaxBatchQueue());

        // Batch may fill up to the non-reserved share.
        Assertions.assertTrue(policy.canAdmit(true, 599, 599));
        Assertions.assertFalse(policy.canAdmit(true, 600, 600));

        // Interactive may use the full queue even when batch items are present.
        Assertions.assertTrue(policy.canAdmit(false, 600, 600));
        Assertions.assertTrue(policy.canAdmit(false, 999, 600));
        Assertions.assertFalse(policy.canAdmit(false, 1000, 600));
    }

    @Test
    public void interactiveCanUseFullQueueWhenNoBatch() {
        MvApplyQueuePolicy policy = new MvApplyQueuePolicy(100, 40);

        Assertions.assertTrue(policy.canAdmit(false, 0, 0));
        Assertions.assertTrue(policy.canAdmit(false, 99, 0));
        Assertions.assertFalse(policy.canAdmit(false, 100, 0));
    }

    @Test
    public void clampsPercentAndHandlesEdges() {
        Assertions.assertEquals(0, new MvApplyQueuePolicy(100, 100).getMaxBatchQueue());
        Assertions.assertEquals(100, new MvApplyQueuePolicy(100, 0).getMaxBatchQueue());
        Assertions.assertEquals(0, new MvApplyQueuePolicy(100, 150).getMaxBatchQueue());
        Assertions.assertEquals(100, new MvApplyQueuePolicy(100, -10).getMaxBatchQueue());

        MvApplyQueuePolicy noBatch = new MvApplyQueuePolicy(50, 100);
        Assertions.assertFalse(noBatch.canAdmit(true, 0, 0));
        Assertions.assertTrue(noBatch.canAdmit(false, 0, 0));
    }

}
