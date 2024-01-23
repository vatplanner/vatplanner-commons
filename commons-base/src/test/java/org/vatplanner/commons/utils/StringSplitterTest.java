package org.vatplanner.commons.utils;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.INTEGER;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StringSplitterTest {
    static Stream<Arguments> provide_spaceSeparatedFields_expectedResults() {
        return Stream.of(
            Arguments.of(
                "Simple split - for single spaces.",
                Arrays.asList("Simple", "split", "-", "for", "single", "spaces."),
                Arrays.asList(0, 7, 13, 15, 19, 26)
            ),
            Arguments.of(
                " space in front",
                Arrays.asList("space", "in", "front"),
                Arrays.asList(1, 7, 10)
            ),
            Arguments.of(
                "  spaces in front",
                Arrays.asList("spaces", "in", "front"),
                Arrays.asList(2, 9, 12)
            ),
            Arguments.of(
                "space at end ",
                Arrays.asList("space", "at", "end"),
                Arrays.asList(0, 6, 9)
            ),
            Arguments.of(
                "spaces at end  ",
                Arrays.asList("spaces", "at", "end"),
                Arrays.asList(0, 7, 10)
            ),
            Arguments.of(
                "    random   spaces     everywhere    ",
                Arrays.asList("random", "spaces", "everywhere"),
                Arrays.asList(4, 13, 24)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("provide_spaceSeparatedFields_expectedResults")
    void testSplitOnSpace_spaceSeparatedFields_returnsExpectedResult(String original, List<String> expectedFields, List<Integer> expectedStartPositions) {
        // precondition
        assertThat(expectedFields).describedAs("test parameters must have same size")
                                  .hasSameSizeAs(expectedStartPositions);

        // arrange
        int expectedSize = expectedFields.size();

        // act
        StringSplitter.Result result = StringSplitter.splitOnSpace(original);

        // assert
        int actualSize = result.size();
        List<String> actualFields = new ArrayList<>();
        List<Integer> actualExpectedStartPositions = new ArrayList<>();
        for (int i = 0; i < actualSize; i++) {
            actualFields.add(result.getField(i));
            actualExpectedStartPositions.add(result.getStartPosition(i));
        }

        assertAll(
            () -> assertThat(result).describedAs("original")
                                    .extracting(StringSplitter.Result::getOriginal)
                                    .isEqualTo(original),

            () -> assertThat(actualSize).describedAs("size")
                                        .isEqualTo(expectedSize),

            () -> assertThat(actualFields).describedAs("fields")
                                          .containsExactlyElementsOf(expectedFields),

            () -> assertThat(actualExpectedStartPositions).describedAs("start positions")
                                                          .containsExactlyElementsOf(expectedStartPositions)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        " ",
        "  "
    })
    void testSplitOnSpace_blank_returnsExpectedResult(String original) {
        // arrange (nothing to do)

        // act
        StringSplitter.Result result = StringSplitter.splitOnSpace(original);

        // assert
        assertAll(
            () -> assertThat(result).describedAs("original")
                                    .extracting(StringSplitter.Result::getOriginal)
                                    .isEqualTo(original),

            () -> assertThat(result).describedAs("size")
                                    .extracting(StringSplitter.Result::size, as(INTEGER))
                                    .isZero()
        );
    }

    @Nested
    class Result {
        @CsvSource({
            "'This is a test',     2, 'a test'",
            "'This is a test',     3, 'test'",
            "'This is a test  ',   3, 'test  '",
            "'  This is a test  ', 3, 'test  '",
            "'  This is a test  ', 0, 'This is a test  '",

            "'This is a test',    -1, 'test'",
            "'This is a test',    -2, 'a test'",
            "'This is a test',    -4, 'This is a test'",
            "'  This is a test  ',-1, 'test  '",
            "'  This is a test  ',-4, 'This is a test  '",

            "'Keep   everything    how it    was   originally', 2, 'how it    was   originally'",
            "'Keep   everything    how it    was   originally', 0, 'Keep   everything    how it    was   originally'",
            "'Keep   everything    how it    was   originally',-3, 'it    was   originally'",
        })
        @ParameterizedTest
        void testGetOriginalFromField_fieldExists_returnsFullStringFromStartOfField(String original, int fieldIndex, String expectedResult) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            String result = splitterResult.getOriginalFromField(fieldIndex);

            // assert
            assertThat(result).isEqualTo(expectedResult);
        }

        @CsvSource({
            "'test', 1",
            "'test', -2",
            "'', 0",
            "'',-1",
            "' ', 0",
            "' ',-1",
        })
        @ParameterizedTest
        void testGetOriginalFromField_invalidIndex_throwsIllegalArgumentException(String original, int fieldIndex) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            ThrowingCallable action = () -> splitterResult.getOriginalFromField(fieldIndex);

            // assert
            assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @CsvSource({
            "'This is a test.',  0,-1, 'This is a'",
            "'This is a test.',  1,-1, 'is a'",
            "'This is a test.',  2,-1, 'a'",
            "'This is a test.', -4,-1, 'This is a'",
            "'This is a test.', -2,-1, 'a'",
            "'This is a test.', -3,-1, 'is a'",

            "' This is a test.', 0,-1, 'This is a'",

            "'  Several  spaces    in  between.  ', 1,-1, 'spaces    in'",
            "'  Several  spaces    in  between.  ',-4, 2, 'Several  spaces'",
        })
        void testGetOriginalBetweenFields_fieldExists_returnsFullStringBetweenFields(String original, int startIndex, int endIndex, String expectedResult) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            String result = splitterResult.getOriginalBetweenFields(startIndex, endIndex);

            // assert
            assertThat(result).isEqualTo(expectedResult);
        }

        @CsvSource({
            "'test', 1, 2",
            "'test', 0, 2",
            "' ', 0, 1",
            "' ', 0,-1",

            // start = end is invalid because end is exclusive
            "'test', 0, 0",
            "'test',-1,-1",
            "'This is a test.', 1, 1",

            "'This is a test.', 1, 0", // start/end reversed
            "'This is a test.', 2, 1", // start/end reversed
            "'This is a test.',-1,-2", // start/end reversed
            "'This is a test.',-5, 1", // start out of bounds
            "'This is a test.', 0,-5", // end reversed and out of bounds
        })
        @ParameterizedTest
        void testGetOriginalBetweenFields_invalidIndex_throwsIllegalArgumentException(String original, int startIndex, int endIndex) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            ThrowingCallable action = () -> splitterResult.getOriginalBetweenFields(startIndex, endIndex);

            // assert
            assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
        }

        @CsvSource({
            "' This  is a test ', -4, 'This'",
            "' This  is a test ', -3, 'is'",
            "' This  is a test ', -2, 'a'",
            "' This  is a test ', -1, 'test'",
        })
        @ParameterizedTest
        void testGetField_validNegative_returnsFieldAddressedFromEnd(String original, int fieldIndex, String expectedResult) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            String result = splitterResult.getField(fieldIndex);

            // assert
            assertThat(result).isEqualTo(expectedResult);
        }

        @CsvSource({
            "'test', 1",
            "'test', -2",
            "'', 0",
            "' ', 0",
            "'', -1",
            "' ', -1",
        })
        @ParameterizedTest
        void testGetField_invalidIndex_throwsIllegalArgumentException(String original, int fieldIndex) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            ThrowingCallable action = () -> splitterResult.getField(fieldIndex);

            // assert
            assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
        }

        @CsvSource({
            "' This is a test. ', -4, 1",
            "' This is a test. ', -3, 6",
            "' This is a test. ', -2, 9",
            "' This is a test. ', -1, 11",
        })
        @ParameterizedTest
        void testGetStartPosition_validNegative_returnsPositionAddressedFromEnd(String original, int fieldIndex, int expectedResult) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            int result = splitterResult.getStartPosition(fieldIndex);

            // assert
            assertThat(result).isEqualTo(expectedResult);
        }

        @CsvSource({
            "'test', 1",
            "'test', -2",
            "'', 0",
            "' ', 0",
            "'', -1",
            "' ', -1",
        })
        @ParameterizedTest
        void testGetStartPosition_invalidIndex_throwsIllegalArgumentException(String original, int fieldIndex) {
            // arrange
            StringSplitter.Result splitterResult = StringSplitter.splitOnSpace(original);

            // act
            ThrowingCallable action = () -> splitterResult.getStartPosition(fieldIndex);

            // assert
            assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
        }
    }
}