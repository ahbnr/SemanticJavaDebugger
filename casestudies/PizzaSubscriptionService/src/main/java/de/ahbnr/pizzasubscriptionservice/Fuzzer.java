package de.ahbnr.pizzasubscriptionservice;

import java.util.LinkedList;
import java.util.Random;

public class Fuzzer {
    public static void main(String[] args) {
        var random = new Random();
        var pizzaGen = new PizzaGenerator();
        var trace = new LinkedList<UserProfile>();

        int maxUsers = 2;
        int numRounds = 10;

        for (int i = 0; i < maxUsers; ++i) {
            var user = new UserProfile();

            for (int round = 0; round < numRounds; ++round) {
                if (random.nextBoolean())
                    user.isVegan = !user.isVegan;

                trace.add(user.deepCopy());
                pizzaGen.generatePizza(user);
            }
        }
    }
}
