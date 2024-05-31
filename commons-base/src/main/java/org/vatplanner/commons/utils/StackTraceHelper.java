package org.vatplanner.commons.utils;

import java.util.Arrays;

/**
 * Helper methods to work with Java stack traces.
 */
public class StackTraceHelper {
    private StackTraceHelper() {
        // utility class; hide constructor
    }

    /**
     * Returns the current thread's stack trace, omitting this call.
     *
     * @return current thread's stack trace without this call
     */
    public static StackTraceElement[] getCurrentThreadStackTraceWithoutDumpCall() {
        return getCurrentThreadStackTrace(3);
    }

    /**
     * Returns the current thread's stack trace, omitting this call and the given number of calls before that.
     *
     * @param numberOfOmittedCalls number of additional calls to omit from top of stack trace
     * @return current thread's stack trace without this call and the given number of calls before that
     */
    public static StackTraceElement[] getCurrentThreadStackTraceWithoutDumpCall(int numberOfOmittedCalls) {
        if (numberOfOmittedCalls < 0) {
            throw new IllegalArgumentException("number of calls to be omitted must be positive");
        }
        return getCurrentThreadStackTrace(3 + numberOfOmittedCalls);
    }

    private static StackTraceElement[] getCurrentThreadStackTrace(int numberOfOmittedCalls) {
        StackTraceElement[] fullStackTrace = Thread.currentThread().getStackTrace();
        int from = numberOfOmittedCalls + 1;
        if (from >= fullStackTrace.length) {
            return new StackTraceElement[0];
        }
        return Arrays.copyOfRange(fullStackTrace, from, fullStackTrace.length);
    }

    /**
     * Formats the current thread's stack trace, omitting this call.
     *
     * @return current thread's stack trace without this call
     */
    public static String formatCurrentThreadStackTraceWithoutDumpCall() {
        return format(getCurrentThreadStackTrace(3));
    }

    /**
     * Formats the current thread's stack trace, omitting this call and the given number of calls before that.
     *
     * @param numberOfOmittedCalls number of additional calls to omit from top of stack trace
     * @return current thread's stack trace without this call and the given number of calls before that
     */
    public static String formatCurrentThreadStackTraceWithoutDumpCall(int numberOfOmittedCalls) {
        if (numberOfOmittedCalls < 0) {
            throw new IllegalArgumentException("number of calls to be omitted must be positive");
        }
        return format(getCurrentThreadStackTrace(3 + numberOfOmittedCalls));
    }

    /**
     * Formats the given stack trace for human-readable output.
     * <p>
     * The format deviates slightly from Exception stack traces.
     * </p>
     *
     * @param stackTrace stack trace to format
     * @return formatted stack trace
     */
    public static String format(StackTraceElement[] stackTrace) {
        if (stackTrace == null) {
            return "<null>";
        } else if (stackTrace.length == 0) {
            return "<empty>";
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        boolean first = true;
        for (StackTraceElement elem : stackTrace) {
            if (first) {
                first = false;
            } else {
                sb.append("\n");
            }
            sb.append("#");
            sb.append(i++);
            sb.append(" ");
            sb.append(elem.getClassName());
            sb.append(".");
            sb.append(elem.getMethodName());
            sb.append("(");
            sb.append(elem.getFileName());
            sb.append(":");
            sb.append(elem.getLineNumber());
            sb.append(")");
        }

        return sb.toString();
    }
}
