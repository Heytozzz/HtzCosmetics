package com.heytozzz.htzcosmetics.core.manifest;

import java.util.EnumSet;
import java.util.Set;

/**
 * Equip slot. A player can wear at most one cosmetic per category.
 *
 * <p>Each category lists the body parts its pieces may follow. Multi-part cosmetics (clothing) attach each top-level
 * group of the model to its own body part; the first listed part is the default for groups that name none.
 */
public enum Category {
    // Listed top to bottom of the body: this is also the order of the wardrobe's category list.
    HEAD(Anchor.HEAD),
    NECKLACE(Anchor.BODY, Anchor.HEAD),
    SHOULDER(Anchor.BODY, Anchor.LEFT_ARM, Anchor.RIGHT_ARM),
    BACK(Anchor.BODY),
    SHIRT(Anchor.BODY, Anchor.LEFT_ARM, Anchor.RIGHT_ARM),
    COAT(Anchor.BODY, Anchor.LEFT_ARM, Anchor.RIGHT_ARM, Anchor.LEFT_LEG, Anchor.RIGHT_LEG),
    WAIST(Anchor.BODY, Anchor.LEFT_LEG, Anchor.RIGHT_LEG),
    PANTS(Anchor.BODY, Anchor.LEFT_LEG, Anchor.RIGHT_LEG),
    SHOES(Anchor.RIGHT_LEG, Anchor.LEFT_LEG),
    /** Glow, outline and bloom around the whole body; no model needed. */
    AURA(Anchor.BODY);

    /** Categories understood by mod builds that only know content format 1. */
    private static final Set<Category> FORMAT_1 = EnumSet.of(HEAD, BACK, SHOULDER, WAIST);

    private final Anchor defaultAnchor;
    private final Set<Anchor> allowedAnchors;

    Category(Anchor defaultAnchor, Anchor... more) {
        this.defaultAnchor = defaultAnchor;
        this.allowedAnchors = EnumSet.of(defaultAnchor, more);
    }

    public boolean allows(Anchor anchor) {
        return allowedAnchors.contains(anchor);
    }

    /** Body part used when the cosmetic (or one of its groups) names none. */
    public Anchor defaultAnchor() {
        return defaultAnchor;
    }

    /** True if older mod builds (content format 1) already know this category. */
    public boolean inFormat1() {
        return FORMAT_1.contains(this);
    }

    /** True if the cosmetic is an effect around the body and needs no model or texture. */
    public boolean isAura() {
        return this == AURA;
    }
}
