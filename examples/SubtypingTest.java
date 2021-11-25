package examples;

class Top {
    public String topField = "topField";
}

class Mid extends Top {
    public String midField = "midField";

    public void midMethod() {

    }
}

class Bot1 extends Mid {
    public String bot1Field = "bot1Field";
}

class Bot2 extends Mid {
    public String bot2Field = "bot2Field";
}

public class SubtypingTest {
    public static void main(String[] args) {
        var top = new Top();
        var mid = new Mid();
        var bot1 = new Bot1();
        var bot2 = new Bot2();

        System.out.println("Hello Type Hierarchy!");
    }
}