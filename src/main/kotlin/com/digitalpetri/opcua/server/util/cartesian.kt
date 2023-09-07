package com.digitalpetri.opcua.server.util

data class Trio<T, U, V>(val first: T, val second: U, val third: V)

fun <T, U, V> cartesianProduct(
    c1: Collection<T>,
    c2: Collection<U>,
    c3: Collection<V>
): List<Trio<T, U, V>> {

    return c1.flatMap { left -> c2.flatMap { middle -> c3.map { right -> Trio(left, middle, right) } } }
}
