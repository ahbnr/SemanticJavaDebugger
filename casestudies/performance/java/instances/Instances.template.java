{%- for i in range(0, num_classes)  %}
class Super{{i}} { } {#- -#}
class C{{i}} extends Super{{i}} { {#- -#}
    private static C{{i}} staticRefField; {#- -#}
    private static int staticPrimitiveField; {#- -#}
 {#- -#}
    private C{{i}} refField; {#- -#}
    private int primitiveField; {#- -#}
 {#- -#}
    public void method() { {#- -#}
        C{{i}} refVar = null; {#- -#}
        int primitiveVar = 42; {#- -#}
    } {#- -#}
} {#- -#}
{%- endfor  %}

public class Instances {
    public static void main(String[] args) {
        {%- set instance_counter = namespace(value=0)  %}
        {%- set expected_num_instances = namespace(value=0) %}

        {%- if gen_mode == "A" %}
            {%- set expected_num_instances.value = num_instances %}

            var objects = new Object[{{num_instances}}];
            for (int i = 0; i < {{num_instances}}; ++i) {
                objects[i] = new C0();
            }
            {%- set instance_counter.value = num_instances  %}
        {%- endif %}

        {%- if gen_mode == "B" %}
            {%- set expected_num_instances.value=num_classes %}

            var objects = new Object[] {
                {%- for classIdx in range(0, num_classes) %}
                    new C{{classIdx}}(),
                    {%- set instance_counter.value = instance_counter.value + 1  %}
                {%- endfor %}
            };
        {%- endif %}

        {%- if gen_mode == "C" %}
            {%- set expected_num_instances.value = num_instances %}

            var objects = new Object[{{num_instances}}];

            int instances = 0;
            {%- for classIdx in range(0, num_classes) %}
                for (int i = 0; i < {{instance_counts[classIdx]}}; ++i) {
                    objects[instances++] = new C{{classIdx}}();
                }
                {%- set instance_counter.value = instance_counter.value + instance_counts[classIdx] %}
            {%- endfor %}
        {%- endif %}

        {{ ("Rendered code will generate the following number of instances: " + (instance_counter.value | string)) | debug }}
        {%- if expected_num_instances.value != instance_counter.value %}
            {{raise("Did not generate intended number of instances: " + (instance_counter.value | string) + " instead of " + (expected_num_instances.value | string) ) }}
        {%- endif %}

        System.out.println("Instances created.");
    }
}