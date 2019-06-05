/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.fir.symbols.FirSymbolOwner
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef

// Good name needed (something with receiver, type parameters, return type, and name)
interface FirCallableDeclaration<D> : FirTypedDeclaration, FirSymbolOwner<D> where D : FirNamedDeclaration, D : FirCallableDeclaration<D> {

    override val symbol: FirCallableSymbol<D>

    val receiverTypeRef: FirTypeRef?
}
