package de.ahbnr.pizzasubscriptionservice;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PizzaGenerator {
    public boolean useReducedHistoryForVeganUsers = true;

    private List<Topping> getNotRecentlyUsedToppings(UserProfile user, List<Topping> allToppings) {
        final var recentlyUsedToppings = user
                .recentlyDelivered
                .stream()
                .limit(user.isVegan && useReducedHistoryForVeganUsers ? 1 : 3)
                .flatMap(pizza -> pizza.toppings.stream())
                .collect(Collectors.toSet());

        return allToppings
                .stream()
                .filter(topping -> !recentlyUsedToppings.contains(topping))
                .collect(Collectors.toList());
    }

    public void generatePizza(UserProfile user) {
        final var randomGen = new Random();

        final var allBases = Base.values();
        final Base base = allBases[randomGen.nextInt(allBases.length)];

        final var allToppings = Arrays.asList(Topping.values());
        Collections.shuffle(allToppings);

        final Stream<Topping> usableToppings;
        {
            final var notRecentlyUsedToppings = getNotRecentlyUsedToppings(user, allToppings);

            final var toppingPartition = notRecentlyUsedToppings.stream().collect(Collectors.partitioningBy(topping -> topping.isVegan));
            final var veganToppings = toppingPartition.get(true);
            final var nonVeganToppings = toppingPartition.get(false);

            if (user.isVegan) {
                usableToppings = veganToppings.stream();
            } else {
                final var minimumToppings = Stream.concat(
                        nonVeganToppings.stream().limit(1),
                        veganToppings.stream().limit(1)
                );

                usableToppings = Stream.concat(minimumToppings, notRecentlyUsedToppings.stream());
            }
        }
        final var toppings = usableToppings.distinct().limit(3).collect(Collectors.toList());

        final var pizza = new Pizza(base, toppings);

        user.deliverPizza(pizza);
    }
}
