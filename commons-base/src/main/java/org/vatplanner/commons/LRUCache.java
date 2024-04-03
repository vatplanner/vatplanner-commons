package org.vatplanner.commons;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link Map}-like thread-safe cache evicting entries based on a configurable Least Recently Used policy.
 * Real-time {@link Instant}s are used for tracking last usage, so eviction depends on system time.
 * <p>
 * Entries can be stored, retrieved and managed similar to a {@link Map} using {@link #put(Object, Object)},
 * {@link #get(Object)}, {@link #remove(Object)} and {@link #clear()}. {@code null} values can be stored but not
 * distinguished from non-existing entries.
 * </p>
 * <p>
 * LRU behaviour is implemented by recording/updating a timestamp indicating last usage when inserting a value via
 * {@link #put(Object, Object)} or retrieving it via {@link #get(Object)}. LRU policy is only applied when calling
 * {@link #maintain()} or inserting a value via {@link #put(Object, Object)}.
 * </p>
 * <p>
 * The following options can be configured:
 * </p>
 * <ul>
 * <li>{@link #setMaxEntries(int)} controls the maximum number of entries to keep. If that limit is exceeded, the least
 * recently used entries will be evicted from the cache until the total number of entries matches the policy again.</li>
 * <li>{@link #setMinEntries(int)} controls the number of most recent entries to be kept, regardless of when they were
 * last used.</li>
 * <li>{@link #setUsageExpiration(Duration)} controls the maximum time since last usage allowed to keep an entry.
 * This does not apply to entries that should be kept according to {@link #setMinEntries(int)}. Full eviction only
 * happens if the minimum number of entries is configured to 0.
 * </li>
 * </ul>
 * <p>
 * By default, the policy is unbound meaning any number of entries will be kept regardless of when they were used
 * (min 0, max open, no usage expiration).
 * </p>
 * <p>
 * Reconfiguring the policy after entries have already been inserted requires {@link #maintain()} to be additionally
 * called at an appropriate time.
 * </p>
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
public class LRUCache<K, V> {
    private final HashMap<K, Entry<V>> map = new HashMap<>();

    private int minEntries = 0;
    private int maxEntries = Integer.MAX_VALUE;
    private Duration usageExpiration = null;

    private static class Entry<V> {
        Instant lastUsed;
        V value;

        Entry(Instant lastUsed, V value) {
            this.lastUsed = lastUsed;
            this.value = value;
        }
    }

    private static class MaintenanceEntry<K> {
        final K key;
        final Duration age;

        MaintenanceEntry(K key, Duration age) {
            this.key = key;
            this.age = age;
        }

        Duration getAge() {
            return age;
        }
    }

    /**
     * Controls the number of most recent entries to be kept, regardless of when they were last used (default: 0).
     * <p>
     * If combined with {@link #setUsageExpiration(Duration)}, the configured number of most recent expired entries
     * will be protected from eviction.
     * </p>
     *
     * @param minEntries minimum number of most-recent entries to keep, if present
     * @return same instance for method-chaining
     */
    public LRUCache<K, V> setMinEntries(int minEntries) {
        synchronized (this) {
            this.minEntries = minEntries;
        }

        return this;
    }

    /**
     * Controls the maximum number of entries to keep. If that limit is exceeded, the least recently used entries will
     * be evicted from the cache until the total number of entries matches the policy again.
     *
     * @param maxEntries maximum number of most-recent entries to keep
     * @return same instance for method-chaining
     */
    public LRUCache<K, V> setMaxEntries(int maxEntries) {
        synchronized (this) {
            this.maxEntries = maxEntries;
        }

        return this;
    }

    /**
     * Controls the maximum time since last usage allowed to keep an entry.
     * <p>
     * This does not apply to entries that should be kept according to {@link #setMinEntries(int)}. Full eviction only
     * happens if the minimum number of entries is configured to 0.
     * </p>
     *
     * @param usageExpiration maximum time (inclusive) since last usage before evicting an entry
     * @return same instance for method-chaining
     */
    public LRUCache<K, V> setUsageExpiration(Duration usageExpiration) {
        synchronized (this) {
            this.usageExpiration = usageExpiration;
        }

        return this;
    }

    /**
     * Returns the value stored for the given key.
     * <p>
     * If present, the entry's usage will be reset to time of access.
     * </p>
     *
     * @param key key to look up value for
     * @return value stored for key; {@code null} if not present
     */
    public V get(K key) {
        synchronized (map) {
            Entry<V> entry = map.get(key);
            if (entry == null) {
                return null;
            }

            entry.lastUsed = now();

            return entry.value;
        }
    }

    /**
     * Stores the given value under the specified key.
     * <p>
     * The entry's usage will be initially set to time of insertion.
     * </p>
     * <p>
     * LRU policy is being applied as part of this call, so previous entries may get evicted from the cache as a
     * side-effect.
     * </p>
     *
     * @param key   key to store value under
     * @param value value to store under key
     * @return previously stored value; {@code null} if no value was present for the given key
     */
    public V put(K key, V value) {
        Entry<V> oldEntry;

        synchronized (map) {
            Entry<V> newEntry = new Entry<>(now(), value);
            oldEntry = map.put(key, newEntry);
        }

        maintain();

        return (oldEntry != null) ? oldEntry.value : null;
    }

    /**
     * Removes the entry matching the given key.
     *
     * @param key key of entry to remove
     * @return previously stored value; {@code null} if no value was present for the given key
     */
    public V remove(K key) {
        synchronized (map) {
            Entry<V> oldEntry = map.remove(key);
            return (oldEntry != null) ? oldEntry.value : null;
        }
    }

    /**
     * Removes all entries.
     */
    public void clear() {
        synchronized (map) {
            map.clear();
        }
    }

    /**
     * Applies LRU policy, evicting any expired or excessive entries from cache.
     */
    public void maintain() {
        // copy configuration for consistent application
        int minEntriesCopy;
        int maxEntriesCopy;
        Duration usageExpirationCopy;
        synchronized (this) {
            minEntriesCopy = this.minEntries;
            maxEntriesCopy = this.maxEntries;
            usageExpirationCopy = this.usageExpiration;
        }

        synchronized (map) {
            Instant startOfMaintenance = now();

            // calculate and sort by age
            List<MaintenanceEntry<K>> maintenanceEntries = map.entrySet()
                                                              .stream()
                                                              .map(mapEntry -> new MaintenanceEntry<>(
                                                                  mapEntry.getKey(),
                                                                  Duration.between(mapEntry.getValue().lastUsed, startOfMaintenance)
                                                              ))
                                                              .sorted(Comparator.comparing(MaintenanceEntry::getAge))
                                                              .collect(Collectors.toList());

            int index = -1;
            for (MaintenanceEntry<K> maintenanceEntry : maintenanceEntries) {
                index++;

                // keep if minEntries is not reached yet
                if (index < minEntriesCopy) {
                    continue;
                }

                // do not keep if this entry exceeds maxEntries
                boolean shouldRemove = (index >= maxEntriesCopy);

                // check expiration if still relevant
                if (!shouldRemove && (usageExpirationCopy != null)) {
                    shouldRemove = (maintenanceEntry.age.compareTo(usageExpirationCopy) > 0);
                }

                if (shouldRemove) {
                    map.remove(maintenanceEntry.key);
                }
            }
        }
    }

    Instant now() {
        return Instant.now();
    }
}
