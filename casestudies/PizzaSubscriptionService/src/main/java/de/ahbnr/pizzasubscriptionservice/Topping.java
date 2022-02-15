package de.ahbnr.pizzasubscriptionservice;

public enum Topping {
    Chicken(),
    Ham(),
    HotSpicedBeef(),
    PeperoniSausage(),

    Gorgonzola(),
    Mozzarella(),
    GoatsCheese(),
    Parmesan(),
    FourCheeses(),

    Anchovies(),
    MixedSeafood(),
    Prawns(),

    Mushroom(true),
    Onion(true),
    Garlic(true),
    Olive(true),
    Pepper(true),
    Caper(true),
    Tomato(true),
    Spinach(true);

    final boolean isVegan;

    Topping() {
        this(false);
    }

    Topping(boolean isVegan) {
        this.isVegan = isVegan;
    }
}
