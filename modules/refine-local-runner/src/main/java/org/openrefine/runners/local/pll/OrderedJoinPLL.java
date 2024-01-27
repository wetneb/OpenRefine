
package org.openrefine.runners.local.pll;

import java.util.*;

import io.vavr.collection.Array;

import org.openrefine.runners.local.pll.partitioning.Partitioner;
import org.openrefine.runners.local.pll.partitioning.RangePartitioner;
import org.openrefine.runners.local.pll.util.IterationContext;
import org.openrefine.util.CloseableIterator;

/**
 * A PLL which represents the join of two others, assuming both are sorted by keys. The types of joins supported are
 * listed in {@link JoinType}. <br>
 * The partitions of this PLL are taken from the first PLL supplied (left). It is assumed that each key appears at most
 * once in each collection.
 * 
 * @author Antonin Delpeuch
 *
 * @param <K>
 * @param <V>
 * @param <W>
 */
public class OrderedJoinPLL<K, V, W> extends PLL<Tuple2<K, Tuple2<V, W>>> {

    /**
     * Type of join to perform (using SQL's terminology)
     */
    public enum JoinType {
        INNER, // only include pairs where the key is present in both collections
        LEFT, // include pairs where the key is missing in the right collection too
        RIGHT, // include pairs where the key is missing in the left collection
        FULL // include pairs where the key is missing in the left or right collection
    };

    private final PairPLL<K, V> first;
    private final PairPLL<K, W> second;
    private final Comparator<K> comparator;
    private final Array<JoinPartition> partitions;
    private final JoinType joinType;
    private Array<Optional<K>> firstKeys;
    private Array<Optional<K>> upperBounds;

    /**
     * Constructs a PLL representing the join of two others
     * 
     * @param first
     *            assumed to be sorted by keys
     * @param second
     *            assumed to be sorted by keys
     * @param comparator
     *            the comparator for the common order of keys
     * @param joinType
     *            whether the join should be inner or outer
     */
    public OrderedJoinPLL(
            PairPLL<K, V> first,
            PairPLL<K, W> second,
            Comparator<K> comparator,
            JoinType joinType) {
        super(first.getContext(), String.format("Ordered join (%s)", joinType));
        this.first = first;
        this.second = second;
        this.comparator = comparator;
        this.joinType = joinType;
        this.partitions = first.getPartitions()
                .map(p -> new JoinPartition(p.getIndex(), p));

        // Compute the first key in each partition but the first one
        if (getPartitioner().isPresent() && getPartitioner().get() instanceof RangePartitioner<?>) {
            RangePartitioner<K> partitioner = (RangePartitioner<K>) getPartitioner().get();
            firstKeys = Array.ofAll((List<Optional<K>>) partitioner.getFirstKeys());
        } else {
            firstKeys = first.runOnPartitionsWithoutInterruption(partition -> {
                try (CloseableIterator<Tuple2<K, V>> iterator = first.iterate(partition)) {
                    return iterator.map(Tuple2::getKey)
                            .headOption().toJavaOptional();
                }
            })
                    .drop(1);
        }
        // Compute the upper bound of each partition but the last one,
        // which is the first key of the first non-empty partition after it.
        // The list is created in reverse order.
        upperBounds = Array.empty();
        Optional<K> lastKeySeen = Optional.empty();
        for (int i = firstKeys.size() - 1; i >= 0; i--) {
            if (firstKeys.get(i).isPresent()) {
                lastKeySeen = firstKeys.get(i);
            }
            upperBounds = upperBounds.append(lastKeySeen);
        }
    }

    public Optional<Partitioner<K>> getPartitioner() {
        return first.getPartitioner();
    }

    @Override
    public Array<Long> computePartitionSizes() {
        if (JoinType.LEFT.equals(joinType)) {
            // for left joins we know that we have exactly as many elements as the first PLL,
            // because we have the same partitioning as the left PLL and the elements in those
            // partitions are preserved
            return first.getPartitionSizes();
        } else {
            // for other types of joins that could be different - resort to counting
            return super.getPartitionSizes();
        }
    }

    @Override
    public boolean hasCachedPartitionSizes() {
        return (JoinType.LEFT.equals(joinType) && first.hasCachedPartitionSizes()) || super.hasCachedPartitionSizes();
    }

