package tech.ydb.mv.apply;

/**
 * Admission policy for the apply queue.
 *
 * Reserves a fraction of {@code queueLimit} for interactive (non-batch) work so
 * that scan/dictionary batch submissions cannot occupy the whole queue.
 *
 * @author zinal
 */
final class MvApplyQueuePolicy {

    static final int MIN_QUEUE_LIMIT = 1000;

    private final int queueLimit;
    private final int applyQueuePercent;
    private final int maxBatchQueue;

    /**
     * @param queueLimit Absolute apply queue limit. Values below
     * {@link #MIN_QUEUE_LIMIT} are raised to that minimum.
     * @param applyQueuePercent Percent of the queue reserved for interactive
     * (non-batch) operations, in {@code [0, 100]}.
     */
    MvApplyQueuePolicy(int queueLimit, int applyQueuePercent) {
        this.queueLimit = Math.max(MIN_QUEUE_LIMIT, queueLimit);
        this.applyQueuePercent = clampPercent(applyQueuePercent);
        this.maxBatchQueue = (int) ((long) this.queueLimit
                * (100L - this.applyQueuePercent) / 100L);
    }

    private static int clampPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    int getQueueLimit() {
        return queueLimit;
    }

    int getApplyQueuePercent() {
        return applyQueuePercent;
    }

    /**
     * @return Maximum number of batch items allowed in the queue.
     */
    int getMaxBatchQueue() {
        return maxBatchQueue;
    }

    /**
     * Decide whether one more item may be accepted by a non-forced submit.
     *
     * Interactive items may use the full queue. Batch items are limited by
     * {@link #getMaxBatchQueue()} so that at least
     * {@code applyQueuePercent}% of capacity remains available for interactive
     * work while batch items are present.
     *
     * @param batch {@code true} for a batch item.
     * @param queueSize Current total queue size.
     * @param batchQueueSize Current number of batch items in the queue.
     * @return {@code true} if the item may be enqueued.
     */
    boolean canAdmit(boolean batch, int queueSize, int batchQueueSize) {
        if (queueSize >= queueLimit) {
            return false;
        }
        if (batch) {
            return batchQueueSize < maxBatchQueue;
        }
        return true;
    }

}
