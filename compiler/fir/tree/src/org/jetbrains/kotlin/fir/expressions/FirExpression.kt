/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.symbols.ConeCallableSymbol
import org.jetbrains.kotlin.fir.transformSingle
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirImplicitTypeRefImpl
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor

abstract class FirExpression(
    session: FirSession,
    psi: PsiElement?
) : FirStatement(session, psi) {
    open var typeRef: FirTypeRef = FirImplicitTypeRefImpl(session, null)

    open fun replaceTypeRef(newTypeRef: FirTypeRef) {
        typeRef = newTypeRef
    }

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R =
        visitor.visitExpression(this, data)

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        super.acceptChildren(visitor, data)
        typeRef.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        typeRef = typeRef.transformSingle(transformer, data)

        return super.transformChildren(transformer, data)
    }
}

fun FirExpression.toResolvedCallableReference(): FirResolvedCallableReference? {
    return (this as? FirQualifiedAccess)?.calleeReference as? FirResolvedCallableReference
}


fun FirExpression.toResolvedCallableSymbol(): ConeCallableSymbol? {
    return toResolvedCallableReference()?.coneSymbol as ConeCallableSymbol?
}