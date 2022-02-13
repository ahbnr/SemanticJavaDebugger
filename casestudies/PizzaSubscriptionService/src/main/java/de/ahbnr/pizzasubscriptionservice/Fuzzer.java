package de.ahbnr.pizzasubscriptionservice;

import java.util.LinkedList;
import java.util.Random;

public class Fuzzer {
    private static final Random random = new Random();
    private static final PizzaGenerator pizzaGen = new PizzaGenerator();

    private static final LinkedList<UserProfile> trace = new LinkedList<>();

    private static UserProfile genUser() {
        var newUser = new UserProfile();

        if (random.nextBoolean()) {
            newUser.addRestriction(DietRestriction.Vegan);
        }

        return newUser;
    }

    private static void oneRound(UserProfile user) {
        if (random.nextBoolean()) {
            if (user.getRestrictions().contains(DietRestriction.Vegan))
                user.removeRestriction(DietRestriction.Vegan);

            else user.addRestriction(DietRestriction.Vegan);
        }

        trace.add(user.deepCopy());
        pizzaGen.generatePizza(user);
    }

    public static void main(String[] args) {
        final var maxUsers = 2;

        for (int i = 0; i < maxUsers; ++i) {
            final var user = genUser();
            final var numRounds = random.nextInt(10);

            for (int round = 0; round < numRounds; ++round) {
                oneRound(user);
            }
        }
    }
}
