package de.ahbnr.pizzasubscriptionservice;

public class Main {
    public static void main(String[] args) {
        final var pizzaGen = new PizzaGenerator();

        final var myUser = new UserProfile();
        myUser.isVegan = true;

        pizzaGen.generatePizza(myUser);
        pizzaGen.generatePizza(myUser);
        pizzaGen.generatePizza(myUser);

        myUser.isVegan = false;

        pizzaGen.generatePizza(myUser);
    }
}
