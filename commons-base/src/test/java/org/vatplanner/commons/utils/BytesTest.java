package org.vatplanner.commons.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BytesTest {
    static Stream<Arguments> provide_validHexStrings_expectedBytes() {
        return Stream.of(
            Arguments.of("00", new byte[]{0}),
            Arguments.of("7f", new byte[]{127}),
            Arguments.of("80", new byte[]{-128}),
            Arguments.of("ff", new byte[]{-1}),
            Arguments.of("", new byte[0]),
            Arguments.of("a0b102ff00", new byte[]{-96, -79, 2, -1, 0})
        );
    }

    @ParameterizedTest
    @MethodSource("provide_validHexStrings_expectedBytes")
    void testParseHexString_validInput_returnsExpectedResult(String input, byte[] expectedResult) {
        // arrange (nothing to do)

        // act
        byte[] result = Bytes.parseHexString(input);

        // assert
        assertThat(result).containsExactly(expectedResult);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0",
        "000",
        " 00",
        "00 ",
        "  ",
        "g0",
        "0g",
        "-1"
    })
    void testParseHexString_invalidInput_throwsIllegalArgumentException(String input) {
        // arrange (nothing to do)

        // act
        ThrowableAssert.ThrowingCallable action = () -> Bytes.parseHexString(input);

        // assert
        assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class);
    }
}
