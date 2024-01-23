package org.vatplanner.commons.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.vatplanner.commons.exceptions.OutOfRange;

/**
 * Helper methods to split a string into fields.
 * <p>
 * Implementations are provided for (hard-coded) specific use-cases to increase performance over complex regular
 * expressions. Compared to simple/fast {@link String#split(String)} the {@link Result} offers additional functionality
 * such as accessing earlier fields split on spaces but a space-containing field at the end without having to rejoin it
 * or breaking intentional white-spaces contained within.
 * </p>
 */
public class StringSplitter {
    public static class Result {
        private final String original;
        private final String[] fields;
        private final Integer[] startPositions;

        private Result(String original, String[] fields, Integer[] startPositions) {
            this.original = original;
            this.fields = fields;
            this.startPositions = startPositions;
        }

        /**
         * Indicates the number of available fields split from original input.
         *
         * @return number of split fields
         */
        public int size() {
            return fields.length;
        }

        /**
         * Returns the original unmodified input.
         *
         * @return original unmodified input
         */
        public String getOriginal() {
            return original;
        }

        /**
         * Returns the content of a single field.
         *
         * @param index index of field to retrieve; starting from 0, negative index counts from last field
         * @return field content according to split method
         * @throws IllegalArgumentException if the index is invalid or the field does not exist
         */
        public String getField(int index) {
            if (index < 0) {
                index = fields.length + index;
            }

            verifyValidIndex(index);
            return fields[index];
        }

        /**
         * Returns the position of the first character of the specified split field on the original input string.
         *
         * @param index index of field to retrieve; starting from 0, negative index counts from last field
         * @return position of first character of the field
         * @throws IllegalArgumentException if the index is invalid or the field does not exist
         */
        public int getStartPosition(int index) {
            if (index < 0) {
                index = fields.length + index;
            }

            verifyValidIndex(index);
            return startPositions[index];
        }

        /**
         * Returns the position of the last character of the specified split field on the original input string.
         *
         * @param index index of field to retrieve; starting from 0, negative index counts from last field
         * @return position of last character of the field
         * @throws IllegalArgumentException if the index is invalid or the field does not exist
         */
        private int getEndPosition(int index) {
            if (index < 0) {
                index = fields.length + index;
            }

            verifyValidIndex(index);
            return startPositions[index] + fields[index].length();
        }

        /**
         * Returns the content of a single field or an empty {@link Optional} if the field does not exist.
         *
         * @param index index of field to retrieve; starting from 0
         * @return field content according to split method; empty if the field does not exist
         * @throws IllegalArgumentException if the index is not positive or zero
         */
        public Optional<String> getOptionalField(int index) {
            verifyPositiveIndex(index);
            if (fields.length <= index) {
                return Optional.empty();
            }

            return Optional.of(fields[index]);
        }

        /**
         * Returns the position of the first character of the specified split field on the original input string or an
         * empty {@link Optional} if the field does not exist.
         *
         * @param index index of field to retrieve; starting from 0
         * @return position of first character of the field; empty if the field does not exist
         * @throws IllegalArgumentException if the index is not positive or zero
         */
        public OptionalInt getOptionalStartPosition(int index) {
            verifyPositiveIndex(index);
            if (startPositions.length <= index) {
                return OptionalInt.empty();
            }

            return OptionalInt.of(startPositions[index]);
        }

        /**
         * Returns the part of the original input string starting from the given field, essentially returning
         * "the field and everything that follows" (incl. any split markers).
         *
         * @param index index of field to start extracting original content from; index starting from 0, negative index counts from last field
         * @return original string starting from given field, including any split markers
         * @throws IllegalArgumentException if the index is invalid or the field does not exist
         */
        public String getOriginalFromField(int index) {
            return original.substring(getStartPosition(index));
        }

        /**
         * Returns the part of the original input string starting from a given field up to another field (excluding it),
         * essentially returning "the field and everything that follows until another field" (incl. any split markers
         * between but not before the end field).
         *
         * @param startIndex index of field to start extracting original content from; index starting from 0
         * @param endIndex   index of field to stop extraction before (exclusive); index starting from 0, negative index counts from last field
         * @return original string between specified fields, including any split markers between fields (not trailing)
         * @throws IllegalArgumentException if an index is invalid or the fields do not exist
         */
        public String getOriginalBetweenFields(int startIndex, int endIndex) {
            if (startIndex == endIndex) {
                throw new IllegalArgumentException("end index can not be equal to start index; both are " + startIndex);
            }

            if (startIndex < 0) {
                startIndex = fields.length + startIndex;
            }

            if (endIndex < 0) {
                endIndex = fields.length + endIndex;
            }

            if (startIndex >= endIndex) {
                throw new IllegalArgumentException("indexes are reversed or equal (start=" + startIndex + ", end=" + endIndex + ")");
            }

            OutOfRange.throwIfNotWithinIncluding("start index", startIndex, 0, fields.length - 1);
            OutOfRange.throwIfNotWithinIncluding("end index", startIndex, 0, fields.length - 1);

            int startPosition = getStartPosition(startIndex);
            int endPosition = getEndPosition(endIndex - 1);

            return original.substring(startPosition, endPosition);
        }

        private void verifyValidIndex(int index) {
            verifyPositiveIndex(index);
            OutOfRange.throwIfNotWithinIncluding("index", index, 0, fields.length - 1);
        }

        private void verifyPositiveIndex(int index) {
            if (index < 0) {
                throw new IllegalArgumentException("index must be positive but was " + index);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Splitter.Result(");

            for (int i = 0; i < fields.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append(i);
                sb.append("@");
                sb.append(startPositions[i]);
                sb.append(":\"");
                sb.append(fields[i]);
                sb.append("\"");
            }

            sb.append(")");

            return sb.toString();
        }
    }

    /**
     * Splits the given string on any number of space characters.
     *
     * @param original string to split on spaces
     * @return fields split on spaces
     */
    public static Result splitOnSpace(String original) {
        List<String> fields = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();

        char[] chars = original.toCharArray();
        boolean previousWasSpace = true;
        int fieldStart = 0;
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            boolean currentIsSpace = (ch == ' ');

            if (currentIsSpace) {
                if (!previousWasSpace) {
                    fields.add(original.substring(fieldStart, i));
                    positions.add(fieldStart);
                    previousWasSpace = true;
                }
            } else if (previousWasSpace) {
                fieldStart = i;
                previousWasSpace = false;
            }
        }

        if (!previousWasSpace) {
            fields.add(original.substring(fieldStart));
            positions.add(fieldStart);
        }

        return new Result(original, fields.toArray(new String[0]), positions.toArray(new Integer[0]));
    }
}
