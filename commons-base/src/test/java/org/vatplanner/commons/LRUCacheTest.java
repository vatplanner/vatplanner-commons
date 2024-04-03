package org.vatplanner.commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LRUCacheTest {
    private static class LRUCacheMock<K, V> extends LRUCache<K, V> {
        static final Instant REFERENCE_TIME = Instant.parse("2024-01-01T12:00:00Z");
        final AtomicReference<Instant> mockNow = new AtomicReference<>(REFERENCE_TIME);

        LRUCacheMock<K, V> atSecondsBeforeMockReferenceTime(int seconds) {
            mockNow.set(REFERENCE_TIME.minus(Duration.ofSeconds(seconds)));
            return this;
        }

        LRUCacheMock<K, V> atMockReferenceTime() {
            mockNow.set(REFERENCE_TIME);
            return this;
        }

        @Override
        Instant now() {
            return mockNow.get();
        }
    }

    @Nested
    class MapBehaviour {
        @Test
        void testGet_notInserted_returnsNull() {
            // arrange
            LRUCache<String, Integer> cache = new LRUCache<>();

            // act
            Integer result = cache.get("notPresent");

            // assert
            assertThat(result).isNull();
        }

        @Test
        void testGet_inserted_returnsLastPutValue() {
            // arrange
            LRUCache<String, Integer> cache = new LRUCache<>();
            cache.put("key", 10);
            cache.put("key", 5);

            // act
            Integer result = cache.get("key");

            // assert
            assertThat(result).isEqualTo(5);
        }

        @Test
        void testGet_removed_returnsNull() {
            // arrange
            LRUCache<String, Integer> cache = new LRUCache<>();
            cache.put("key", 5);
            cache.remove("key");

            // act
            Integer result = cache.get("key");

            // assert
            assertThat(result).isNull();
        }

        @Test
        void testGet_cleared_returnsNull() {
            // arrange
            LRUCache<String, Integer> cache = new LRUCache<>();
            cache.put("key", 5);
            cache.clear();

            // act
            Integer result = cache.get("key");

            // assert
            assertThat(result).isNull();
        }
    }

    @Nested
    class LRUBehaviour {
        @Test
        void testPut_maxEntriesExceeded_oldestEntryIsRemoved() {
            // arrange
            LRUCache<String, Integer> cache = new LRUCache<>();
            cache.setMaxEntries(3);

            cache.put("m", 200);
            cache.put("z", 1);
            cache.put("a", 500);

            // act
            cache.put("n", 100);

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("oldest entry (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("z")).describedAs("third recent entry (should be kept)")
                                                .isEqualTo(1),

                () -> assertThat(cache.get("a")).describedAs("second recent entry (should be kept)")
                                                .isEqualTo(500),

                () -> assertThat(cache.get("n")).describedAs("most recent entry (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testPut_usageExpired_expiredEntriesAreRemoved() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(32)
                 .put("m", 200);

            cache.atSecondsBeforeMockReferenceTime(27)
                 .put("z", 1);

            cache.atSecondsBeforeMockReferenceTime(3)
                 .put("a", 500);

            // act
            cache.atMockReferenceTime()
                 .put("n", 100);

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("oldest entry (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("z")).describedAs("third recent entry (should be kept)")
                                                .isEqualTo(1),

                () -> assertThat(cache.get("a")).describedAs("second recent entry (should be kept)")
                                                .isEqualTo(500),

                () -> assertThat(cache.get("n")).describedAs("most recent entry (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testPut_oldestInsertedOtherwiseExpiredEntryRecentlyUsed_allEntriesAreKept() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(32)
                 .put("m", 200);

            cache.atSecondsBeforeMockReferenceTime(27)
                 .put("z", 1);

            cache.atSecondsBeforeMockReferenceTime(3)
                 .put("a", 500);
            cache.get("m");

            // act
            cache.atMockReferenceTime()
                 .put("n", 100);

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("oldest inserted entry (recently used => should be kept)")
                                                .isEqualTo(200),

                () -> assertThat(cache.get("z")).describedAs("third recent entry (should be kept)")
                                                .isEqualTo(1),

                () -> assertThat(cache.get("a")).describedAs("second recent entry (should be kept)")
                                                .isEqualTo(500),

                () -> assertThat(cache.get("n")).describedAs("most recent entry (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testPut_minEntriesReachedWithUsageExpired_allEntriesAreKept() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setMinEntries(4);
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(130)
                 .put("m", 200);

            cache.atSecondsBeforeMockReferenceTime(70)
                 .put("z", 1);

            cache.atSecondsBeforeMockReferenceTime(10)
                 .put("a", 500);

            // act
            cache.atMockReferenceTime()
                 .put("n", 100);

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("first expired entry (should be kept)")
                                                .isEqualTo(200),

                () -> assertThat(cache.get("z")).describedAs("second expired entry (should be kept)")
                                                .isEqualTo(1),

                () -> assertThat(cache.get("a")).describedAs("second recent non-expired entry (should be kept)")
                                                .isEqualTo(500),

                () -> assertThat(cache.get("n")).describedAs("most recent entry (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testPut_minEntriesExceededWithUsageExpired_expiredMinimumNumberOfMostRecentEntriesAreKept() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setMinEntries(3);
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(130)
                 .put("m", 200);

            cache.atSecondsBeforeMockReferenceTime(70)
                 .put("z", 1);

            cache.atSecondsBeforeMockReferenceTime(10)
                 .put("a", 500);

            // act
            cache.atMockReferenceTime()
                 .put("n", 100);

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("first expired entry (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("z")).describedAs("second expired entry (should be kept)")
                                                .isEqualTo(1),

                () -> assertThat(cache.get("a")).describedAs("second recent non-expired entry (should be kept)")
                                                .isEqualTo(500),

                () -> assertThat(cache.get("n")).describedAs("most recent entry (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testMaintain_insertTimeExpiredButRecentlyUsed_entriesLastUsedAreKeptUpToTimeout() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(120)
                 .put("m", 200);
            cache.put("z", 1);
            cache.put("a", 500);
            cache.put("n", 100);

            cache.atSecondsBeforeMockReferenceTime(45)
                 .get("z");

            cache.atSecondsBeforeMockReferenceTime(25)
                 .get("m");

            cache.atSecondsBeforeMockReferenceTime(10)
                 .get("n");

            // act
            cache.atMockReferenceTime()
                 .maintain();

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("first inserted entry, recently used (should be kept)")
                                                .isEqualTo(200),

                () -> assertThat(cache.get("z")).describedAs("second inserted entry, used too long ago (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("a")).describedAs("third inserted entry, never used (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("n")).describedAs("fourth inserted entry, recently used (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testMaintain_allEntriesExpired_mostRecentMinEntriesAreStillKept() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setMinEntries(3);
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(120)
                 .put("m", 200);
            cache.put("z", 1);
            cache.put("a", 500);

            cache.atSecondsBeforeMockReferenceTime(119)
                 .put("n", 100);

            cache.atSecondsBeforeMockReferenceTime(60)
                 .get("z");
            cache.get("m");

            // act
            cache.atMockReferenceTime()
                 .maintain();

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("most recently used entry #1 (should be kept)")
                                                .isEqualTo(200),

                () -> assertThat(cache.get("z")).describedAs("most recently used entry #2 (should be kept)")
                                                .isEqualTo(1),

                () -> assertThat(cache.get("a")).describedAs("unused oldest entry (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("n")).describedAs("unused second-oldest entry (should be kept)")
                                                .isEqualTo(100)
            );
        }

        @Test
        void testMaintain_moreRecentEntriesRemovedAndRemainingExpired_mostRecentMinEntriesAreStillKept() {
            // arrange
            LRUCacheMock<String, Integer> cache = new LRUCacheMock<>();
            cache.setMinEntries(2);
            cache.setUsageExpiration(Duration.ofSeconds(30));

            cache.atSecondsBeforeMockReferenceTime(120)
                 .put("m", 200);
            cache.put("z", 1);
            cache.put("a", 500);

            cache.atSecondsBeforeMockReferenceTime(119)
                 .put("n", 100);

            cache.atSecondsBeforeMockReferenceTime(60)
                 .get("m");

            cache.atSecondsBeforeMockReferenceTime(5)
                 .get("z");
            cache.remove("z");

            // act
            cache.atMockReferenceTime()
                 .maintain();

            // assert
            assertAll(
                () -> assertThat(cache.get("m")).describedAs("most recently used remaining entry (should be kept)")
                                                .isEqualTo(200),

                () -> assertThat(cache.get("z")).describedAs("most recently used but deleted entry (was removed before maintenance)")
                                                .isNull(),

                () -> assertThat(cache.get("a")).describedAs("unused oldest entry (should be removed)")
                                                .isNull(),

                () -> assertThat(cache.get("n")).describedAs("unused second-oldest entry (should be kept)")
                                                .isEqualTo(100)
            );
        }
    }
}
