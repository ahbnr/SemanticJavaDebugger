package examples.tests;

import java.util.Iterator;
import java.lang.Override;

class BMTMyClass {

}

public class BridgeMethodTest implements Iterator<BMTMyClass> {
    @Override
    public boolean hasNext() {
        return true;
    }

    // At runtime, a bridge method `Object next()` will be generated.
    // See also http://www.angelikalanger.com/GenericsFAQ/FAQSections/TechnicalDetails.html#FAQ102
    @Override
    public BMTMyClass next() {
        return new BMTMyClass();
    }

    public static void main(String[] args) {
        System.out.println("BridgeMethodTest");
    }
}