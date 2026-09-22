package com.edwinkarolczyk.edhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure regression for recurring household rotation rules. */
public final class RotationRulesSmoke {
    public static void main(String[] args) {
        List<Long> people = Arrays.asList(10L, 20L, 30L);
        check(RotationRules.validate(Collections.emptyList(), true) == null);
        check(RotationRules.validate(people, true) == null);
        check(RotationRules.validate(people, false) != null);
        check(RotationRules.validate(Collections.singletonList(10L), true) != null);
        check(RotationRules.validate(Arrays.asList(10L, 10L), true) != null);
        check(RotationRules.next(people, 10L) == 20L);
        check(RotationRules.next(people, 20L) == 30L);
        check(RotationRules.next(people, 30L) == 10L);
        check(RotationRules.next(people, null) == 10L);
        check(RotationRules.next(people, 999L) == 10L);
        check(RotationRules.moved(people, 0, 2).equals(
            Arrays.asList(20L, 30L, 10L)));
        check(RotationRules.moved(people, 2, 0).equals(
            Arrays.asList(30L, 10L, 20L)));
        try {
            RotationRules.moved(people, -1, 0);
            throw new AssertionError("Invalid move accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Recurring assignee rotation, wrap-around and ordering: PASS");
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Rotation regression failed");
    }
}
