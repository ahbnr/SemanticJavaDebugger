package de.ahbnr.pizzasubscriptionservice;

import java.util.*;

class UserProfile {
    boolean isVegan;
    LinkedList<Pizza> recentlyDelivered = new LinkedList<>();

    void deliverPizza(Pizza pizza) {
        System.out.println("Delivered pizza: " + pizza.toString());

        if (recentlyDelivered.size() >= 3) {
            recentlyDelivered.removeLast();
        }

        recentlyDelivered.addFirst(pizza);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "isVegan=" + isVegan +
                ", recentlyDelivered=" + recentlyDelivered +
                '}';
    }

    public UserProfile deepCopy() {
        final var copy = new UserProfile();

        copy.isVegan = this.isVegan;
        copy.recentlyDelivered = new LinkedList<>(this.recentlyDelivered);

        return copy;
    }
}
