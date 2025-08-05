package com.bayoumi.services;


import com.bayoumi.util.LoggerWrapper;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * {@link TimerTask} with modifiable execution period.
 *
 * @author Bayoumi
 */
public class EditablePeriodTimerTask extends TimerTask {

    private final Runnable task;
    private final Supplier<Long> period;
    private Timer timer;
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(EditablePeriodTimerTask.class);


    /**
     * Constructor with task and supplier for period
     *
     * @param task   the task to execute in {@link TimerTask#run()}
     * @param period a provider for the period between task executions
     */
    public EditablePeriodTimerTask(Runnable task, Supplier<Long> period) {
        super();
        Objects.requireNonNull(task);
        Objects.requireNonNull(period);
        this.task = task;
        this.period = period;
        timer = new Timer();
    }

    public final void updateTimer() {
        Long p = period.get();
        Objects.requireNonNull(p);
        LOGGER.info(() -> String.format("Period set to: %d s", p / 1000));
        stopTask();
        timer = new Timer();
        timer.schedule(new EditablePeriodTimerTask(task, period), p, p);
    }

    public void stopTask() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = null;
    }

    @Override
    public void run() {
        task.run();
        LOGGER.info(() -> "run():- " + Thread.currentThread().getName());
    }

}