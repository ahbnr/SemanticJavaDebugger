package de.ahbnr.pizzasubscriptionservice;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PizzaGenerator {
    public void generatePizza(UserProfile user) {
        final var randomGen = new Random();

        final var allBases = Base.values();
        final Base base = allBases[randomGen.nextInt(allBases.length)];

        final var allToppings = Arrays.asList(Topping.values());
        Collections.shuffle(allToppings);

        var usableToppings = allToppings
                .stream()
                .filter(topping -> topping.satisfiesRestrictions(user.getRestrictions()));

        final List<Topping> toppings;
        if (!user.getRestrictions().contains(DietRestriction.Vegan)) {
            final var recentlyUsedToppings = user
                    .getRecentlyDeliveredPizzas().stream()
                    .flatMap(pizza -> pizza.getToppings().stream())
                    .collect(Collectors.toSet());
            final var nonRecentlyUsedToppings = usableToppings
                    .filter(topping -> !recentlyUsedToppings.contains(topping))
                    .collect(Collectors.toList());

            final Stream<Topping> minimumToppings;
            {
                final var toppingPartition = nonRecentlyUsedToppings.stream().collect(Collectors.partitioningBy(topping -> topping.satisfiesRestriction(DietRestriction.Vegan)));
                final var veganToppings = toppingPartition.get(true);
                final var nonVeganToppings = toppingPartition.get(false);

                minimumToppings = Stream.concat(
                        nonVeganToppings.stream().limit(1),
                        veganToppings.stream().limit(1)
                );
            }

            toppings = Stream.concat(minimumToppings, nonRecentlyUsedToppings.stream()).distinct().limit(3).collect(Collectors.toList());
        } else {
            toppings = usableToppings.limit(3).collect(Collectors.toList());
        }

        final var pizza = new Pizza(base, toppings);

        user.deliverPizza(pizza);
    }
}
