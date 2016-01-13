package com.conversantmedia.util.concurrent;

/**
 * This implements the CAS approach to lock-free synchronization, in
 * other words a sequence of atomic changes.  Each change is represented by an
 * integer sequence number.  Each sequence number may represent a single atomic
 * change for a single thread.  All other threads, who fail to acquire permission
 * to update the sequence must obtain another sequence number and try again.
 *
 * If the call to update succeeds, the the caller must call commit to notify other
 * threads that they are finished modifying the sequence.  Otherwise deadlock will occur.
 *
 * Atomic means that only one change per sequence number is possible.  And that changes
 * to numerically lower numbers are guaranteed to occur prior to changes to numerically higher
 * sequence numbers.
 *
 * Unlike synchronization, this mechanism does not include a fence operation.    No thread state
 * is synchronized by this call.    If your usage requires a fence that should be implemented with
 * volatile variables.
 *
 * Its possible for the sequence to flip negative, over hundreds of years or with incredibly fast hardware
 * this would have no impact on the correctness of the atomic sequence.   However, the best practice for code of this nature is
 * to make only relative comparison,   sequence1 - sequence2 {@literal >} 0, rather than sequence1 {@literal >} sequence2
 *
 *
 * The general strategy is as follows:
 *
 * <pre><code>
 * for(;;) {
 *     long lock = sequence.get();
 *     // any preliminary checking (capacity, etc. can be done here
 *     // the next call ensures that no other thread has modified the sequence
 *     // while we work
 *     if(sequence.update(lock)) {
 *          try {
 *              // update something atomically here
 *              return;
 *          } finally {
 *              sequence.commit();
 *          }
 *     }
 * }
 * </code></pre>
 *
 *
 * Created by jcairns on 9/24/14.
 */
public final class AtomicSequence {
    private final PaddedAtomicLong cursor = new PaddedAtomicLong(0L);
    private final PaddedAtomicLong sequence = new PaddedAtomicLong(0L);

    // Locally (L1) cached value of the sequence
    // try to use the value in this cores L1 cache whenever possible
    // rather than reading from memory every time
    private final PaddedLong       sequenceCache = new PaddedLong(0L);


    /**
     * @return long - the current sequence
     */
    public long get() {
        return sequenceCache.value;
    }

    /**
     * force a fenced read of the sequence, only required if the sequence is known out of date
     *
     * @return long - sequence number
     */
    public long getAtomic() {
        return sequenceCache.value = sequence.get();
    }

    /**
     *
     * @param sequence - input sequence number
     * @return boolean - true if its safe to write the transaction for the given sequence
     */
    public boolean update(final long sequence) {
        if(cursor.compareAndSet(sequence, sequence+1)) {
            return true;
        }

        // must read the sequence cache from memory as it has
        // been modified by another thread
        sequenceCache.value = this.sequence.get();

        return false;
    }

    /**
     * commit the change to the sequence
     */
    public void commit() {
        sequence.lazySet(cursor.get());
        sequenceCache.value = sequence.get();
    }

}
