package de.ahbnr.pizzasubscriptionservice;

public class Main {
    public static void main(String[] args) {
        final var pizzaGen = new PizzaGenerator();

        final var myUser = new UserProfile();
        myUser.addRestriction(DietRestriction.Vegan);

        pizzaGen.generatePizza(myUser);
        pizzaGen.generatePizza(myUser);
        pizzaGen.generatePizza(myUser);

        myUser.removeRestriction(DietRestriction.Vegan);

        pizzaGen.generatePizza(myUser);
    }
}
