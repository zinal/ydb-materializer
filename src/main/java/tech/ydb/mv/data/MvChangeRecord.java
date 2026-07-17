package tech.ydb.mv.data;

import java.time.Instant;
import java.util.Map;

/**
 * Change record produced by CDC/scan for a single table key.
 *
 * Carries operation type and optional "before"/"after" images.
 *
 * @author zinal
 */
public class MvChangeRecord {

    private final MvKey key;
    private final Instant tv;
    private final OpType operationType;
    private final YdbStruct imageBefore;
    private final YdbStruct imageAfter;
    private final boolean batch;

    /**
     * Create an UPSERT change record without images.
     *
     * @param key Table key.
     * @param tv Timestamp/version of the change.
     */
    public MvChangeRecord(MvKey key, Instant tv) {
        this(key, tv, OpType.UPSERT);
    }

    /**
     * Create a change record without images.
     *
     * @param key Table key.
     * @param tv Timestamp/version of the change.
     * @param operationType Operation type.
     */
    public MvChangeRecord(MvKey key, Instant tv, OpType operationType) {
        this(key, tv, operationType, YdbStruct.EMPTY, YdbStruct.EMPTY, false);
    }

    /**
     * Create a change record with before/after images.
     *
     * @param key Table key.
     * @param tv Timestamp/version of the change.
     * @param operationType Operation type.
     * @param imageBefore Row image before change (may be {@code null}).
     * @param imageAfter Row image after change (may be {@code null}).
     */
    public MvChangeRecord(MvKey key, Instant tv, OpType operationType,
            YdbStruct imageBefore, YdbStruct imageAfter) {
        this(key, tv, operationType, imageBefore, imageAfter, false);
    }

    /**
     * Create a change record with before/after images and batch marker.
     *
     * @param key Table key.
     * @param tv Timestamp/version of the change.
     * @param operationType Operation type.
     * @param imageBefore Row image before change (may be {@code null}).
     * @param imageAfter Row image after change (may be {@code null}).
     * @param batch {@code true} for scan/dictionary-driven batch work.
     */
    public MvChangeRecord(MvKey key, Instant tv, OpType operationType,
            YdbStruct imageBefore, YdbStruct imageAfter, boolean batch) {
        this.key = key;
        this.tv = tv;
        this.operationType = operationType;
        this.imageBefore = (imageBefore == null) ? YdbStruct.EMPTY : imageBefore;
        this.imageAfter = (imageAfter == null) ? YdbStruct.EMPTY : imageAfter;
        this.batch = batch;
    }

    public MvKey getKey() {
        return key;
    }

    public Instant getTv() {
        return tv;
    }

    public OpType getOperationType() {
        return operationType;
    }

    public YdbStruct getImageBefore() {
        return imageBefore;
    }

    public YdbStruct getImageAfter() {
        return imageAfter;
    }

    /**
     * @return {@code true} if the record belongs to a scan/dictionary batch flow.
     */
    public boolean isBatch() {
        return batch;
    }

    /**
     * Return this record with the requested batch marker.
     *
     * @param batch Desired batch marker.
     * @return This instance when the marker already matches, otherwise a copy.
     */
    public MvChangeRecord withBatch(boolean batch) {
        if (this.batch == batch) {
            return this;
        }
        return new MvChangeRecord(key, tv, operationType, imageBefore, imageAfter, batch);
    }

    @Override
    public String toString() {
        return "CR{" + "key=" + key + ", op=" + operationType
                + ", batch=" + batch
                + ", before=" + imageBefore + ", after=" + imageAfter + '}';
    }

    /**
     * Keep this change as the latest event for its key when it is newer than
     * the stored one (by timestamp, with input order as tiebreaker).
     */
    public void recordLatest(Map<MvKey, LatestEvent> latestByKey, int order) {
        LatestEvent prev = latestByKey.get(key);
        if (prev == null || tv.isAfter(prev.tv)
                || (tv.equals(prev.tv) && order > prev.order)) {
            latestByKey.put(key, new LatestEvent(operationType, tv, order));
        }
    }

    /**
     * Latest CDC event recorded for a key during batch deduplication.
     */
    public static final class LatestEvent {

        private final OpType operationType;
        private final Instant tv;
        private final int order;

        LatestEvent(OpType operationType, Instant tv, int order) {
            this.operationType = operationType;
            this.tv = tv;
            this.order = order;
        }

        public OpType getOperationType() {
            return operationType;
        }
    }

    /**
     * Operation type of the change.
     */
    public static enum OpType {
        /**
         * Insert or update a row
         */
        UPSERT,
        /**
         * Delete a row
         */
        DELETE
    }

}
