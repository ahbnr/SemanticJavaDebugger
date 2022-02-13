package de.ahbnr.pizzasubscriptionservice;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum Topping {
    Chicken(),
    Gorgonzola(),
    Prawns(),
    Mushroom(DietRestriction.Vegan),
    Onion(DietRestriction.Vegan),
    Spinach(DietRestriction.Vegan);

    private final Set<DietRestriction> satisfiedRestrictions;

    Topping(DietRestriction... satisfiedRestrictions) {
        this.satisfiedRestrictions = new HashSet<>();
        Collections.addAll(this.satisfiedRestrictions, satisfiedRestrictions);
    }

    public Boolean satisfiesRestriction(DietRestriction restriction) {
        return this.satisfiedRestrictions.contains(restriction);
    }

    public Boolean satisfiesRestrictions(Collection<DietRestriction> restrictions) {
        return this.satisfiedRestrictions.containsAll(restrictions);
    }
}