    @Override
    protected CloseableIterator<Tuple2<K, Tuple2<V, W>>> compute(Partition partition, IterationContext context) {
        CloseableIterator<Tuple2<K, V>> firstStream = first.iterate(partition.getParent(), context);
        CloseableIterator<Tuple2<K, W>> secondStream;
        Optional<K> lowerBound = Optional.empty();
        Optional<K> upperBound = Optional.empty();
        if (partition.getIndex() > 0) {
            lowerBound = firstKeys.get(partition.getIndex() - 1);
            if (lowerBound.isEmpty()) {
                // This partition is empty on the left side.
                // We skip it: for an inner join, the result is clearly empty,
                // and for an outer join the corresponding elements on the right-hand side
                // are added to the joins of the neighbouring partitions.
                return CloseableIterator.empty();
            }
        }
        if (partition.getIndex() < numPartitions() - 1) {
            upperBound = upperBounds.get(numPartitions() - 2 - partition.getIndex());
        }
        secondStream = second.streamBetweenKeys(lowerBound, upperBound, comparator, context);
        return joinStreams(firstStream, secondStream, comparator, joinType);
    }

    /**
     * Merges two key-ordered streams where each key is guaranteed to appear at most once in each stream.
     *
     * @param firstIterator
     *            the first stream to join
     * @param secondIterator
     *            the second stream to join
     * @param comparator
     *            the comparator with respect to which both are sorted
     * @param joinType
     *            the type of join to compute
     * @return
     */
    protected static <K, V, W> CloseableIterator<Tuple2<K, Tuple2<V, W>>> joinStreams(
            CloseableIterator<Tuple2<K, V>> firstIterator,
            CloseableIterator<Tuple2<K, W>> secondIterator,
            Comparator<K> comparator,
            JoinType joinType) {
        boolean includeEmptyLeft = JoinType.RIGHT.equals(joinType) || JoinType.FULL.equals(joinType);
        boolean includeEmptyRight = JoinType.LEFT.equals(joinType) || JoinType.FULL.equals(joinType);
        return new CloseableIterator<>() {

            Tuple2<K, V> lastSeenLeft = null;
            Tuple2<K, W> lastSeenRight = null;
            Tuple2<K, Tuple2<V, W>> nextTuple = null;

            @Override
            public boolean hasNext() {
                fetchNextTuple();
                return nextTuple != null;
            }

            private void fetchNextTuple() {
                while ((nextTuple == null)
                        && ((lastSeenLeft != null || firstIterator.hasNext()) || (lastSeenRight != null || secondIterator.hasNext()))
                        && (lastSeenLeft != null || firstIterator.hasNext() || includeEmptyLeft)
                        && (lastSeenRight != null || secondIterator.hasNext() || includeEmptyRight)) {
                    if (lastSeenLeft == null && firstIterator.hasNext()) {
                        lastSeenLeft = firstIterator.next();
                    } else if (lastSeenRight == null && secondIterator.hasNext()) {
                        lastSeenRight = secondIterator.next();
                    } else if (lastSeenLeft != null
                            && lastSeenRight != null
                            && lastSeenLeft.getKey().equals(lastSeenRight.getKey())) {
                        nextTuple = Tuple2.of(lastSeenLeft.getKey(),
                                Tuple2.of(lastSeenLeft.getValue(), lastSeenRight.getValue()));
                        lastSeenLeft = null;
                        lastSeenRight = null;
                    } else if ((lastSeenLeft != null &&
                            lastSeenRight != null &&
                            comparator.compare(lastSeenLeft.getKey(), lastSeenRight.getKey()) > 0) ||
                            (lastSeenLeft == null && !firstIterator.hasNext())) {
                        if (includeEmptyLeft) {
                            nextTuple = Tuple2.of(lastSeenRight.getKey(),
                                    Tuple2.of(null, lastSeenRight.getValue()));
                        }
                        if (secondIterator.hasNext()) {
                            lastSeenRight = secondIterator.next();
                        } else {
                            lastSeenRight = null;
                        }
                    } else {
                        if (includeEmptyRight) {
                            nextTuple = Tuple2.of(lastSeenLeft.getKey(),
                                    Tuple2.of(lastSeenLeft.getValue(), null));
                        }
                        if (firstIterator.hasNext()) {
                            lastSeenLeft = firstIterator.next();
                        } else {
                            lastSeenLeft = null;
                        }
                    }
                }

            }

            @Override
            public Tuple2<K, Tuple2<V, W>> next() {
                fetchNextTuple();
                Tuple2<K, Tuple2<V, W>> toReturn = nextTuple;
                nextTuple = null;
                return toReturn;
            }

            @Override
            public void close() {
                firstIterator.close();
                secondIterator.close();
            }

        };
    }

    @Override
    public Array<? extends Partition> getPartitions() {
        return partitions;
    }

    @Override
    public List<PLL<?>> getParents() {
        return Arrays.asList(first, second);
    }

    protected static class JoinPartition implements Partition {

        protected final int index;
        protected final Partition parent;

        protected JoinPartition(int index, Partition parent) {
            this.index = index;
            this.parent = parent;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public Partition getParent() {
            return parent;
        }

    }

}
