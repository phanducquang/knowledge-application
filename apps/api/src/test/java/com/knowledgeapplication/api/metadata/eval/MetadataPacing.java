package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.*;
import java.util.function.LongSupplier;

/** Evaluation-only monotonic minimum spacing; never uses wall-clock minute resets. */
final class MetadataPacing implements MetadataEvaluation.Pacer {
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
    static final class WaitBudget {
        private final LongSupplier ticker; private final Sleeper sleeper; private final long maxNanos;
        private long spent;
        WaitBudget(LongSupplier ticker,Sleeper sleeper,int maxWaitSeconds) {
            this.ticker=ticker; this.sleeper=sleeper; this.maxNanos=maxWaitSeconds*1_000_000_000L;
        }
        void sleep(long nanos) {
            checkInterrupted();
            if(nanos<0 || nanos>maxNanos-spent) throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET);
            long remaining=nanos;
            while(remaining>0) {
                long millis=Math.min(30000,(remaining+999999)/1000000),start=ticker.getAsLong();
                try { sleeper.sleep(millis); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET); }
                long elapsed=ticker.getAsLong()-start;
                if(elapsed<=0 || elapsed>maxNanos-spent) throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET);
                spent+=elapsed; remaining=Math.max(0,remaining-elapsed); checkInterrupted();
            }
        }
    }
    private final LongSupplier ticker; private final WaitBudget wait; private final MetadataEvaluation.Pacer database;
    private final long intervalNanos;
    private boolean started; private long lastAttempt;
    MetadataPacing(long maxRpm,long metadataRpm,long safetyMillis,LongSupplier ticker,WaitBudget wait,MetadataEvaluation.Pacer database) {
        if(maxRpm<1 || maxRpm>60000 || metadataRpm<1 || safetyMillis<1 || safetyMillis>10000) throw new IllegalArgumentException("Invalid evaluation pacing");
        long effective=Math.min(maxRpm,metadataRpm);
        intervalNanos=((60000+effective-1)/effective+safetyMillis)*1_000_000L;
        this.ticker=ticker; this.wait=wait; this.database=database;
    }
    long intervalMillis() { return intervalNanos/1_000_000; }
    @Override public void await(long chars) {
        checkInterrupted(); database.await(chars);
        if(started) {
            long elapsed=ticker.getAsLong()-lastAttempt;
            if(elapsed<0) throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET);
            if(elapsed<intervalNanos) wait.sleep(intervalNanos-elapsed);
        }
        checkInterrupted(); attemptStarted();
    }
    // Called again by the live adapter immediately before transport, after its atomic reservation.
    void attemptStarted() { lastAttempt=ticker.getAsLong(); started=true; }
    private static void checkInterrupted() {
        if(Thread.currentThread().isInterrupted()) throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET);
    }
}
