package de.ahbnr.pizzasubscriptionservice;

import java.util.*;

public class UserProfile implements DeepCopyable<UserProfile> {
    private Set<DietRestriction> restrictions = new HashSet<>();
    private LinkedList<Pizza> recentlyDeliveredPizzas = new LinkedList<>();

    public List<Pizza> getRecentlyDeliveredPizzas() {
        return recentlyDeliveredPizzas;
    }

    public Set<DietRestriction> getRestrictions() {
        return this.restrictions;
    }

    public void addRestriction(DietRestriction restriction) {
        restrictions.add(restriction);
    }

    public void removeRestriction(DietRestriction restriction) {
        restrictions.remove(restriction);
    }

    public void deliverPizza(Pizza pizza) {
        System.out.println("Delivered pizza: " + pizza.toString());

        if (recentlyDeliveredPizzas.size() >= 3) {
            recentlyDeliveredPizzas.removeLast();
        }

        recentlyDeliveredPizzas.addFirst(pizza);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "restrictions=" + restrictions +
                ", recentlyDeliveredPizzas=" + recentlyDeliveredPizzas +
                '}';
    }

    @Override
    public UserProfile deepCopy() {
        final var copy = new UserProfile();

        copy.restrictions = new HashSet<>(this.restrictions);
        copy.recentlyDeliveredPizzas = new LinkedList<>(this.recentlyDeliveredPizzas);

        return copy;
    }
}
