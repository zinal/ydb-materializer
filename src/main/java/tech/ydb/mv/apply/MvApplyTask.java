package tech.ydb.mv.apply;

import java.util.List;

import tech.ydb.mv.feeder.MvCommitHandler;
import tech.ydb.mv.data.MvChangeRecord;

/**
 *
 * @author zinal
 */
public class MvApplyTask {

    private final MvChangeRecord data;
    private final MvApplyActionList actions;
    private final MvCommitHandler commit;

    public MvApplyTask(MvChangeRecord data, MvCommitHandler commit,
            MvApplyActionList actions) {
        this.data = data;
        this.actions = actions;
        this.commit = commit;
    }

    public MvApplyTask(MvChangeRecord data, MvCommitHandler commit,
            List<MvApplyAction> actions, int workerId) {
        this.data = data;
        this.actions = new MvApplyActionList(actions);
        this.commit = commit;
    }

    public MvChangeRecord getData() {
        return data;
    }

    public List<MvApplyAction> getActions() {
        return actions.getItems();
    }

    public MvCommitHandler getCommit() {
        return commit;
    }

    /**
     * @return {@code true} when the underlying change belongs to a batch flow.
     */
    public boolean isBatch() {
        return data != null && data.isBatch();
    }

    @Override
    public String toString() {
        return "MvApplyTask{" + data + '}';
    }

}
