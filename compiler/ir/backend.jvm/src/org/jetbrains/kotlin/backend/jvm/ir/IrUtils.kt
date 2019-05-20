/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.ir

import org.jetbrains.kotlin.backend.jvm.codegen.isJvmInterface
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.util.isNullable

/**
 * Perform as much type erasure as is significant for JVM signature generation.
 * Class types are kept as is, while type parameters are replaced with their
 * erased upper bounds, keeping the nullability information.
 *
 * For example, a type parameter `T?` where `T : Any`, `T : Comparable<T>` is
 * erased to `Any?`.
 *
 * Type arguments to the erased upper bound are replaced by `*`, since
 * recursive erasure could loop. For example, a type parameter
 * `T : Comparable<T>` is replaced by `Comparable<*>`.
 */
fun IrType.eraseTypeParameters() = when (this) {
    is IrErrorType -> this
    is IrSimpleType ->
        when (val owner = classifier.owner) {
            is IrClass -> this
            is IrTypeParameter -> {
                val upperBound = owner.erasedUpperBound
                IrSimpleTypeImpl(
                    upperBound.symbol,
                    isNullable(),
                    List(upperBound.typeParameters.size) { IrStarProjectionImpl },    // Should not affect JVM signature, but may result in an invalid type object
                    owner.annotations
                )
            }
            else -> error("Unknown IrSimpleType classifier kind: $owner")
        }
    else -> error("Unknown IrType kind: $this")
}

/**
 * Computes the erased class for this type parameter according to the java erasure rules.
 */
val IrTypeParameter.erasedUpperBound: IrClass
    get() {
        // Pick the (necessarily unique) non-interface upper bound if it exists
        for (type in superTypes) {
            val irClass = type.classOrNull?.owner ?: continue
            if (!irClass.isJvmInterface) return irClass
        }

        // Otherwise, choose either the first IrClass supertype or recurse.
        // In the first case, all supertypes are interface types and the choice was arbitrary.
        // In the second case, there is only a single supertype.
        return superTypes.first().erasedUpperBound
    }

val IrType.erasedUpperBound: IrClass
    get() = when (val classifier = classifierOrNull) {
        is IrClassSymbol -> classifier.owner
        is IrTypeParameterSymbol -> classifier.owner.erasedUpperBound
        else -> throw IllegalStateException()
    }
