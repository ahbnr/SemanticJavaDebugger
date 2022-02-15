package de.ahbnr.pizzasubscriptionservice;

import java.util.ArrayList;
import java.util.List;

class Pizza {
    Base base;
    List<Topping> toppings;

    public Pizza(Base base, List<Topping> toppings) {
        this.base = base;
        this.toppings = toppings;
    }

    @Override
    public String toString() {
        return "Pizza{" + "base=" + base + ", toppings=" + toppings + '}';
    }

    public Pizza deepCopy() {
        return new Pizza(
                this.base,
                new ArrayList<>(this.toppings)
        );
    }
}
