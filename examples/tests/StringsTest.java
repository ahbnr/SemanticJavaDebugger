package examples.tests;

public class StringsTest {
    public static void main(String[] args) {
        final var simpleString = "Simple String";
        final var stringWithBadSymbols = "< & \"";
        final var unicodeString = "InterestingPizza ≡ Pizza ⊓ (≥3)hasTopping: ⊤";
        final var binaryString = new String(new byte[]{0});

        System.out.println("StringsTest: Test Point");
    }
}