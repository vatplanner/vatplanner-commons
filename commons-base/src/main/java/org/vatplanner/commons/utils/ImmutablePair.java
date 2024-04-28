package org.vatplanner.commons.utils;

import java.util.Objects;

public class ImmutablePair<L, R> {
    private final L left;
    private final R right;

    public ImmutablePair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeftValue() {
        return left;
    }

    public R getRightValue() {
        return right;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ImmutablePair)) {
            return false;
        }

        ImmutablePair<?, ?> other = (ImmutablePair<?, ?>) obj;

        return Objects.equals(this.left, other.left) && Objects.equals(this.right, other.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return "ImmutablePair(" + left + ", " + right + ")";
    }
}
