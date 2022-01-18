package examples.tests;

class Optional<T> {
    private T value;

    private Optional(T value) {
        this.value = value;
    }

    public static <U> Optional<U> Some(U value) {
        return new Optional<U>(value);
    }

    public static <U> Optional<U> None() {
        return new Optional<U>(null);
    }
}

public class ConditionalBreakpointsTest {
    private static void testOwlDlSatisfiableCondition() {
        var optional = Optional.Some(42);

        System.out.println("Test: OwlDlSatisfiableCondition");
    }

    private static void testOwlDlUnsatisfiableCondition() {
        var optional = Optional.None();

        System.out.println("Test: OwlDlUnsatisfiableCondition");
    }

    private static void testSparqlAnyCondition() {
        var optional = Optional.Some(42);

        System.out.println("Test: SparqlAnyCondition");
    }

    private static void testSparqlNoneCondition() {
        var optional = Optional.None();

        System.out.println("Test: SparqlNoneCondition");
    }

    public static void main(String[] args) {
        testOwlDlSatisfiableCondition();
        testOwlDlUnsatisfiableCondition();
        testSparqlAnyCondition();
        testSparqlNoneCondition();
    }
}