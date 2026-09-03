package com.example.matching.service.system;

import com.example.matching.entity.system.AbilityTag;

/** Semantic levels used by the canonical capability taxonomy. */
public final class AbilityTagHierarchy {
    public static final int ROOT_LEVEL = 0;
    public static final int DOMAIN_LEVEL = 1;
    public static final int ASSESSABLE_LEVEL = 2;

    private AbilityTagHierarchy() {
    }

    public static boolean isAssessable(AbilityTag tag) {
        return tag != null && tag.getStatus() != null && tag.getStatus() == 1
                && Integer.valueOf(ASSESSABLE_LEVEL).equals(tag.getTagLevel());
    }

    public static boolean isEnabledDomain(AbilityTag tag) {
        return tag != null && tag.getStatus() != null && tag.getStatus() == 1
                && Integer.valueOf(DOMAIN_LEVEL).equals(tag.getTagLevel());
    }
}
