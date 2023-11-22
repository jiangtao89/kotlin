// ISSUE: KT-62554
// FIR_DUMP
// SCOPE_DUMP: D:foo
// FILE: A.java

public class A<T> {
    public String foo(T x) {
        return "A";
    }
}

// FILE: C.java

public class C extends A<Integer> {}

// FILE: main.kt

interface B {
    fun foo(x: Int) = "B"
}

<!MANY_IMPL_MEMBER_NOT_IMPLEMENTED!>class D<!> : C(), B

fun main() {
    D().foo(42)
}
