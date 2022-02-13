package de.ahbnr.pizzasubscriptionservice;

import java.util.ArrayList;
import java.util.List;

public class Pizza implements DeepCopyable<Pizza> {
    private Base base;
    private List<Topping> toppings;

    public List<Topping> getToppings() {
        return toppings;
    }

    public Pizza(Base base, List<Topping> toppings) {
        this.base = base;
        this.toppings = toppings;
    }

    @Override
    public String toString() {
        return "Pizza{" + "base=" + base + ", toppings=" + toppings + '}';
    }

    @Override
    public Pizza deepCopy() {
        return new Pizza(
                this.base,
                new ArrayList<>(this.toppings)
        );
    }
}
