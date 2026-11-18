package com.heytozzz.htzcosmetics.core.manifest;

import java.util.EnumSet;
import java.util.Set;

/** Equip slot. A player can wear at most one cosmetic per category. */
public enum Category {
    HEAD(EnumSet.of(Anchor.HEAD)),
    BACK(EnumSet.of(Anchor.BODY)),
    SHOULDER(EnumSet.of(Anchor.BODY, Anchor.LEFT_ARM, Anchor.RIGHT_ARM)),
    WAIST(EnumSet.of(Anchor.BODY));

    private final Set<Anchor> allowedAnchors;

    Category(Set<Anchor> allowedAnchors) {
        this.allowedAnchors = allowedAnchors;
    }

    public boolean allows(Anchor anchor) {
        return allowedAnchors.contains(anchor);
    }
}
